"""
Real asynchronous Ping service using system ping execution and Asyncio.
Streams each packet reply in real-time with RTT and packet loss calculations.
"""

import asyncio
import platform
import re
import socket
import time
from typing import AsyncGenerator, Optional, Union

from .models import PingPacket, PingSummary


class AsyncPingService:
    """Asynchronous real ping runner supporting Linux, macOS, and Windows."""

    def __init__(self):
        self.os_type = platform.system()
        # Patterns for parsing ping replies across OSes
        self.reply_regex = re.compile(
            r'(\d+)\s+bytes\s+from\s+([^:]+):.*?(?:icmp_seq|seq)=(\d+).*?ttl=(\d+).*?time=([\d\.]+)\s*ms',
            re.IGNORECASE
        )
        self.windows_reply_regex = re.compile(
            r'(?:Ответ от|Reply from)\s+([^:]+):\s+(?:число байт|bytes)=(\d+)\s+(?:время|time)[=<]([\d\.]+)\s*ms\s+TTL=(\d+)',
            re.IGNORECASE
        )
        self.stats_regex = re.compile(
            r'(\d+)\s+(?:packets transmitted|пакетов: отправлено).*?(\d+)\s+(?:received|получено).*?([\d\.]+)%\s+(?:packet loss|потерь)',
            re.IGNORECASE
        )
        self.rtt_regex = re.compile(
            r'(?:rtt|round-trip)\s+min/avg/max/(?:mdev|stddev)\s*=\s*([\d\.]+)/([\d\.]+)/([\d\.]+)',
            re.IGNORECASE
        )

    def clean_host(self, host: str) -> str:
        return host.strip().replace("https://", "").replace("http://", "").split("/")[0].split(":")[0].strip()

    async def resolve_target(self, host: str) -> str:
        """Resolves domain to IP asynchronously."""
        loop = asyncio.get_running_loop()
        clean = self.clean_host(host)
        try:
            info = await loop.getaddrinfo(clean, None, family=socket.AF_INET)
            return info[0][4][0]
        except Exception:
            return clean

    async def ping_stream(
        self,
        raw_target: str,
        count: int = 4,
        timeout_seconds: int = 2
    ) -> AsyncGenerator[Union[PingPacket, PingSummary, str], None]:
        """
        Executes real system ping and yields PingPacket items in real time.
        Count = 0 means continuous ping until cancelled.
        """
        target = self.clean_host(raw_target)
        if not target:
            yield "Ошибка: Не указан целевой хост"
            return

        resolved_ip = await self.resolve_target(target)
        yield f"PING {target} ({resolved_ip}): таймаут {timeout_seconds}с, пакетов: {'непрерывно' if count == 0 else count}"

        cmd = []
        if self.os_type == "Windows":
            cmd = ["ping"]
            if count > 0:
                cmd.extend(["-n", str(count)])
            else:
                cmd.append("-t")
            cmd.extend(["-w", str(timeout_seconds * 1000), resolved_ip])
        elif self.os_type == "Darwin":
            cmd = ["ping"]
            if count > 0:
                cmd.extend(["-c", str(count)])
            cmd.extend(["-W", str(timeout_seconds * 1000), resolved_ip])
        else:
            # Linux
            cmd = ["ping"]
            if count > 0:
                cmd.extend(["-c", str(count)])
            cmd.extend(["-W", str(timeout_seconds), resolved_ip])

        proc: Optional[asyncio.subprocess.Process] = None
        transmitted = 0
        received = 0
        loss_pct = 0.0
        rtts = []

        try:
            proc = await asyncio.create_subprocess_exec(
                *cmd,
                stdout=asyncio.subprocess.PIPE,
                stderr=asyncio.subprocess.STDOUT
            )

            seq_counter = 1
            while True:
                line_bytes = await proc.stdout.readline()
                if not line_bytes:
                    break

                # Decode according to OS
                encoding = "cp866" if self.os_type == "Windows" else "utf-8"
                line = line_bytes.decode(encoding, errors="replace").strip()
                if not line:
                    continue

                # Match POSIX reply
                m = self.reply_regex.search(line)
                if m:
                    bytes_cnt = int(m.group(1))
                    ip = m.group(2)
                    seq = int(m.group(3))
                    ttl = int(m.group(4))
                    rtt_ms = float(m.group(5))
                    received += 1
                    rtts.append(rtt_ms)

                    yield PingPacket(
                        sequence=seq,
                        bytes_count=bytes_cnt,
                        ip=ip,
                        ttl=ttl,
                        rtt_ms=rtt_ms,
                        raw_line=line
                    )
                    continue

                # Match Windows reply
                wm = self.windows_reply_regex.search(line)
                if wm:
                    ip = wm.group(1)
                    bytes_cnt = int(wm.group(2))
                    rtt_ms = float(wm.group(3))
                    ttl = int(wm.group(4))
                    received += 1
                    rtts.append(rtt_ms)

                    yield PingPacket(
                        sequence=seq_counter,
                        bytes_count=bytes_cnt,
                        ip=ip,
                        ttl=ttl,
                        rtt_ms=rtt_ms,
                        raw_line=line
                    )
                    seq_counter += 1
                    continue

                # Check timeouts
                if "timeout" in line.lower() or "превышен" in line.lower() or "100%" in line:
                    yield PingPacket(
                        sequence=seq_counter,
                        bytes_count=0,
                        ip=resolved_ip,
                        ttl=0,
                        rtt_ms=0.0,
                        is_timeout=True,
                        raw_line=line
                    )
                    seq_counter += 1
                    continue

                # Check stats
                sm = self.stats_regex.search(line)
                if sm:
                    transmitted = int(sm.group(1))
                    received = int(sm.group(2))
                    loss_pct = float(sm.group(3))
                    continue

                rm = self.rtt_regex.search(line)
                if rm:
                    min_rtt = float(rm.group(1))
                    avg_rtt = float(rm.group(2))
                    max_rtt = float(rm.group(3))
                    summary = PingSummary(
                        target=target,
                        packets_transmitted=transmitted or len(rtts),
                        packets_received=received,
                        packet_loss_percent=loss_pct,
                        min_rtt_ms=min_rtt,
                        avg_rtt_ms=avg_rtt,
                        max_rtt_ms=max_rtt
                    )
                    yield summary
                    continue

                yield line

            await proc.wait()

            # If no OS summary regex matched, build summary from captured packets
            if rtts:
                min_rtt = min(rtts)
                max_rtt = max(rtts)
                avg_rtt = round(sum(rtts) / len(rtts), 2)
                trans = transmitted if transmitted > 0 else (count if count > 0 else len(rtts))
                loss = round(((trans - len(rtts)) / trans) * 100, 1) if trans > 0 else 0.0

                yield PingSummary(
                    target=target,
                    packets_transmitted=trans,
                    packets_received=len(rtts),
                    packet_loss_percent=loss,
                    min_rtt_ms=min_rtt,
                    avg_rtt_ms=avg_rtt,
                    max_rtt_ms=max_rtt
                )

        except asyncio.CancelledError:
            if proc:
                try:
                    proc.terminate()
                except Exception:
                    pass
            yield "--- Пинг остановлен пользователем ---"
            raise
        except Exception as e:
            yield f"Ошибка выполнения пинга: {e}"
        finally:
            if proc and proc.returncode is None:
                try:
                    proc.terminate()
                except Exception:
                    pass
