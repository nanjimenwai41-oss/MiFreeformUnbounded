"""汇总真实壁纸层呈现间隔，并读取 native EGL 帧间隔；显式排除息屏长空档。"""
import argparse
import csv
import io
import json
from pathlib import Path
import re
import statistics
import subprocess


def percentile(values, fraction):
    return sorted(values)[min(len(values) - 1, round((len(values) - 1) * fraction))]


def layer_statistics(text, name_pattern=r"Wallpaper [^\n]+"):
    layers = []
    for block in re.split(r"(?=displayRefreshRate =)", text):
        name = re.search(r"layerName = (" + name_pattern + r")", block)
        histogram = re.search(r"present2present histogram is as below:\s*([^\n]+)", block)
        if not name or not histogram:
            continue
        counts = [(int(ms), int(n)) for ms, n in re.findall(r"(\d+)ms=(\d+)", histogram[1])]
        values = [ms for ms, n in counts for _ in range(n) if ms < 500]
        if not values:
            continue
        average = re.search(r"averageFPS = ([\d.]+)", block)
        count = lambda key: int(re.search(rf"^{key} = (\d+)$", block, re.M)[1]) if re.search(rf"^{key} = (\d+)$", block, re.M) else 0
        layers.append({
            "name": name[1], "interval_count": len(values), "average_fps": float(average[1]) if average else None,
            "total_frames": count("totalFrames"), "dropped_frames": count("droppedFrames"),
            "late_acquire_frames": count("lateAcquireFrames"),
            "bad_desired_present_frames": count("badDesiredPresentFrames"),
            "p50_ms_bucket": percentile(values, .5), "p95_ms_bucket": percentile(values, .95),
            "intervals_over_33ms": sum(ms > 33 for ms in values),
            "intervals_over_50ms": sum(ms > 50 for ms in values),
            "excluded_gaps_over_500ms": sum(n for ms, n in counts if ms >= 500),
        })
    return layers


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("capture", type=Path)
    parser.add_argument("--trace-processor", type=Path)
    args = parser.parse_args()
    result = {}
    result["wallpaper_layers"] = layer_statistics((args.capture / "timestats.txt").read_text(encoding="utf-8"))
    # CI74 时钟遮罩由 SystemUI RenderNode 合成，必须同时测真实 NotificationShade 窗口。
    result["clock_window_layers"] = layer_statistics(
        (args.capture / "timestats.txt").read_text(encoding="utf-8"),
        name_pattern=r"[^\n]*NotificationShade[^\n]*",
    )
    evidence = json.loads((args.capture / "evidence.json").read_text(encoding="utf-8"))
    if evidence.get("capture_schema", 1) >= 2:
        result["wallpaper_windows"] = [
            {"window": index, "layers": layer_statistics((args.capture / f"timestats-{index}.txt").read_text(encoding="utf-8"))}
            for index in range(len(evidence["windows"]))
        ]
        result["clock_window_windows"] = [
            {"window": index, "layers": layer_statistics(
                (args.capture / f"timestats-{index}.txt").read_text(encoding="utf-8"),
                name_pattern=r"[^\n]*NotificationShade[^\n]*")}
            for index in range(len(evidence["windows"]))
        ]
    log_file = args.capture / "logcat.txt"
    if log_file.is_file():
        text = log_file.read_text(encoding="utf-8")
        result["mask_pipeline_samples"] = [line for line in text.splitlines() if "CI74 clock-mask applied=" in line][-12:]
    if args.trace_processor and (args.capture / "frames.perfetto-trace").is_file():
        processes = (args.capture / "processes.txt").read_text(encoding="utf-8")
        pid = re.search(r"^\s*(\d+)\s+com\.miui\.miwallpaper\.snowmountain:snowmountainSuperWallpaper\s*$", processes, re.M)
        if not pid:
            raise RuntimeError("未发现当前雪山 renderer PID")
        query = f"""SELECT s.ts, s.dur FROM slice s
JOIN thread_track tt ON s.track_id=tt.id JOIN thread t ON tt.utid=t.utid
JOIN process p USING(upid) WHERE p.pid={int(pid[1])} AND s.name='Freeform.wallpaperSwap' ORDER BY s.ts"""
        completed = subprocess.run([str(args.trace_processor.resolve()), str(args.capture / "frames.perfetto-trace"),
                                    "-Q", query], capture_output=True, timeout=90, text=True)
        if completed.returncode:
            raise RuntimeError(completed.stderr)
        (args.capture / "wallpaper-egl.csv").write_text(completed.stdout, encoding="utf-8")
        rows = list(csv.DictReader(io.StringIO(completed.stdout)))
        times = [int(row["ts"]) for row in rows]
        # >500ms 的息屏/静止空档单列；不把它们混进连续运动的帧率。
        intervals = [(b - a) / 1e6 for a, b in zip(times, times[1:])]
        active = [value for value in intervals if 0 < value < 500]
        if active:
            result["native_egl"] = {
                "pid": int(pid[1]), "frames": len(times), "active_intervals": len(active),
                "excluded_gaps_over_500ms": sum(value >= 500 for value in intervals),
                "average_fps": 1000 / statistics.mean(active),
                "p50_ms": percentile(active, .5), "p95_ms": percentile(active, .95),
                "maximum_ms": max(active), "intervals_over_33ms": sum(value > 33.334 for value in active),
                "intervals_over_50ms": sum(value > 50 for value in active),
                "swap_p95_ms": percentile([int(row["dur"]) / 1e6 for row in rows], .95),
            }
    (args.capture / "performance.json").write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
