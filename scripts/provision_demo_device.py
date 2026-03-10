#!/usr/bin/env python3
import argparse
import json
import os
import sys
import urllib.error
import urllib.request


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Provision a CorePost demo device for the Android panic client.",
    )
    parser.add_argument(
        "--base-url",
        required=True,
        help="CorePost base URL, for example http://host-or-lan-server:PORT",
    )
    parser.add_argument(
        "--admin-token",
        default=None,
        help="Admin token. If omitted, COREPOST_ADMIN_TOKEN is used.",
    )
    parser.add_argument(
        "--display-name",
        default="Android Demo",
        help="Display name for /admin/register",
    )
    parser.add_argument(
        "--hwid",
        default="android-emulator-my_avd",
        help="HWID marker for the test device",
    )
    args = parser.parse_args()

    admin_token = args.admin_token or os.environ.get("COREPOST_ADMIN_TOKEN")
    if not admin_token:
        print("COREPOST_ADMIN_TOKEN is required", file=sys.stderr)
        return 2

    payload = {
        "displayName": args.display_name,
        "hwid": args.hwid,
        "unlockProfile": "2fa",
        "userCanUnlock": True,
        "agentAction": "observe",
    }
    request = urllib.request.Request(
        f"{args.base_url.rstrip('/')}/admin/register",
        data=json.dumps(payload).encode(),
        headers={
            "Content-Type": "application/json",
            "X-Admin-Token": admin_token,
        },
        method="POST",
    )
    try:
        with urllib.request.urlopen(request) as response:
            data = json.loads(response.read().decode())
    except urllib.error.HTTPError as error:
        body = error.read().decode()
        print(f"Provisioning failed with HTTP {error.code}: {body}", file=sys.stderr)
        return 1
    except urllib.error.URLError as error:
        print(f"Provisioning failed: {error.reason}", file=sys.stderr)
        return 1

    print(json.dumps(data, indent=2, ensure_ascii=False))
    print()
    print("Android onboarding values:")
    print(f"  Base URL: {args.base_url.rstrip('/')}")
    print(f"  Emergency ID: {data['emergencyId']}")
    print(f"  Panic secret: {data['panicSecret']}")
    print()
    print("Demo flow:")
    print("  1. emulator -avd my_avd")
    print("  2. Если используется локальный адрес хоста, выполните adb reverse для выбранного порта")
    print("  3. ./scripts/install_demo_on_emulator.sh")
    print("  4. Вставьте значения выше в приложение")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
