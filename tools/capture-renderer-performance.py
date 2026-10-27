"""从 SurfaceFlinger 实际呈现记录采集壁纸性能；不以 SystemUI gfxinfo 替代。"""
import argparse
import json
from pathlib import Path
import subprocess
import time


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--serial", required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=False)

    def adb(*command):
        result = subprocess.run(["adb", "-s", args.serial, *command], capture_output=True, timeout=45)
        if result.returncode:
            raise RuntimeError((result.stdout + result.stderr).decode(errors="replace"))
        return result.stdout.decode(errors="replace").strip()

    def save(name, *command):
        value = adb(*command)
        (args.output / name).write_text(value, encoding="utf-8")
        return value

    boot = save("boot-before.txt", "shell", "cat", "/proc/sys/kernel/random/boot_id")
    save("processes.txt", "shell", "ps", "-A", "-o", "PID,NAME")
    save("package.txt", "shell", "dumpsys", "package", "com.freeform.unbounded")
    save("power-before.txt", "shell", "dumpsys", "power")
    save("thermal-before.txt", "shell", "dumpsys", "thermalservice")
    adb("shell", "dumpsys", "SurfaceFlinger", "--timestats", "-enable", "-clear")
    windows = []
    started = time.monotonic()
    for index in range(3):
        adb("shell", "input", "keyevent", "KEYCODE_SLEEP")
        time.sleep(1)
        adb("shell", "dumpsys", "SurfaceFlinger", "--timestats", "-clear")
        stamp = time.monotonic()
        adb("shell", "input", "keyevent", "KEYCODE_WAKEUP")
        # 当前 OS4 WAKEUP 可能只显示 AOD 脉冲；已验证此处轻触能进入完整锁屏。
        adb("shell", "input", "tap", "578", "1250")
        power = adb("shell", "dumpsys", "power")
        # OS4 可将 WAKEUP 消费为 AOD 脉冲，不能将 Dozing 当作完整锁屏。
        if "mWakefulness=Awake" not in power:
            adb("shell", "input", "keyevent", "KEYCODE_POWER")
            power = adb("shell", "dumpsys", "power")
        (args.output / f"power-wake-{index}.txt").write_text(power, encoding="utf-8")
        if "mWakefulness=Awake" not in power:
            raise RuntimeError("未进入 Awake，拒绝把 AOD 数据作为完整锁屏性能")
        time.sleep(7)
        end_power = save(f"power-end-{index}.txt", "shell", "dumpsys", "power")
        if "mWakefulness=Awake" not in end_power:
            raise RuntimeError("采样结束已离开 Awake，拒绝混入 AOD 帧率")
        save(f"layers-{index}.txt", "shell", "dumpsys", "SurfaceFlinger", "--list")
        save(f"timestats-{index}.txt", "shell", "dumpsys", "SurfaceFlinger", "--timestats", "-dump")
        windows.append({"wake_offset_seconds": stamp - started, "sample_seconds": 7})
    save("timestats.txt", "shell", "dumpsys", "SurfaceFlinger", "--timestats", "-dump")
    adb("shell", "dumpsys", "SurfaceFlinger", "--timestats", "-disable")
    save("thermal-after.txt", "shell", "dumpsys", "thermalservice")
    save("window.txt", "shell", "dumpsys", "window")
    save("logcat.txt", "logcat", "-d", "-t", "12000", "-v", "threadtime")
    after = save("boot-after.txt", "shell", "cat", "/proc/sys/kernel/random/boot_id")
    if after != boot:
        raise RuntimeError("boot ID 变化")
    (args.output / "evidence.json").write_text(json.dumps({
        "serial": args.serial, "boot_id": boot, "windows": windows, "capture_schema": 3,
        "elapsed_seconds": time.monotonic() - started,
    }, indent=2), encoding="utf-8")
    print("已采集三次唤醒的 SurfaceFlinger 呈现统计和 FrameTimeline: " + str(args.output))


if __name__ == "__main__":
    main()
