#!/usr/bin/env python3
"""ADB-based synthetic message benchmark harness (no device sqlite3 required)."""

from __future__ import annotations

import argparse
import csv
import sqlite3
import subprocess
import tempfile
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Sequence

DEFAULT_PACKAGE = "org.fossify.messages.debug"
DEFAULT_DB_PATH_TEMPLATE = "/data/data/{package}/databases/conversations.db"
DEFAULT_SIZES = (1000, 5000, 10000)
DEFAULT_THREADS = 50
DEFAULT_MARKER = "BENCHMARK_MATCH"
DEFAULT_MARKER_EVERY = 10
DEFAULT_ADB = "adb"


@dataclass(frozen=True)
class BenchmarkResult:
    size: int
    thread_count: int
    messages_per_thread: int
    insert_ms: float
    filter_ms: float
    matched_messages: int
    matched_conversations: int
    matched_categories: int


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Synthetic message benchmark for Messages app.")
    parser.add_argument("--adb", default=DEFAULT_ADB, help="ADB executable path")
    parser.add_argument("--package", default=DEFAULT_PACKAGE, help="Android package name")
    parser.add_argument("--db-path", default=None, help="Full database path on device")
    parser.add_argument("--sizes", nargs="+", type=int, default=list(DEFAULT_SIZES), help="Dataset sizes")
    parser.add_argument("--threads", type=int, default=DEFAULT_THREADS, help="Number of conversation threads")
    parser.add_argument("--marker", default=DEFAULT_MARKER, help="Search marker")
    parser.add_argument("--marker-every", type=int, default=DEFAULT_MARKER_EVERY, help="Marker frequency")
    parser.add_argument("--output-csv", type=Path, default=None, help="Output CSV file")
    parser.add_argument("--dry-run", action="store_true", help="Print SQL without executing")
    parser.add_argument("--preview-lines", type=int, default=25, help="Lines of SQL to preview")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    if args.threads <= 0 or args.marker_every <= 0 or any(s <= 0 for s in args.sizes):
        raise SystemExit("Invalid arguments (positive values required).")

    db_path = args.db_path or DEFAULT_DB_PATH_TEMPLATE.format(package=args.package)
    results = []

    for size in args.sizes:
        result = benchmark_size(
            adb=args.adb,
            package=args.package,
            db_path=db_path,
            size=size,
            threads=args.threads,
            marker=args.marker,
            marker_every=args.marker_every,
            dry_run=args.dry_run,
            preview_lines=args.preview_lines,
        )
        results.append(result)

    print_results(results)
    if args.output_csv and not args.dry_run:
        write_csv(args.output_csv, results)
        print(f"\nWrote CSV to: {args.output_csv}")
    return 0


def benchmark_size(*, adb, package, db_path, size, threads, marker, marker_every, dry_run, preview_lines):
    thread_count = min(threads, size)
    messages_per_thread, remainder = divmod(size, thread_count)
    if messages_per_thread == 0:
        messages_per_thread = 1
        remainder = 0
        thread_count = size

    sql_script, counts = build_sql_script(
        size=size,
        thread_count=thread_count,
        messages_per_thread=messages_per_thread,
        remainder=remainder,
        marker=marker,
        marker_every=marker_every,
    )

    if dry_run:
        print(f"\n=== SIZE {size} (dry-run) ===")
        lines = sql_script.splitlines()
        for i, line in enumerate(lines[:preview_lines], 1):
            print(line)
        if len(lines) > preview_lines:
            print(f"... ({len(lines) - preview_lines} more lines)")
        return BenchmarkResult(size, thread_count, messages_per_thread, 0.0, 0.0,
                               counts[0], counts[1], counts[2])

    ensure_app_has_db(adb, package, db_path)
    insert_ms = run_sql_host(adb, package, db_path, sql_script)
    matched_messages, matched_conversations, matched_categories, filter_ms = run_filter_probe_host(
        adb, package, db_path, marker
    )
    return BenchmarkResult(size, thread_count, messages_per_thread, insert_ms, filter_ms,
                           matched_messages, matched_conversations, matched_categories)


def build_sql_script(*, size, thread_count, messages_per_thread, remainder, marker, marker_every):
    # Schema creation statements (only if tables don't exist)
    schema = """
CREATE TABLE IF NOT EXISTS conversations (
    thread_id INTEGER PRIMARY KEY,
    snippet TEXT,
    date INTEGER,
    read INTEGER,
    title TEXT,
    photo_uri TEXT,
    is_group_conversation INTEGER,
    phone_number TEXT,
    is_scheduled INTEGER,
    uses_custom_title INTEGER,
    archived INTEGER,
    unread_count INTEGER,
    category TEXT
);

CREATE TABLE IF NOT EXISTS messages (
    id INTEGER PRIMARY KEY,
    body TEXT,
    type INTEGER,
    status INTEGER,
    participants TEXT,
    date INTEGER,
    read INTEGER,
    thread_id INTEGER,
    is_mms INTEGER,
    attachment TEXT,
    sender_phone_number TEXT,
    sender_name TEXT,
    sender_photo_uri TEXT,
    subscription_id INTEGER,
    is_scheduled INTEGER,
    category_name TEXT,
    category_id INTEGER
);

CREATE TABLE IF NOT EXISTS categories (
    id INTEGER PRIMARY KEY,
    name TEXT,
    color INTEGER
);
"""
    statements = [
        "BEGIN IMMEDIATE;",
        "DELETE FROM messages;",
        "DELETE FROM conversations;",
        "DELETE FROM categories;",
        schema]

    matched_messages = 0
    matched_conversations = 0
    thread_base = size * 10_000
    message_id = size * 1_000_000

    for thread_index in range(thread_count):
        thread_id = thread_base + thread_index + 1
        msg_count = messages_per_thread + (1 if thread_index < remainder else 0)
        if msg_count == 0:
            continue

        thread_marker = marker if thread_index % marker_every == 0 else ""
        if thread_marker:
            matched_conversations += 1

        latest_snippet = _build_message_body(
            size=size,
            thread_index=thread_index,
            message_index=msg_count - 1,
            marker=thread_marker,
        )
        statements.append(
            f"INSERT OR REPLACE INTO conversations (thread_id, snippet, date, read, title, photo_uri, is_group_conversation, phone_number, is_scheduled, uses_custom_title, archived, unread_count, category) "
            f"VALUES ({thread_id}, {sql_text(latest_snippet)}, {1710000000 + thread_index}, 1, {sql_text(f'Benchmark Thread {size}-{thread_index+1:03d}')}, '', 0, {sql_text(_phone_number(thread_index))}, 0, 0, 0, 0, '');"
        )

        for msg_idx in range(msg_count):
            body_marker = marker if msg_idx % marker_every == 0 else ""
            if body_marker:
                matched_messages += 1
            body = _build_message_body(
                size=size,
                thread_index=thread_index,
                message_index=msg_idx,
                marker=body_marker,
            )
            statements.append(
                f"INSERT OR REPLACE INTO messages (id, body, type, status, participants, date, read, thread_id, is_mms, attachment, sender_phone_number, sender_name, sender_photo_uri, subscription_id, is_scheduled, category_name, category_id) "
                f"VALUES ({message_id}, {sql_text(body)}, 1, -1, '[]', {1710000000 + thread_index*1000 + msg_idx}, 1, {thread_id}, 0, NULL, {sql_text(_phone_number(thread_index))}, {sql_text(f'Benchmark Sender {thread_index+1:03d}')}, '', 1, 0, '', 0);"
            )
            message_id += 1

    statements.append("COMMIT;")
    return "\n".join(statements), (matched_messages, matched_conversations, 0)


def _build_message_body(*, size: int, thread_index: int, message_index: int, marker: str) -> str:
    marker_part = f" {marker}" if marker else ""
    return f"BENCHMARK|size={size}|thread={thread_index+1:03d}|msg={message_index+1:04d}{marker_part} alpha beta gamma"


def _phone_number(thread_index: int) -> str:
    return f"+1555{thread_index+1:06d}"


def sql_text(value: str) -> str:
    return "'" + value.replace("'", "''") + "'"


def ensure_app_has_db(adb: str, package: str, db_path: str) -> None:
    check = subprocess.run(
        [adb, "shell", "run-as", package, "test", "-f", db_path, "&&", "echo", "1"],
        capture_output=True,
        text=True,
    )
    if check.stdout.strip() == "1":
        return
    print(f"Database missing – launching app to create it...")
    subprocess.run([adb, "shell", "monkey", "-p", package, "1"], capture_output=True)
    time.sleep(2)


def run_sql_host(adb: str, package: str, db_path: str, sql_script: str) -> float:
    started = time.perf_counter()

    # Ensure database directory exists
    db_dir = str(Path(db_path).parent)
    subprocess.run([adb, "shell", "run-as", package, "mkdir", "-p", db_dir],
                   capture_output=True, check=False)

    with tempfile.NamedTemporaryFile(suffix=".db") as tmp:
        # Pull existing database or create empty one
        pull = subprocess.run([adb, "exec-out", "run-as", package, "cat", db_path],
                              stdout=tmp, stderr=subprocess.PIPE, check=False)
        if pull.returncode != 0:
            # Database doesn't exist – create empty file on host
            open(tmp.name, "wb").close()

        # Run SQL script on host
        conn = sqlite3.connect(tmp.name)
        try:
            conn.executescript(sql_script)
            conn.commit()
        except sqlite3.Error as e:
            raise RuntimeError(f"SQL error: {e}") from e
        finally:
            conn.close()

        # Push modified database to device temporary location
        device_temp = "/data/local/tmp/benchmark_temp.db"
        subprocess.run([adb, "push", tmp.name, device_temp], check=True, capture_output=True)

        # Copy from temp location to app's private directory using cat
        # We need to ensure the target directory exists before copying
        # Create the directory again (idempotent)
        subprocess.run([adb, "shell", "run-as", package, "mkdir", "-p", db_dir],
                       capture_output=True, check=True)

        # Use dd or cat to write the file
        copy_cmd = [adb, "shell", "run-as", package, "sh", "-c",
                    f"cat {device_temp} > {db_path} && rm {device_temp}"]
        proc = subprocess.run(copy_cmd, capture_output=True, text=True)
        if proc.returncode != 0:
            # Try alternative: use dd if available
            dd_cmd = [adb, "shell", "run-as", package, "dd", f"of={db_path}"]
            with open(tmp.name, "rb") as f:
                proc2 = subprocess.run(dd_cmd, stdin=f, capture_output=True, check=False)
            if proc2.returncode != 0:
                raise RuntimeError(
                    f"Failed to write database. cat stderr: {proc.stderr}\ndd stderr: {proc2.stderr}"
                )
            # Remove temp file
            subprocess.run([adb, "shell", "rm", device_temp], capture_output=True, check=False)

    return (time.perf_counter() - started) * 1000.0


def run_filter_probe_host(adb: str, package: str, db_path: str, marker: str) -> tuple[int, int, int, float]:
    started = time.perf_counter()

    with tempfile.NamedTemporaryFile(suffix=".db") as tmp:
        pull = subprocess.run(
            [adb, "exec-out", "run-as", package, "cat", db_path],
            stdout=tmp,
            stderr=subprocess.PIPE,
            check=False,
        )
        if pull.returncode != 0:
            return (0, 0, 0, (time.perf_counter() - started) * 1000.0)

        conn = sqlite3.connect(tmp.name)
        cur = conn.cursor()
        try:
            cur.execute("SELECT COUNT(*) FROM messages WHERE body LIKE ?", (f"%{marker}%",))
            msg_cnt = cur.fetchone()[0]
            cur.execute("SELECT COUNT(*) FROM conversations WHERE title LIKE ?", (f"%{marker}%",))
            conv_cnt = cur.fetchone()[0]
            cur.execute("SELECT COUNT(*) FROM categories WHERE name LIKE ?", (f"%{marker}%",))
            cat_cnt = cur.fetchone()[0]
        except sqlite3.OperationalError:
            msg_cnt = conv_cnt = cat_cnt = 0
        finally:
            conn.close()

    return (msg_cnt, conv_cnt, cat_cnt, (time.perf_counter() - started) * 1000.0)


def print_results(results: Sequence[BenchmarkResult]) -> None:
    print("\nBenchmark results")
    print("size,threads,messages_per_thread,insert_ms,filter_ms,matched_messages,matched_conversations,matched_categories")
    for r in results:
        print(f"{r.size},{r.thread_count},{r.messages_per_thread},{r.insert_ms:.2f},{r.filter_ms:.2f},{r.matched_messages},{r.matched_conversations},{r.matched_categories}")


def write_csv(path: Path, results: Sequence[BenchmarkResult]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerow(["size", "threads", "messages_per_thread", "insert_ms", "filter_ms", "matched_messages", "matched_conversations", "matched_categories"])
        for r in results:
            writer.writerow([r.size, r.thread_count, r.messages_per_thread, f"{r.insert_ms:.2f}", f"{r.filter_ms:.2f}", r.matched_messages, r.matched_conversations, r.matched_categories])


if __name__ == "__main__":
    raise SystemExit(main())
