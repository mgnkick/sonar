package com.wifipulse.analyzer.data.repository

import com.wifipulse.analyzer.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.regex.Pattern

class NetworkDiagnosticRepository {

    private val pingReplyPattern = Pattern.compile(
        "^(\\d+)\\s+bytes from\\s+([^:]+):\\s+icmp_seq=(\\d+)\\s+ttl=(\\d+)\\s+time=([\\d\\.]+)\\s*ms",
        Pattern.CASE_INSENSITIVE
    )

    private val pingTtlExceededPattern = Pattern.compile(
        "From\\s+([^ :]+).*Time to live exceeded",
        Pattern.CASE_INSENSITIVE
    )

    private val pingStatsPattern = Pattern.compile(
        "(\\d+)\\s+packets transmitted,\\s+(\\d+)(?:\\s+packets)?\\s+received,\\s+([\\d\\.]+)%\\s+packet loss",
        Pattern.CASE_INSENSITIVE
    )

    private val pingRttPattern = Pattern.compile(
        "(?:rtt|round-trip)\\s+min/avg/max/(?:mdev|stddev)\\s*=\\s*([\\d\\.]+)/([\\d\\.]+)/([\\d\\.]+)/([\\d\\.]+)\\s*ms",
        Pattern.CASE_INSENSITIVE
    )

    /**
     * Sanitizes user input (strips http://, https://, paths, ports)
     */
    fun cleanHost(input: String): String {
        return input.trim()
            .replace("https://", "")
            .replace("http://", "")
            .split("/")[0]
            .split(":")[0]
            .trim()
    }

    /**
     * Resolves host to IP address
     */
    suspend fun resolveHost(host: String): Result<InetAddress> = withContext(Dispatchers.IO) {
        runCatching {
            val cleaned = cleanHost(host)
            if (cleaned.isEmpty()) throw IllegalArgumentException("Целевой узел не указан")
            InetAddress.getByName(cleaned)
        }
    }

    /**
     * Executes real Ping using Android's native /system/bin/ping binary and streams lines in real-time.
     * Supports cancellation when the collector coroutine is cancelled.
     */
    fun executePing(
        rawTarget: String,
        count: Int = 4, // 0 means continuous until stopped
        timeoutSeconds: Int = 2
    ): Flow<DiagnosticLogEntry> = callbackFlow {
        val target = cleanHost(rawTarget)
        if (target.isEmpty()) {
            trySend(DiagnosticLogEntry.Error("Ошибка: Введите IP-адрес или домен"))
            close()
            return@callbackFlow
        }

        // Check DNS / IP resolution first
        val resolvedIp = withContext(Dispatchers.IO) {
            try {
                InetAddress.getByName(target).hostAddress ?: target
            } catch (e: Exception) {
                target
            }
        }

        trySend(DiagnosticLogEntry.Output("PING $target ($resolvedIp): таймаут ${timeoutSeconds}с, пакетов: ${if (count > 0) count else "непрерывно"}", isHighlight = true))

        var process: Process? = null

        try {
            val cmd = mutableListOf("/system/bin/ping")
            if (count > 0) {
                cmd.add("-c")
                cmd.add(count.toString())
            }
            cmd.add("-W")
            cmd.add(timeoutSeconds.toString())
            cmd.add(resolvedIp)

            val pb = ProcessBuilder(cmd)
            pb.redirectErrorStream(true)
            val proc = pb.start()
            process = proc

            val reader = BufferedReader(InputStreamReader(proc.inputStream))
            var line: String?

            var transmitted = 0
            var received = 0
            var loss = 0f
            var minRtt = 0f
            var avgRtt = 0f
            var maxRtt = 0f
            var mdevRtt = 0f

            while (reader.readLine().also { line = it } != null) {
                val currentLine = line?.trim() ?: continue
                if (currentLine.isEmpty()) continue

                // Check for packet reply
                val replyMatcher = pingReplyPattern.matcher(currentLine)
                if (replyMatcher.find()) {
                    val bytes = replyMatcher.group(1)?.toIntOrNull() ?: 64
                    val ip = replyMatcher.group(2) ?: resolvedIp
                    val seq = replyMatcher.group(3)?.toIntOrNull() ?: 0
                    val ttl = replyMatcher.group(4)?.toIntOrNull() ?: 64
                    val timeMs = replyMatcher.group(5)?.toFloatOrNull() ?: 0f

                    val packet = PingPacket(
                        sequence = seq,
                        bytes = bytes,
                        ip = ip,
                        ttl = ttl,
                        rttMs = timeMs,
                        rawText = currentLine
                    )
                    trySend(DiagnosticLogEntry.PingEntry(packet))
                    continue
                }

                // Check for stats
                val statsMatcher = pingStatsPattern.matcher(currentLine)
                if (statsMatcher.find()) {
                    transmitted = statsMatcher.group(1)?.toIntOrNull() ?: 0
                    received = statsMatcher.group(2)?.toIntOrNull() ?: 0
                    loss = statsMatcher.group(3)?.toFloatOrNull() ?: 0f
                    continue
                }

                val rttMatcher = pingRttPattern.matcher(currentLine)
                if (rttMatcher.find()) {
                    minRtt = rttMatcher.group(1)?.toFloatOrNull() ?: 0f
                    avgRtt = rttMatcher.group(2)?.toFloatOrNull() ?: 0f
                    maxRtt = rttMatcher.group(3)?.toFloatOrNull() ?: 0f
                    mdevRtt = rttMatcher.group(4)?.toFloatOrNull() ?: 0f

                    val summary = PingSummary(
                        packetsTransmitted = transmitted,
                        packetsReceived = received,
                        packetLossPercent = loss,
                        minRttMs = minRtt,
                        avgRttMs = avgRtt,
                        maxRttMs = maxRtt,
                        mdevRttMs = mdevRtt
                    )
                    trySend(DiagnosticLogEntry.Summary(summary))
                    continue
                }

                // Generic output line
                trySend(DiagnosticLogEntry.Output(currentLine))
            }

            proc.waitFor()
            close()
        } catch (e: Exception) {
            // ProcessBuilder fallback to Java TCP Socket ping if OS restricts process
            if (process == null) {
                fallbackSocketPing(target, resolvedIp, count, timeoutSeconds)
            } else {
                trySend(DiagnosticLogEntry.Error("Процесс завершен: ${e.message ?: "Остановлено пользователем"}"))
            }
            close()
        }

        awaitClose {
            try {
                process?.destroy()
            } catch (ignored: Exception) {}
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Java TCP Ping fallback if native /system/bin/ping is blocked by OEM SELinux rules
     */
    private suspend fun kotlinx.coroutines.channels.ProducerScope<DiagnosticLogEntry>.fallbackSocketPing(
        host: String,
        ip: String,
        count: Int,
        timeoutSeconds: Int
    ) {
        val total = if (count > 0) count else 4
        var rec = 0
        var totalTime = 0f

        trySend(DiagnosticLogEntry.Output("Использование сокетного эхо-тестирования для $host ($ip)..."))

        for (seq in 1..total) {
            val start = System.currentTimeMillis()
            var ok = false
            var rtt = 0f
            try {
                Socket().use { sock ->
                    sock.connect(InetSocketAddress(ip, 80), timeoutSeconds * 1000)
                    rtt = (System.currentTimeMillis() - start).toFloat()
                    ok = true
                }
            } catch (e: Exception) {
                // If port 80 refused, connection was still established up to TCP layer!
                if (e.message?.contains("refused", ignoreCase = true) == true) {
                    rtt = (System.currentTimeMillis() - start).toFloat()
                    ok = true
                }
            }

            if (ok) {
                rec++
                totalTime += rtt
                trySend(DiagnosticLogEntry.PingEntry(
                    PingPacket(sequence = seq, bytes = 64, ip = ip, ttl = 64, rttMs = rtt, rawText = "Ответ от $ip: seq=$seq время=${rtt}мс")
                ))
            } else {
                trySend(DiagnosticLogEntry.Output("Запрос seq=$seq: Превышен таймаут ответа"))
            }
            kotlinx.coroutines.delay(1000)
        }

        val loss = ((total - rec).toFloat() / total) * 100f
        val avg = if (rec > 0) totalTime / rec else 0f
        trySend(DiagnosticLogEntry.Summary(PingSummary(total, rec, loss, avg, avg, avg, 0f)))
    }

    /**
     * Executes real Traceroute using Android's TTL parameter (-t <ttl>) on /system/bin/ping.
     * Increments TTL from 1 up to maxHops to map every gateway along the route.
     */
    fun executeTraceroute(
        rawTarget: String,
        maxHops: Int = 30,
        timeoutSeconds: Int = 2
    ): Flow<DiagnosticLogEntry> = flow {
        val target = cleanHost(rawTarget)
        if (target.isEmpty()) {
            emit(DiagnosticLogEntry.Error("Ошибка: Введите целевой хост для трассировки"))
            return@flow
        }

        // Resolve target IP
        val targetAddress = try {
            withContext(Dispatchers.IO) {
                InetAddress.getByName(target)
            }
        } catch (e: Exception) {
            emit(DiagnosticLogEntry.Error("Не удалось разрешить домен $target: ${e.message}"))
            return@flow
        }

        val targetIp = targetAddress.hostAddress ?: target
        emit(DiagnosticLogEntry.Output("Трассировка маршрута к $target [$targetIp]", isHighlight = true))
        emit(DiagnosticLogEntry.Output("Максимальное число прыжков: $maxHops, таймаут: ${timeoutSeconds}с"))

        var targetReached = false

        for (ttl in 1..maxHops) {
            currentCoroutineContext().ensureActive()

            val hopResult = executeSingleHopProbe(targetIp, ttl, timeoutSeconds)
            emit(DiagnosticLogEntry.HopEntry(hopResult))

            if (hopResult.isTargetReached) {
                targetReached = true
                emit(DiagnosticLogEntry.Output("✓ Маршрут до $target успешно завершен за $ttl хопов!", isHighlight = true))
                break
            }
        }

        if (!targetReached) {
            emit(DiagnosticLogEntry.Output("Трассировка остановлена (достигнут лимит $maxHops хопов)."))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Probes a single hop with TTL flag
     */
    private suspend fun executeSingleHopProbe(
        targetIp: String,
        ttl: Int,
        timeoutSeconds: Int
    ): TracerouteHop = withContext(Dispatchers.IO) {
        var process: Process? = null
        try {
            val cmd = listOf(
                "/system/bin/ping",
                "-c", "1",
                "-W", timeoutSeconds.toString(),
                "-t", ttl.toString(),
                targetIp
            )

            val startTime = System.currentTimeMillis()
            process = ProcessBuilder(cmd).redirectErrorStream(true).start()
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = StringBuilder()
            var line: String?

            var hopIp: String? = null
            var isTtlExceeded = false
            var isTargetReached = false
            var rttMs = 0f

            while (reader.readLine().also { line = it } != null) {
                val l = line ?: continue
                output.append(l).append("\n")

                // Match TTL exceeded
                val ttlMatcher = pingTtlExceededPattern.matcher(l)
                if (ttlMatcher.find()) {
                    isTtlExceeded = true
                    hopIp = ttlMatcher.group(1)?.trim()
                }

                // Match final target reply
                val replyMatcher = pingReplyPattern.matcher(l)
                if (replyMatcher.find()) {
                    isTargetReached = true
                    hopIp = replyMatcher.group(2)?.trim() ?: targetIp
                    rttMs = replyMatcher.group(5)?.toFloatOrNull() ?: 0f
                }
            }

            process.waitFor()
            val totalElapsed = (System.currentTimeMillis() - startTime).toFloat()
            if (rttMs == 0f) rttMs = totalElapsed

            // Try reverse DNS for resolved hop IP
            var hostName: String? = null
            if (hopIp != null) {
                hostName = try {
                    InetAddress.getByName(hopIp).canonicalHostName
                } catch (e: Exception) {
                    null
                }
                if (hostName == hopIp) hostName = null
            }

            if (isTargetReached) {
                TracerouteHop(
                    hopNumber = ttl,
                    ip = hopIp ?: targetIp,
                    hostName = hostName,
                    rttMs = rttMs,
                    isTimeout = false,
                    isTargetReached = true,
                    rawOutput = output.toString()
                )
            } else if (isTtlExceeded && hopIp != null) {
                TracerouteHop(
                    hopNumber = ttl,
                    ip = hopIp,
                    hostName = hostName,
                    rttMs = rttMs,
                    isTimeout = false,
                    isTargetReached = false,
                    rawOutput = output.toString()
                )
            } else {
                // Hop did not respond (ICMP filtered or timeout)
                TracerouteHop(
                    hopNumber = ttl,
                    ip = null,
                    hostName = null,
                    rttMs = null,
                    isTimeout = true,
                    isTargetReached = false,
                    rawOutput = "* * * Таймаут"
                )
            }
        } catch (e: Exception) {
            TracerouteHop(
                hopNumber = ttl,
                ip = null,
                isTimeout = true,
                rawOutput = "Ошибка: ${e.message}"
            )
        } finally {
            try {
                process?.destroy()
            } catch (ignored: Exception) {}
        }
    }
}
