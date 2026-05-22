#!/usr/bin/env python3

"""
Smoke Test Result Analyzer
Analyzes logcat and test results to generate a report
Usage: python3 tools/analyze_smoke_test.py [logcat_file] [optional: output.html]
"""

import sys
import re
from datetime import datetime
from pathlib import Path

class SmokeTestAnalyzer:
    def __init__(self, logcat_file):
        self.logcat_file = logcat_file
        self.results = {
            'timestamp': datetime.now().isoformat(),
            'errors': [],
            'warnings': [],
            'crashes': [],
            'permissions': [],
            'database': [],
            'summary': {}
        }

    def analyze(self):
        """Analyze logcat file"""
        try:
            with open(self.logcat_file, 'r') as f:
                lines = f.readlines()
        except FileNotFoundError:
            print(f"❌ File not found: {self.logcat_file}")
            return False

        print(f"📊 Analyzing {len(lines)} log lines...")

        for line in lines:
            self._check_errors(line)
            self._check_crashes(line)
            self._check_permissions(line)
            self._check_database(line)

        self._generate_summary()
        return True

    def _check_errors(self, line):
        """Look for error patterns"""
        if re.search(r'ERROR|FATAL|Exception|NullPointerException', line, re.IGNORECASE):
            self.results['errors'].append(line.strip())

    def _check_crashes(self, line):
        """Look for crash patterns"""
        if re.search(r'CRASH|ANR|AndroidRuntime.*FATAL|Process.*died', line, re.IGNORECASE):
            self.results['crashes'].append(line.strip())

    def _check_permissions(self, line):
        """Look for permission issues"""
        if re.search(r'permission denied|Permission|PermissionError', line, re.IGNORECASE):
            self.results['permissions'].append(line.strip())

    def _check_database(self, line):
        """Look for database-related messages"""
        if re.search(r'Migration|database|SQLite|schema|v10|v11', line, re.IGNORECASE):
            self.results['database'].append(line.strip())

    def _generate_summary(self):
        """Generate summary counts"""
        self.results['summary'] = {
            'total_errors': len(self.results['errors']),
            'total_crashes': len(self.results['crashes']),
            'total_permissions': len(self.results['permissions']),
            'total_database_msgs': len(self.results['database']),
        }

    def print_report(self):
        """Print analysis report"""
        print("\n" + "="*70)
        print("SMOKE TEST ANALYSIS REPORT")
        print("="*70)
        print(f"Timestamp: {self.results['timestamp']}")
        print(f"Logcat file: {self.logcat_file}")
        print()

        summary = self.results['summary']

        # Summary section
        print("📊 SUMMARY")
        print("-" * 70)
        print(f"Total Errors:          {summary['total_errors']}")
        print(f"Total Crashes:         {summary['total_crashes']}")
        print(f"Permission Issues:     {summary['total_permissions']}")
        print(f"Database Messages:     {summary['total_database_msgs']}")
        print()

        # Verdict
        print("🎯 VERDICT")
        print("-" * 70)
        if summary['total_crashes'] > 0:
            print("❌ FAIL — Crashes detected! Cannot proceed to defense.")
            print("   Action: Review crash stack traces and fix.")
        elif summary['total_errors'] > 3:
            print("⚠️  WARNING — Multiple errors found. Review below.")
            print("   Action: Check if errors are critical or pre-existing.")
        elif summary['total_permissions'] > 0:
            print("⚠️  WARNING — Permission issues detected.")
            print("   Action: Manually grant permissions via adb shell pm grant")
        else:
            print("✅ PASS — No critical issues found!")
            print("   Status: Ready for defense smoke test.")
        print()

        # Detailed sections
        if self.results['crashes']:
            print("🔴 CRASHES (Most Critical)")
            print("-" * 70)
            for i, crash in enumerate(self.results['crashes'][:5], 1):
                print(f"{i}. {crash[:80]}")
            if len(self.results['crashes']) > 5:
                print(f"... and {len(self.results['crashes']) - 5} more")
            print()

        if self.results['errors']:
            print("🟠 ERRORS")
            print("-" * 70)
            for i, error in enumerate(self.results['errors'][:5], 1):
                print(f"{i}. {error[:80]}")
            if len(self.results['errors']) > 5:
                print(f"... and {len(self.results['errors']) - 5} more")
            print()

        if self.results['permissions']:
            print("🟡 PERMISSION ISSUES")
            print("-" * 70)
            for i, perm in enumerate(self.results['permissions'][:3], 1):
                print(f"{i}. {perm[:80]}")
            print()

        if self.results['database']:
            print("🔵 DATABASE MESSAGES")
            print("-" * 70)
            for i, db_msg in enumerate(self.results['database'][:3], 1):
                print(f"{i}. {db_msg[:80]}")
            print()

        print("="*70)
        print("📋 NEXT STEPS")
        print("-" * 70)
        if summary['total_crashes'] > 0:
            print("1. ❌ Fix crashes before proceeding")
            print("2. Re-run smoke test")
        else:
            print("1. ✅ Proceed with manual smoke test (SMOKE_TEST_CHECKLIST.md)")
            print("2. ✅ Test categories, age headers, swipe")
            print("3. ✅ Record demo video")
        print()

    def save_html_report(self, output_file):
        """Save report as HTML"""
        html = self._generate_html()
        with open(output_file, 'w') as f:
            f.write(html)
        print(f"✅ HTML report saved: {output_file}")

    def _generate_html(self):
        """Generate HTML report"""
        summary = self.results['summary']

        # Determine status color
        if summary['total_crashes'] > 0:
            status = "FAIL"
            color = "red"
        elif summary['total_errors'] > 3:
            status = "WARNING"
            color = "orange"
        else:
            status = "PASS"
            color = "green"

        html = f"""
<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <title>Smoke Test Report</title>
    <style>
        body {{ font-family: Arial, sans-serif; margin: 20px; background: #f5f5f5; }}
        .header {{ background: {color}; color: white; padding: 15px; border-radius: 5px; }}
        .section {{ background: white; margin: 15px 0; padding: 15px; border-left: 4px solid {color}; }}
        .summary {{ display: grid; grid-template-columns: 1fr 1fr 1fr 1fr; gap: 10px; }}
        .stat {{ background: #f0f0f0; padding: 10px; border-radius: 3px; text-align: center; }}
        .stat-value {{ font-size: 24px; font-weight: bold; color: {color}; }}
        .stat-label {{ font-size: 12px; color: #666; }}
        table {{ width: 100%; border-collapse: collapse; }}
        td, th {{ padding: 8px; text-align: left; border-bottom: 1px solid #ddd; }}
        th {{ background: #f0f0f0; font-weight: bold; }}
        .error {{ color: red; }}
        .warning {{ color: orange; }}
        .success {{ color: green; }}
        code {{ background: #f0f0f0; padding: 2px 5px; border-radius: 3px; font-family: monospace; }}
    </style>
</head>
<body>
    <div class="header">
        <h1>🚀 Smoke Test Analysis Report</h1>
        <p><strong>Status:</strong> <span class="{color.lower()}">{status}</span></p>
        <p>Generated: {self.results['timestamp']}</p>
    </div>

    <div class="section">
        <h2>📊 Summary</h2>
        <div class="summary">
            <div class="stat">
                <div class="stat-value error">{summary['total_errors']}</div>
                <div class="stat-label">Errors</div>
            </div>
            <div class="stat">
                <div class="stat-value error">{summary['total_crashes']}</div>
                <div class="stat-label">Crashes</div>
            </div>
            <div class="stat">
                <div class="stat-value warning">{summary['total_permissions']}</div>
                <div class="stat-label">Permission Issues</div>
            </div>
            <div class="stat">
                <div class="stat-value success">{summary['total_database_msgs']}</div>
                <div class="stat-label">DB Messages</div>
            </div>
        </div>
    </div>

    <div class="section">
        <h2>🎯 Verdict</h2>
        <p class="{color.lower()}">
            {'❌ FAIL — Crashes detected!' if status == 'FAIL' else '⚠️ WARNING — Review below' if status == 'WARNING' else '✅ PASS — Ready for defense'}
        </p>
    </div>

    {'<div class="section"><h2>🔴 Crashes</h2><table><tr><th>Issue</th></tr>' + ''.join(f'<tr><td class="error"><code>{c[:100]}</code></td></tr>' for c in self.results['crashes'][:5]) + '</table></div>' if self.results['crashes'] else ''}

    {'<div class="section"><h2>🟠 Errors</h2><table><tr><th>Issue</th></tr>' + ''.join(f'<tr><td class="error"><code>{e[:100]}</code></td></tr>' for e in self.results['errors'][:5]) + '</table></div>' if self.results['errors'] else ''}

</body>
</html>
"""
        return html


def main():
    if len(sys.argv) < 2:
        print("Usage: python3 tools/analyze_smoke_test.py <logcat_file> [output.html]")
        print("Example: python3 tools/analyze_smoke_test.py logcat_20260522_1135.txt")
        sys.exit(1)

    logcat_file = sys.argv[1]
    output_html = sys.argv[2] if len(sys.argv) > 2 else None

    analyzer = SmokeTestAnalyzer(logcat_file)

    if analyzer.analyze():
        analyzer.print_report()

        if output_html:
            analyzer.save_html_report(output_html)
    else:
        sys.exit(1)


if __name__ == '__main__':
    main()

