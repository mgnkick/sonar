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
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.util.Random
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
        timeoutSeconds: Int = 2,
        packetSizeBytes: Int = 32
    ): Flow<DiagnosticLogEntry> = callbackFlow {
        val target = cleanHost(rawTarget)
        if (target.isEmpty()) {
            trySend(DiagnosticLogEntry.Error("Ошибка: Введите IP-адрес или домен"))
            close()
            return@callbackFlow
        }

        val safePacketSize = packetSizeBytes.coerceIn(8, 1400)

        // Check DNS / IP resolution first
        val resolvedIp = withContext(Dispatchers.IO) {
            try {
                InetAddress.getByName(target).hostAddress ?: target
            } catch (e: Exception) {
                target
            }
        }

        trySend(DiagnosticLogEntry.Output("PING $target ($resolvedIp): payload ${safePacketSize} байт, таймаут ${timeoutSeconds}с, пакетов: ${if (count > 0) count else "непрерывно"}", isHighlight = true))

        var process: Process? = null

        try {
            val cmd = mutableListOf("/system/bin/ping")
            if (count > 0) {
                cmd.add("-c")
                cmd.add(count.toString())
            }
            cmd.add("-W")
            cmd.add(timeoutSeconds.toString())
            cmd.add("-s")
            cmd.add(safePacketSize.toString())
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
                fallbackSocketPing(target, resolvedIp, count, timeoutSeconds, safePacketSize)
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
        timeoutSeconds: Int,
        packetSizeBytes: Int
    ) {
        val total = if (count > 0) count else 4
        var rec = 0
        var totalTime = 0f

        trySend(DiagnosticLogEntry.Output("ICMP ping недоступен. TCP-проверка для $host ($ip); размер ICMP payload ${packetSizeBytes} байт здесь не применяется."))

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

    /** ТСАП: continuously probes each TTL and emits cumulative loss/latency statistics per hop. */
    fun executeTsapTraceroute(
        rawTarget: String,
        maxHops: Int = 30,
        timeoutSeconds: Int = 2
    ): Flow<DiagnosticLogEntry> = flow {
        val target = cleanHost(rawTarget)
        if (target.isBlank()) {
            emit(DiagnosticLogEntry.Error("Ошибка: Введите целевой хост для трассировки"))
            return@flow
        }
        val targetIp = runCatching { withContext(Dispatchers.IO) { InetAddress.getByName(target).hostAddress } }
            .getOrNull()
        if (targetIp.isNullOrBlank()) {
            emit(DiagnosticLogEntry.Error("Не удалось разрешить домен $target"))
            return@flow
        }

        emit(DiagnosticLogEntry.Output("=== ТСАП · НАЧАЛО: $target [$targetIp] ===", isHighlight = true))
        emit(DiagnosticLogEntry.Output("Непрерывная трассировка: одна проба на хоп за цикл; остановите вручную. Потери на промежуточном хопе могут быть вызваны ограничением ICMP."))
        data class Stats(val sent: Int, val received: Int, val times: List<Float>, val latest: TracerouteHop)
        val stats = linkedMapOf<Int, Stats>()
        var routeLength = maxHops
        var cycle = 0
        var reportedLossHop: Int? = null
        while (true) {
            currentCoroutineContext().ensureActive()
            cycle++
            var reached = false
            for (ttl in 1..routeLength) {
                currentCoroutineContext().ensureActive()
                val probe = executeSingleHopProbe(targetIp, ttl, timeoutSeconds)
                val prev = stats[ttl]
                val sent = (prev?.sent ?: 0) + 1
                val received = (prev?.received ?: 0) + if (probe.isTimeout) 0 else 1
                val times = (prev?.times.orEmpty() + listOfNotNull(probe.rttMs)).takeLast(500)
                val avg = times.takeIf { it.isNotEmpty() }?.average()?.toFloat()
                val aggregate = probe.copy(
                    probesSent = sent,
                    probesReceived = received,
                    lossPercent = (sent - received) * 100f / sent,
                    minRttMs = times.minOrNull(),
                    averageRttMs = avg,
                    maxRttMs = times.maxOrNull()
                )
                stats[ttl] = Stats(sent, received, times, aggregate)
                emit(DiagnosticLogEntry.HopEntry(aggregate))
                if (probe.isTargetReached) {
                    routeLength = ttl
                    reached = true
                    break
                }
            }
            val likelyStart = stats.entries.firstOrNull { (hop, current) ->
                current.sent >= 5 && current.received < current.sent &&
                    stats[hop + 1]?.let { downstream -> downstream.sent >= 5 && downstream.received < downstream.sent } == true
            }?.key
            if (likelyStart != reportedLossHop) {
                reportedLossHop = likelyStart
                if (likelyStart != null) {
                    emit(DiagnosticLogEntry.Output("Первые устойчивые потери наблюдаются начиная с хопа $likelyStart. Это ориентир: промежуточные маршрутизаторы могут ограничивать ICMP, проверьте потери до конечного узла."))
                }
            }
            emit(DiagnosticLogEntry.Output("Цикл $cycle завершён${if (reached) " • цель достигнута на хопе $routeLength" else ""}"))
            kotlinx.coroutines.delay(1000)
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

    /**
     * Runs a guided, real connectivity check. A TCP fallback is used when the
     * device/OEM blocks the native ping binary; that result is explicitly labeled.
     */
    suspend fun runGuidedDiagnostics(
        gateway: String,
        dns1: String,
        dns2: String,
        onProgress: (String) -> Unit = {}
    ): List<String> = withContext(Dispatchers.IO) {
        // Keep the existing result list while streaming each line to the UI as soon as it is produced.
        val lines = object : ArrayList<String>() {
            override fun add(element: String): Boolean {
                val added = super.add(element)
                if (added) onProgress(element)
                return added
            }

            override fun addAll(elements: Collection<String>): Boolean {
                val added = super.addAll(elements)
                if (added) elements.forEach(onProgress)
                return added
            }
        }
        lines += "=== ПОШАГОВАЯ ДИАГНОСТИКА ==="
        lines += "Время: ${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}"
        lines += ""

        val cleanGateway = cleanHost(gateway)
        var gatewayReachable = false
        var gatewayLoss: Float? = null
        var gatewayAvg: Float? = null

        if (cleanGateway.isBlank() || cleanGateway == "0.0.0.0") {
            lines += "[НЕТ ДАННЫХ] Основной шлюз не определён. Убедитесь, что телефон подключён к Wi-Fi."
        } else {
            lines += "[1/4] Проверка шлюза $cleanGateway (4 ICMP-пакета)"
            val pingResult = runCatching {
                val process = ProcessBuilder("/system/bin/ping", "-c", "4", "-W", "1", cleanGateway)
                    .redirectErrorStream(true).start()
                val output = process.inputStream.bufferedReader().use { it.readText() }
                val finished = process.waitFor()
                output to finished
            }.getOrNull()

            if (pingResult != null) {
                val output = pingResult.first
                val stats = Regex("(\\d+)\\s+packets transmitted,\\s*(\\d+)\\s*(?:packets )?received,\\s*([\\d.]+)%\\s*packet loss", RegexOption.IGNORE_CASE).find(output)
                val rtt = Regex("(?:rtt|round-trip)\\s+min/avg/max/(?:mdev|stddev)\\s*=\\s*([\\d.]+)/([\\d.]+)/([\\d.]+)", RegexOption.IGNORE_CASE).find(output)
                if (stats != null) {
                    val sent = stats.groupValues[1].toIntOrNull() ?: 4
                    val received = stats.groupValues[2].toIntOrNull() ?: 0
                    gatewayLoss = stats.groupValues[3].toFloatOrNull()
                    gatewayReachable = received > 0
                    gatewayAvg = rtt?.groupValues?.getOrNull(2)?.toFloatOrNull()
                    lines += if (gatewayReachable) {
                        "✓ Шлюз отвечает: получено $received/$sent, потери ${gatewayLoss ?: 0f}%${gatewayAvg?.let { ", средняя задержка %.1f мс".format(java.util.Locale.US, it) } ?: ""}"
                    } else "✗ Шлюз не ответил на ICMP. Это может быть фильтрация ping, а не отказ роутера."
                } else {
                    lines += "⚠ Не удалось разобрать ответ ping (код ${pingResult.second})."
                    lines += output.lineSequence().filter { it.isNotBlank() }.take(3).toList()
                }
            } else {
                val tcp = tcpProbe(cleanGateway, 80, 1500) || tcpProbe(cleanGateway, 443, 1500)
                gatewayReachable = tcp
                lines += if (tcp) "✓ Шлюз доступен по TCP; ICMP недоступен, поэтому потери ICMP не измерены."
                else "⚠ Нет ответа от шлюза по ICMP/TCP. Проверьте Wi-Fi, адрес шлюза и локальную сеть."
            }
        }

        lines += ""
        lines += "[2/4] Проверка DNS"
        val dnsServers = listOf(dns1, dns2).map { cleanHost(it) }.filter { it.isNotBlank() && it != "0.0.0.0" }.distinct()
        if (dnsServers.isEmpty()) {
            lines += "⚠ DNS-серверы из конфигурации подключения не получены; проверим системный DNS."
        }
        var explicitDnsPassed = false
        dnsServers.forEach { server ->
            val result = runCatching { queryDnsA(server, "example.com", 1800) }.getOrDefault(false)
            if (result) explicitDnsPassed = true
            lines += if (result) "✓ DNS $server отвечает на запрос example.com"
                     else "✗ DNS $server не ответил на UDP/53. Возможны фильтрация или недоступность сервера."
        }
        val systemDns = runCatching {
            InetAddress.getAllByName("yandex.ru").isNotEmpty()
        }.getOrDefault(false)
        lines += if (systemDns) "✓ Системное разрешение имени yandex.ru работает"
                 else "✗ Системный DNS не смог разрешить yandex.ru"
        val dnsPassed = explicitDnsPassed || systemDns

        lines += ""
        lines += "[3/4] Проверка внешней доступности"
        val externalIpPing = runCatching {
            val process = ProcessBuilder("/system/bin/ping", "-c", "2", "-W", "1", "1.1.1.1").redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor()
            Regex("(\\d+)\\s+packets transmitted,\\s*(\\d+)\\s*(?:packets )?received", RegexOption.IGNORE_CASE)
                .find(output)?.groupValues?.getOrNull(2)?.toIntOrNull()?.let { it > 0 } == true
        }.getOrDefault(false)
        val externalTcp = tcpProbe("1.1.1.1", 443, 2500) || tcpProbe("yandex.ru", 443, 2500)
        val externalReachable = externalIpPing || externalTcp
        lines += when {
            externalIpPing -> "✓ Внешний IP 1.1.1.1 отвечает на ICMP."
            externalTcp -> "✓ Внешний HTTPS/TCP доступен; ICMP может фильтроваться."
            else -> "✗ Не удалось достичь контрольного внешнего узла ни по ICMP, ни по TCP/443."
        }

        lines += ""
        lines += "[4/4] ВЫВОД И СЛЕДУЮЩИЕ ДЕЙСТВИЯ"
        when {
            cleanGateway.isBlank() || cleanGateway == "0.0.0.0" ->
                lines += "1) Подключите телефон к Wi-Fi и повторите проверку. Без шлюза нельзя отделить локальную проблему от внешней."
            !gatewayReachable && !externalReachable ->
                lines += "1) Начните с локального сегмента: проверьте Wi-Fi, IP-адрес, шлюз, кабель WAN и питание роутера. Ping может быть запрещён политикой сети."
            !gatewayReachable && externalReachable ->
                lines += "1) Интернет доступен, но шлюз не отвечает на проверяемые пробы. Вероятна фильтрация ICMP; проверьте веб-интерфейс роутера, а не объявляйте неисправность."
            gatewayReachable && !externalReachable ->
                lines += "1) Локальная связь с роутером есть, внешний узел недоступен. Проверьте WAN/PPPoE, статус линии провайдера и трассировку."
            gatewayReachable && !dnsPassed ->
                lines += "1) Шлюз доступен и внешний узел может отвечать, но DNS-проверка не прошла. Проверьте адреса DNS и попробуйте альтернативный разрешитель."
            gatewayLoss != null && gatewayLoss!! > 0f ->
                lines += "1) Есть потери до шлюза (${gatewayLoss}%). Проверьте уровень Wi-Fi, помехи, питание роутера и Ethernet-соединение."
            gatewayAvg != null && gatewayAvg!! > 20f ->
                lines += "1) Средняя задержка до шлюза повышена (${"%.1f".format(java.util.Locale.US, gatewayAvg)} мс). Сравните замер рядом с роутером и в проблемной комнате."
            else ->
                lines += "✓ Шлюз, DNS и внешняя доступность проверены. Если жалоба остаётся, сравните скорость и задержку рядом с роутером и в проблемной зоне."
        }
        lines += ""
        lines += "Важно: отсутствие ответа на ICMP само по себе не доказывает неисправность — ping может быть заблокирован."
        lines
    }

    private fun tcpProbe(host: String, port: Int, timeoutMs: Int): Boolean = runCatching {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, port), timeoutMs)
            true
        }
    }.getOrDefault(false)

    /** Minimal UDP DNS A query to test a specific configured resolver without external libraries. */
    private fun queryDnsA(server: String, hostname: String, timeoutMs: Int): Boolean {
        val address = InetAddress.getByName(server)
        if (address !is Inet4Address && address.address.size != 16) return false
        val id = Random().nextInt(65536)
        val packet = java.io.ByteArrayOutputStream()
        java.io.DataOutputStream(packet).use { out ->
            out.writeShort(id); out.writeShort(0x0100); out.writeShort(1)
            out.writeShort(0); out.writeShort(0); out.writeShort(0)
            hostname.split(".").forEach { label ->
                val bytes = label.toByteArray(Charsets.US_ASCII)
                out.writeByte(bytes.size); out.write(bytes)
            }
            out.writeByte(0); out.writeShort(1); out.writeShort(1)
        }
        return DatagramSocket().use { socket ->
            socket.soTimeout = timeoutMs
            val bytes = packet.toByteArray()
            socket.send(DatagramPacket(bytes, bytes.size, address, 53))
            val buffer = ByteArray(1500)
            val response = DatagramPacket(buffer, buffer.size)
            socket.receive(response)
            if (response.length < 12) return false
            val responseId = ((buffer[0].toInt() and 0xff) shl 8) or (buffer[1].toInt() and 0xff)
            val flags = ((buffer[2].toInt() and 0xff) shl 8) or (buffer[3].toInt() and 0xff)
            responseId == id && (flags and 0x8000) != 0 && (flags and 0x000f) == 0
        }
    }

}
