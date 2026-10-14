"""
Real asynchronous Traceroute service using incremental TTL probing and system utilities.
Streams each hop in real-time as route discovery progresses.
"""

import asyncio
import platform
import re
import socket
import time
from typing import AsyncGenerator, Optional

from .models import TracerouteHop


class AsyncTracerouteService:
    """Asynchronous real traceroute runner supporting Linux, macOS, and Windows."""

    def __init__(self):
        self.os_type = platform.system()
        # POSIX ping TTL exceeded: "From 192.168.1.1: icmp_seq=1 Time to live exceeded"
        self.ttl_exceeded_regex = re.compile(
            r'(?:From|От)\s+([0-9a-f\.\:]+).*?(?:Time to live exceeded|срок жизни)',
            re.IGNORECASE
        )
        # Final destination reply
        self.target_reached_regex = re.compile(
            r'(\d+)\s+bytes\s+from\s+([0-9a-f\.\:]+):.*?time=([\d\.]+)\s*ms',
            re.IGNORECASE
        )
        self.win_ttl_exceeded_regex = re.compile(
            r'(?:Ответ от|Reply from)\s+([0-9a-f\.\:]+).*?(?:срок жизни|TTL expired)',
            re.IGNORECASE
        )

    def clean_host(self, host: str) -> str:
        return host.strip().replace("https://", "").replace("http://", "").split("/")[0].split(":")[0].strip()

    async def resolve_target(self, host: str) -> str:
        loop = asyncio.get_running_loop()
        clean = self.clean_host(host)
        try:
            info = await loop.getaddrinfo(clean, None, family=socket.AF_INET)
            return info[0][4][0]
        except Exception:
            return clean

    async def reverse_dns(self, ip: str) -> Optional[str]:
        """Resolves IP to domain name asynchronously."""
        loop = asyncio.get_running_loop()
        try:
            name, _ = await loop.getnameinfo((ip, 0), socket.NI_NAMEREQD)
            return name if name != ip else None
        except Exception:
            return None

    async def traceroute_stream(
        self,
        raw_target: str,
        max_hops: int = 30,
        timeout_seconds: int = 2
    ) -> AsyncGenerator[TracerouteHop, None]:
        """
        Executes real traceroute via incremental TTL ping probes.
        Yields TracerouteHop for every hop from 1 up to max_hops.
        """
        target = self.clean_host(raw_target)
        if not target:
            return

        resolved_ip = await self.resolve_target(target)

        # Probe hops incrementally
        for ttl in range(1, max_hops + 1):
            hop = await self._probe_single_hop(resolved_ip, ttl, timeout_seconds)
            yield hop

            if hop.is_target_reached:
                break

    async def _probe_single_hop(self, target_ip: str, ttl: int, timeout_seconds: int) -> TracerouteHop:
        """Sends a single probe packet with specified TTL."""
        cmd = []
        if self.os_type == "Windows":
            # Windows ping: -n 1 (1 packet), -i <ttl>, -w <timeout_ms>
            cmd = ["ping", "-n", "1", "-i", str(ttl), "-w", str(timeout_seconds * 1000), target_ip]
        elif self.os_type == "Darwin":
            cmd = ["ping", "-c", "1", "-m", str(ttl), "-W", str(timeout_seconds * 1000), target_ip]
        else:
            # Linux: -c 1 (1 packet), -t <ttl>, -W <timeout_s>
            cmd = ["ping", "-c", "1", "-t", str(ttl), "-W", str(timeout_seconds), target_ip]

        proc: Optional[asyncio.subprocess.Process] = None
        start_time = time.time()
        hop_ip: Optional[str] = None
        is_reached = False
        rtt_ms: Optional[float] = None
        output_lines = []

        try:
            proc = await asyncio.create_subprocess_exec(
                *cmd,
                stdout=asyncio.subprocess.PIPE,
                stderr=asyncio.subprocess.STDOUT
            )

            stdout_bytes, _ = await proc.communicate()
            elapsed_ms = round((time.time() - start_time) * 1000, 1)

            encoding = "cp866" if self.os_type == "Windows" else "utf-8"
            output = stdout_bytes.decode(encoding, errors="replace")

            for line in output.splitlines():
                output_lines.append(line.strip())

                # Check TTL Exceeded
                m_ttl = self.ttl_exceeded_regex.search(line) or self.win_ttl_exceeded_regex.search(line)
                if m_ttl:
                    hop_ip = m_ttl.group(1).strip()
                    rtt_ms = elapsed_ms
                    continue

                # Check Destination Reached
                m_reply = self.target_reached_regex.search(line)
                if m_reply:
                    hop_ip = m_reply.group(2).strip()
                    rtt_ms = float(m_reply.group(3))
                    is_reached = True
                    continue

                if self.os_type == "Windows" and ("Ответ от" in line or "Reply from" in line) and target_ip in line:
                    hop_ip = target_ip
                    is_reached = True
                    rtt_ms = elapsed_ms

            # Resolve reverse DNS if IP was found
            host_name = None
            if hop_ip:
                host_name = await self.reverse_dns(hop_ip)

            if is_reached:
                return TracerouteHop(
                    hop_number=ttl,
                    ip=hop_ip or target_ip,
                    host_name=host_name,
                    rtt_ms=rtt_ms or elapsed_ms,
                    is_timeout=False,
                    is_target_reached=True,
                    raw_output="\n".join(output_lines)
                )
            elif hop_ip:
                return TracerouteHop(
                    hop_number=ttl,
                    ip=hop_ip,
                    host_name=host_name,
                    rtt_ms=rtt_ms or elapsed_ms,
                    is_timeout=False,
                    is_target_reached=False,
                    raw_output="\n".join(output_lines)
                )
            else:
                return TracerouteHop(
                    hop_number=ttl,
                    ip=None,
                    host_name=None,
                    rtt_ms=None,
                    is_timeout=True,
                    is_target_reached=False,
                    raw_output="* * * Таймаут"
                )

        except asyncio.CancelledError:
            if proc:
                try:
                    proc.terminate()
                except Exception:
                    pass
            raise
        except Exception:
            return TracerouteHop(
                hop_number=ttl,
                ip=None,
                host_name=None,
                rtt_ms=None,
                is_timeout=True,
                is_target_reached=False
            )
