#!/usr/bin/env python3
"""固定数据集 v1 的 API 查询/看板负载工具；仅使用 Python 标准库。"""

from __future__ import annotations

import argparse
import csv
import hashlib
import http.client
import ipaddress
import json
import math
import os
import platform
import re
import shutil
import socket
import subprocess
import sys
import threading
import time
import uuid
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Any
from urllib.parse import urlsplit

ROOT = Path(__file__).resolve().parents[2]
DATASET_VERSION = "fixed-dataset-v1"
EXPECTED_COUNTS = {
    "communities": 2,
    "elder_profiles": 100,
    "vital_records": 5000,
    "work_orders": 500,
    "service_reservations": 500,
}
TARGETS = {
    "core_query_p95_ms": 2000,
    "core_write_p95_ms": 3000,
    "dashboard_p95_ms": 3000,
    "unexpected_failure_rate_max": 0.01,
    "admin_state_visible_after_submit_ms": 5000,
}
CSV_FIELDS = [
    "phase", "timestamp_utc", "worker_id", "sequence", "scenario", "category",
    "method", "path", "status_code", "application_code", "duration_ms", "success",
    "error_type", "error_message", "request_body_json", "response_bytes",
    "response_sha256", "response_item_count",
]
SENSITIVE_KEY_PARTS = (
    "password", "token", "authorization", "cookie", "phone", "mobile",
    "telephone", "idnumber", "identitycard", "idcard", "secret",
)


@dataclass(frozen=True)
class Scenario:
    name: str
    category: str
    method: str
    path: str
    body: dict[str, Any] | None = None
    idempotency_key: str | None = None


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="milliseconds")


def redact(value: Any, secrets_to_redact: list[str]) -> Any:
    if isinstance(value, dict):
        cleaned = {}
        for key, item in value.items():
            normalized = re.sub(r"[^a-z0-9]", "", str(key).lower())
            if any(part in normalized for part in SENSITIVE_KEY_PARTS):
                cleaned[key] = "[REDACTED]"
            else:
                cleaned[key] = redact(item, secrets_to_redact)
        return cleaned
    if isinstance(value, list):
        return [redact(item, secrets_to_redact) for item in value]
    if isinstance(value, str):
        result = value
        for secret in secrets_to_redact:
            if secret:
                result = result.replace(secret, "[REDACTED]")
        return result
    return value


def parse_body(raw: bytes) -> tuple[Any, str | None, int, str, int | None]:
    text = raw.decode("utf-8", errors="replace")
    try:
        parsed = json.loads(text) if text else None
    except json.JSONDecodeError:
        parsed = None
    app_code = parsed.get("code") if isinstance(parsed, dict) else None
    item_count = None
    if isinstance(parsed, dict):
        data = parsed.get("data")
        if isinstance(data, list):
            item_count = len(data)
        elif isinstance(data, dict):
            for key in ("records", "items", "list", "dataPoints", "rows"):
                if isinstance(data.get(key), list):
                    item_count = len(data[key])
                    break
    return parsed, app_code, len(raw), hashlib.sha256(raw).hexdigest(), item_count


def make_connection(parsed_url, timeout: float):
    hostname = parsed_url.hostname
    port = parsed_url.port or (443 if parsed_url.scheme == "https" else 80)
    if parsed_url.scheme == "https":
        return http.client.HTTPSConnection(hostname, port, timeout=timeout)
    return http.client.HTTPConnection(hostname, port, timeout=timeout)


def request_once(
    connection,
    method: str,
    path: str,
    request_body: dict[str, Any] | None,
    headers: dict[str, str],
    timeout: float,
    secrets_to_redact: list[str],
    phase: str,
    worker_id: int,
    sequence: int,
    scenario: str,
    category: str,
) -> tuple[dict[str, Any], Any, list[tuple[str, str]]]:
    body_bytes = None
    if request_body is not None:
        body_bytes = json.dumps(request_body, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
    request_headers = {
        "Accept": "application/json",
        "Connection": "keep-alive",
        "User-Agent": "graduation-project-perf-harness/1",
    }
    request_headers.update(headers)
    if body_bytes is not None:
        request_headers["Content-Type"] = "application/json; charset=utf-8"
    if connection.sock is not None:
        connection.sock.settimeout(timeout)

    started = time.perf_counter_ns()
    status_code = None
    error_type = ""
    error_message = ""
    app_code = None
    response_obj = None
    response_bytes = 0
    response_sha256 = ""
    response_item_count = None
    response_cookie_headers: list[tuple[str, str]] = []
    try:
        connection.request(method, path, body=body_bytes, headers=request_headers)
        response = connection.getresponse()
        raw = response.read()
        status_code = response.status
        response_cookie_headers = [
            (name, value)
            for name, value in response.getheaders()
            if name.lower() == "set-cookie"
        ]
        response_obj, app_code, response_bytes, response_sha256, response_item_count = parse_body(raw)
    except (TimeoutError, socket.timeout) as exc:
        error_type = type(exc).__name__
        error_message = "request timed out"
    except http.client.HTTPException as exc:
        error_type = type(exc).__name__
        error_message = "HTTP transport failure"
    except OSError as exc:
        error_type = type(exc).__name__
        error_message = "network I/O failure"
    except Exception as exc:
        error_type = type(exc).__name__
        error_message = "client-side request failure"

    elapsed_ms = (time.perf_counter_ns() - started) / 1_000_000
    success = (
        status_code is not None
        and 200 <= status_code < 300
        and (app_code is None or app_code == "OK")
        and not error_type
    )
    safe_request = redact(request_body, secrets_to_redact) if request_body is not None else None
    request_encoded = (
        json.dumps(safe_request, ensure_ascii=False, separators=(",", ":"))
        if safe_request is not None
        else ""
    )
    record = {
        "phase": phase,
        "timestamp_utc": utc_now(),
        "worker_id": worker_id,
        "sequence": sequence,
        "scenario": scenario,
        "category": category,
        "method": method,
        "path": path,
        "status_code": status_code,
        "application_code": app_code,
        "duration_ms": round(elapsed_ms, 3),
        "success": success,
        "error_type": error_type,
        "error_message": error_message,
        "request_body_json": request_encoded,
        "response_bytes": response_bytes,
        "response_sha256": response_sha256,
        "response_item_count": response_item_count,
    }
    return record, response_obj, response_cookie_headers


def percentile_nearest_rank(values: list[float], percentile: float) -> float | None:
    if not values:
        return None
    ordered = sorted(values)
    return round(ordered[max(0, math.ceil(percentile * len(ordered)) - 1)], 3)


def summarize_latencies(values: list[float]) -> dict[str, Any]:
    return {
        "count": len(values),
        "p50_ms": percentile_nearest_rank(values, 0.50),
        "p95_ms": percentile_nearest_rank(values, 0.95),
        "min_ms": round(min(values), 3) if values else None,
        "max_ms": round(max(values), 3) if values else None,
    }


def total_memory_bytes() -> int | None:
    try:
        if os.name == "nt":
            import ctypes

            class MemoryStatus(ctypes.Structure):
                _fields_ = [
                    ("dwLength", ctypes.c_ulong),
                    ("dwMemoryLoad", ctypes.c_ulong),
                    ("ullTotalPhys", ctypes.c_ulonglong),
                    ("ullAvailPhys", ctypes.c_ulonglong),
                    ("ullTotalPageFile", ctypes.c_ulonglong),
                    ("ullAvailPageFile", ctypes.c_ulonglong),
                    ("ullTotalVirtual", ctypes.c_ulonglong),
                    ("ullAvailVirtual", ctypes.c_ulonglong),
                    ("ullAvailExtendedVirtual", ctypes.c_ulonglong),
                ]

            status = MemoryStatus()
            status.dwLength = ctypes.sizeof(status)
            if ctypes.windll.kernel32.GlobalMemoryStatusEx(ctypes.byref(status)):
                return int(status.ullTotalPhys)
        else:
            return int(os.sysconf("SC_PHYS_PAGES") * os.sysconf("SC_PAGE_SIZE"))
    except (AttributeError, OSError, ValueError):
        return None
    return None


def declared_versions() -> dict[str, Any]:
    api_pom = ROOT / "services" / "api" / "pom.xml"
    web_package = ROOT / "apps" / "admin-web" / "package.json"
    spring_boot = java_target = frontend_version = None
    try:
        pom_text = api_pom.read_text(encoding="utf-8")
        match = re.search(
            r"<parent>.*?<artifactId>spring-boot-starter-parent</artifactId>\s*<version>([^<]+)</version>",
            pom_text,
            re.DOTALL,
        )
        spring_boot = match.group(1).strip() if match else None
        match = re.search(r"<java\.version>([^<]+)</java\.version>", pom_text)
        java_target = match.group(1).strip() if match else None
    except OSError:
        pass
    try:
        frontend_version = json.loads(web_package.read_text(encoding="utf-8")).get("version")
    except (OSError, json.JSONDecodeError):
        pass
    return {
        "spring_boot_declared": spring_boot or "未读取",
        "java_target_declared": java_target or "未读取",
        "admin_frontend_package_version": frontend_version or "未读取",
    }


def git_revision() -> str:
    try:
        result = subprocess.run(
            ["git", "-C", str(ROOT), "rev-parse", "--short", "HEAD"],
            check=True,
            capture_output=True,
            text=True,
            timeout=3,
        )
        return result.stdout.strip()
    except (OSError, subprocess.SubprocessError):
        return "未读取"


def environment_snapshot(origin: str) -> dict[str, Any]:
    try:
        disk = shutil.disk_usage(ROOT)
        disk_info = {"total_bytes": disk.total, "free_bytes": disk.free}
    except OSError:
        disk_info = {"total_bytes": None, "free_bytes": None}
    server_keys = {
        "os": "PERF_SERVER_OS",
        "cpu": "PERF_SERVER_CPU",
        "memory": "PERF_SERVER_MEMORY",
        "disk": "PERF_SERVER_DISK",
        "java": "PERF_SERVER_JAVA",
        "spring_boot": "PERF_SERVER_SPRING_BOOT",
        "mysql": "PERF_SERVER_MYSQL",
        "deployment": "PERF_SERVER_DEPLOYMENT",
        "network": "PERF_SERVER_NETWORK",
        "frontend": "PERF_SERVER_FRONTEND",
    }
    return {
        "target_base_url": origin,
        "runner": {
            "os": platform.platform(),
            "python": platform.python_version(),
            "logical_cpu_count": os.cpu_count(),
            "processor": platform.processor() or "未提供",
            "total_memory_bytes": total_memory_bytes(),
            "repo_disk": disk_info,
            "timezone": time.tzname,
        },
        "server": {name: os.environ.get(key, "未提供") for name, key in server_keys.items()},
        "repository": {"head": git_revision(), **declared_versions()},
    }


def parse_target(base_url: str, allow_nonloopback: bool):
    try:
        parsed = urlsplit(base_url)
        hostname = parsed.hostname
        _ = parsed.port
    except ValueError:
        raise ValueError("PERF_BASE_URL 格式无效。") from None
    if parsed.scheme not in ("http", "https") or not hostname:
        raise ValueError("PERF_BASE_URL 必须使用 http 或 https 并包含主机名。")
    if parsed.username or parsed.password or parsed.query or parsed.fragment:
        raise ValueError("PERF_BASE_URL 不得包含用户名、密码、查询参数或片段。")
    if parsed.path not in ("", "/"):
        raise ValueError("PERF_BASE_URL 只填写服务根地址，不要包含路径。")
    try:
        is_loopback = ipaddress.ip_address(hostname).is_loopback
    except ValueError:
        is_loopback = hostname.lower() == "localhost"
    if not is_loopback and not allow_nonloopback:
        raise ValueError("非本机目标需要显式添加 --allow-nonloopback；不得指向生产环境。")
    return parsed, f"{parsed.scheme}://{parsed.netloc}"


def scenario_cycle(
    community_id: str,
    elder_id: int,
    include_writes: bool,
) -> list[Scenario]:
    cycle = [
        Scenario("elder_archive_page", "query", "GET", "/api/v1/elder/archives?page=1&size=20"),
        Scenario("elder_archive_page", "query", "GET", "/api/v1/elder/archives?page=1&size=20"),
        Scenario(
            "vital_trend", "query", "GET",
            f"/api/v1/health/vitals/trend?elderId={elder_id}&vitalType=BLOOD_PRESSURE&days=30",
        ),
        Scenario(
            "vital_latest", "query", "GET",
            f"/api/v1/health/vitals/latest?elderId={elder_id}&vitalType=BLOOD_PRESSURE",
        ),
        Scenario(
            "work_order_dispatch_pool", "query", "GET",
            f"/api/v1/work-orders/dispatch-pool?communityId={community_id}&status=PENDING_DISPATCH",
        ),
        Scenario(
            "work_order_dispatch_pool", "query", "GET",
            f"/api/v1/work-orders/dispatch-pool?communityId={community_id}&status=PENDING_DISPATCH",
        ),
        Scenario(
            "dashboard_summary", "dashboard", "GET",
            f"/api/v1/dashboard/community/summary?communityId={community_id}",
        ),
        Scenario(
            "dashboard_charts", "dashboard", "GET",
            f"/api/v1/dashboard/community/charts?communityId={community_id}&period=7D",
        ),
        Scenario(
            "dashboard_alerts_feed", "dashboard", "GET",
            f"/api/v1/dashboard/community/alerts-feed?communityId={community_id}&limit=10",
        ),
    ]
    if include_writes:
        cycle.append(Scenario("vital_record_create", "write", "POST", "/api/v1/health/vitals"))
    return cycle

def write_record(writer, lock: threading.Lock, record: dict[str, Any]) -> None:
    with lock:
        writer.writerow(record)


def run(args, parsed_url, origin: str) -> int:
    phone = os.environ.get("PERF_LOGIN_PHONE", "")
    password = os.environ.get("PERF_LOGIN_PASSWORD", "")
    missing = [name for name, value in (
        ("PERF_LOGIN_PHONE", phone),
        ("PERF_LOGIN_PASSWORD", password),
    ) if not value]
    if missing:
        print("缺少登录环境变量：" + ", ".join(missing), file=sys.stderr)
        return 2

    output_root = Path(args.output_dir).resolve()
    output_root.mkdir(parents=True, exist_ok=True)
    stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    run_id = f"{stamp}-{uuid.uuid4().hex[:6]}"
    run_dir = output_root / f"run-{run_id}"
    run_dir.mkdir()
    csv_path = run_dir / "raw_requests.csv"
    summary_path = run_dir / "summary.json"
    secrets_to_redact = [phone, password]
    csv_lock = threading.Lock()
    records: list[dict[str, Any]] = []
    login_error = None
    request_headers: dict[str, str] = {}
    community_id = None

    with csv_path.open("w", encoding="utf-8", newline="") as csv_file:
        writer = csv.DictWriter(csv_file, fieldnames=CSV_FIELDS, lineterminator="\n")
        writer.writeheader()
        login_connection = make_connection(parsed_url, args.timeout_seconds)
        try:
            login_body = {"phone": phone, "password": password, "clientType": "WEB"}
            login_record, login_response, cookie_headers = request_once(
                login_connection, "POST", "/api/v1/auth/login", login_body, {},
                args.timeout_seconds, secrets_to_redact, "preflight", 0, 0, "auth_login", "setup",
            )
            records.append(login_record)
            write_record(writer, csv_lock, login_record)
            data = login_response.get("data") if isinstance(login_response, dict) else None
            roles = data.get("roles", []) if isinstance(data, dict) else []
            community_id = data.get("communityId") if isinstance(data, dict) else None
            csrf_token = data.get("csrfToken") if isinstance(data, dict) else None
            cookie_value = None
            for header_name, header_value in cookie_headers:
                if header_name.lower() == "set-cookie":
                    first_part = header_value.split(";", 1)[0]
                    if first_part.startswith("SESSION_TOKEN="):
                        cookie_value = first_part
                        secrets_to_redact.append(first_part.split("=", 1)[1])
            if not login_record["success"] or not data:
                login_error = "登录预检失败；请确认账号状态、口令与 API 地址。"
            elif "COMMUNITY_ADMIN" not in roles:
                login_error = "登录账号没有 COMMUNITY_ADMIN 角色。"
            elif not community_id:
                login_error = "登录账号没有绑定社区范围。"
            elif not cookie_value:
                login_error = "登录响应没有 SESSION_TOKEN Cookie。"
            else:
                request_headers = {"Cookie": cookie_value}
                if csrf_token:
                    request_headers["X-CSRF-Token"] = csrf_token
                    secrets_to_redact.append(csrf_token)
        finally:
            login_connection.close()

        if login_error:
            summary = {
                "run_id": run_id,
                "status": "setup_failed",
                "created_at_utc": utc_now(),
                "dataset_version": DATASET_VERSION,
                "target": origin,
                "error": login_error,
                "credentials": "只从 PERF_LOGIN_PHONE / PERF_LOGIN_PASSWORD 环境变量读取，值未保存",
                "raw_requests_csv": str(csv_path),
                "formal_acceptance": "not_assessed",
            }
            summary_path.write_text(json.dumps(summary, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
            print(f"登录预检失败；脱敏原始记录：{csv_path}", file=sys.stderr)
            return 2

        if args.include_writes and args.write_elder_id is None:
            print("启用写入负载时必须显式指定 --write-elder-id。", file=sys.stderr)
            return 2
        elder_id = args.write_elder_id if args.include_writes else 1
        start_event = threading.Event()
        ready = threading.Barrier(args.users + 1)
        result_lock = threading.Lock()
        write_lock = threading.Lock()
        write_counter = [0]
        write_base = datetime.now(timezone(timedelta(hours=8), "Asia/Shanghai")).replace(tzinfo=None, microsecond=0)
        scenario_results: list[dict[str, Any]] = []

        def worker(worker_id: int) -> None:
            connection = make_connection(parsed_url, args.timeout_seconds)
            sequence = 0
            ready.wait()
            start_event.wait()
            try:
                while time.monotonic() < stop_at:
                    sequence += 1
                    cycle = scenario_cycle(str(community_id), elder_id, args.include_writes)
                    scenario = cycle[(sequence - 1) % len(cycle)]
                    if scenario.category == "write":
                        with write_lock:
                            write_counter[0] += 1
                            write_number = write_counter[0]
                        idempotency_key = f"PERF1-{run_id}-{worker_id:02d}-{sequence:07d}"
                        recorded_at = (
                            write_base + timedelta(milliseconds=write_number)
                        ).isoformat(timespec="milliseconds")
                        scenario = Scenario(
                            "vital_record_create",
                            "write",
                            "POST",
                            "/api/v1/health/vitals",
                            {
                                "elderId": elder_id,
                                "vitalType": "BLOOD_PRESSURE",
                                "valuePrimary": 122 + (write_number % 12),
                                "valueSecondary": 76 + (write_number % 10),
                                "unit": "mmHg",
                                "sourceType": "MANUAL",
                                "recordedAt": recorded_at,
                                "remark": "固定性能负载合成写入",
                                "idempotencyKey": idempotency_key,
                            },
                            idempotency_key,
                        )
                    headers = dict(request_headers)
                    if scenario.idempotency_key:
                        headers["Idempotency-Key"] = scenario.idempotency_key
                    record, _, _ = request_once(
                        connection, scenario.method, scenario.path, scenario.body, headers,
                        args.timeout_seconds, secrets_to_redact, "measurement", worker_id,
                        sequence, scenario.name, scenario.category,
                    )
                    write_record(writer, csv_lock, record)
                    with result_lock:
                        scenario_results.append(record)
            finally:
                connection.close()

        threads = [
            threading.Thread(target=worker, args=(worker_id,), name=f"perf-worker-{worker_id:02d}")
            for worker_id in range(1, args.users + 1)
        ]
        for thread in threads:
            thread.start()
        ready.wait()
        measurement_started = time.monotonic()
        stop_at = measurement_started + args.duration_seconds
        started_at_utc = utc_now()
        start_event.set()
        for thread in threads:
            thread.join()
        measurement_ended = time.monotonic()
        ended_at_utc = utc_now()

    wall_seconds = max(0.001, measurement_ended - measurement_started)
    total = len(scenario_results)
    success_count = sum(1 for item in scenario_results if item["success"])
    error_count = total - success_count
    all_latencies = [float(item["duration_ms"]) for item in scenario_results]
    by_category_values: dict[str, list[float]] = {}
    by_scenario_values: dict[str, list[float]] = {}
    category_counts: dict[str, dict[str, int]] = {}
    for item in scenario_results:
        category = str(item["category"])
        name = str(item["scenario"])
        by_category_values.setdefault(category, []).append(float(item["duration_ms"]))
        by_scenario_values.setdefault(name, []).append(float(item["duration_ms"]))
        counts = category_counts.setdefault(category, {"requests": 0, "success": 0, "errors": 0})
        counts["requests"] += 1
        counts["success"] += int(bool(item["success"]))
        counts["errors"] += int(not bool(item["success"]))

    by_category = {}
    for category in ("query", "write", "dashboard"):
        values = by_category_values.get(category, [])
        item = summarize_latencies(values)
        item.update(category_counts.get(category, {"requests": 0, "success": 0, "errors": 0}))
        if category == "write" and not args.include_writes:
            item["status"] = "未测：未启用 --include-writes"
        by_category[category] = item
    by_scenario = {
        name: summarize_latencies(values)
        for name, values in sorted(by_scenario_values.items())
    }
    error_rate = error_count / total if total else None
    summary = {
        "run_id": run_id,
        "status": "completed",
        "started_at_utc": started_at_utc,
        "ended_at_utc": ended_at_utc,
        "target": origin,
        "dataset": {
            "version": DATASET_VERSION,
            "declared_pre_run_counts": EXPECTED_COUNTS,
            "counts_verified_by_runner": False,
            "write_requests_add_rows": bool(args.include_writes),
        },
        "load_profile": {
            "model": "closed-loop; worker clients share one authenticated session",
            "users": args.users,
            "scheduled_duration_seconds": args.duration_seconds,
            "measurement_wall_seconds": round(wall_seconds, 3),
            "request_timeout_seconds": args.timeout_seconds,
            "write_scenario_enabled": bool(args.include_writes),
            "write_target_elder_id": elder_id if args.include_writes else None,
            "write_recorded_at_timezone": "Asia/Shanghai (UTC+08:00)" if args.include_writes else None,
            "matches_nominal_nfr04_users_and_duration": args.users == 10 and args.duration_seconds == 300,
        },
        "requests": {
            "total": total,
            "success": success_count,
            "errors": error_count,
            "unexpected_failure_rate": round(error_rate, 6) if error_rate is not None else None,
            "throughput_requests_per_second": round(total / wall_seconds, 3),
            "successful_throughput_per_second": round(success_count / wall_seconds, 3),
            "latency_ms_all_completed_attempts": summarize_latencies(all_latencies),
            "by_category": by_category,
            "by_scenario": by_scenario,
        },
        "targets_for_manual_review": TARGETS,
        "environment": {
            "target_base_url": origin,
            "runner": {
                "os": platform.platform(),
                "python": platform.python_version(),
                "logical_cpu_count": os.cpu_count(),
                "processor": platform.processor() or "未提供",
                "total_memory_bytes": total_memory_bytes(),
                "repo_disk": {
                    "total_bytes": shutil.disk_usage(ROOT).total,
                    "free_bytes": shutil.disk_usage(ROOT).free,
                },
                "timezone": time.tzname,
            },
            "server": {
                "os": os.environ.get("PERF_SERVER_OS", "未提供"),
                "cpu": os.environ.get("PERF_SERVER_CPU", "未提供"),
                "memory": os.environ.get("PERF_SERVER_MEMORY", "未提供"),
                "disk": os.environ.get("PERF_SERVER_DISK", "未提供"),
                "java": os.environ.get("PERF_SERVER_JAVA", "未提供"),
                "spring_boot": os.environ.get("PERF_SERVER_SPRING_BOOT", "未提供"),
                "mysql": os.environ.get("PERF_SERVER_MYSQL", "未提供"),
                "deployment": os.environ.get("PERF_SERVER_DEPLOYMENT", "未提供"),
                "network": os.environ.get("PERF_SERVER_NETWORK", "未提供"),
                "frontend": os.environ.get("PERF_SERVER_FRONTEND", "未提供"),
            },
            "repository": {"head": git_revision(), **declared_versions()},
        },
        "credentials": {
            "source": "PERF_LOGIN_PHONE and PERF_LOGIN_PASSWORD environment variables",
            "values_saved": False,
            "values_printed": False,
        },
        "evidence_limits": [
            "本报告只汇总本次 API 工具运行及声明的合成数据规模，不自动判定 AT-40 通过。",
            "管理端提交后 5 秒内页面可见更新不由本 API 工具测量。",
            "未启用写入时核心写操作为未测；启用写入时每次 POST 都会新增体征行，复跑前须重建隔离数据库。",
            "服务端资源与部署网络信息由 PERF_SERVER_* 环境变量提供；未填项标记为未提供。",
            "raw_requests.csv 只存响应字节数、SHA-256、应用 code 和集合项数，不保存业务响应正文；P50/P95 包含全部完成的请求尝试。",
        ],
        "formal_acceptance": "not_assessed",
        "raw_requests_csv": str(csv_path),
    }
    summary_path.write_text(json.dumps(summary, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"负载完成：{total} 请求，成功 {success_count}，错误 {error_count}")
    print(f"CSV：{csv_path}")
    print(f"JSON：{summary_path}")
    print("该结果是本次工具运行摘要，不是自动性能验收结论。")
    return 0


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="固定数据集 v1 API 查询/看板负载工具；默认不发送请求。",
    )
    parser.add_argument("--base-url", default=os.environ.get("PERF_BASE_URL", "http://127.0.0.1:8080"))
    parser.add_argument("--users", type=int, default=10)
    parser.add_argument("--duration-seconds", type=int, default=300)
    parser.add_argument("--timeout-seconds", type=float, default=15.0)
    parser.add_argument("--output-dir", default=str(ROOT / "ops" / "performance" / "results"))
    parser.add_argument("--include-writes", action="store_true")
    parser.add_argument("--write-elder-id", type=int)
    parser.add_argument("--allow-nonloopback", action="store_true")
    parser.add_argument("--execute", action="store_true", help="确认开始发送负载请求")
    return parser


def main() -> int:
    args = build_parser().parse_args()
    if not 1 <= args.users <= 100:
        print("--users 必须介于 1 和 100。", file=sys.stderr)
        return 2
    if not 1 <= args.duration_seconds <= 1800:
        print("--duration-seconds 必须介于 1 和 1800。", file=sys.stderr)
        return 2
    if not 0 < args.timeout_seconds <= 120:
        print("--timeout-seconds 必须大于 0 且不超过 120。", file=sys.stderr)
        return 2
    if args.include_writes and args.write_elder_id is None:
        print("启用写入负载时必须显式指定 --write-elder-id。", file=sys.stderr)
        return 2
    if not args.include_writes and args.write_elder_id is not None:
        print("--write-elder-id 只有配合 --include-writes 才能使用。", file=sys.stderr)
        return 2
    if args.write_elder_id is not None and not 1 <= args.write_elder_id <= 100:
        print("--write-elder-id 必须位于固定数据集 v1 的 1 到 100 范围。", file=sys.stderr)
        return 2
    try:
        parsed_url, origin = parse_target(args.base_url, args.allow_nonloopback)
    except ValueError as exc:
        print(str(exc), file=sys.stderr)
        return 2
    if not args.execute:
        print("仅完成参数预检，没有发送 HTTP 请求。需要运行时加 --execute。")
        print(f"目标：{origin}；用户数：{args.users}；持续秒数：{args.duration_seconds}。")
        return 0
    return run(args, parsed_url, origin)


if __name__ == "__main__":
    raise SystemExit(main())
