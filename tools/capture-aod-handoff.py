"""采集亮屏锁屏→AOD 短转场；录屏仅用于时序观察，不作为性能基准。"""

import argparse
import json
from pathlib import Path
import subprocess
import time


parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--serial', required=True, help='ADB serial of the test device')
parser.add_argument('--label', required=True)
args = parser.parse_args()
if not args.label.replace('-', '').replace('_', '').isalnum():
    raise ValueError('label 必须是字母、数字、短横线或下划线')
out = Path('.tmp-os4') / f'{args.label}-aod-{time.strftime("%Y%m%d-%H%M%S")}'
out.mkdir(exist_ok=False)
base = ['adb', '-s', args.serial]


def run(*cmd, timeout=40):
    result = subprocess.run(base + list(cmd), capture_output=True, timeout=timeout, check=True)
    return result.stdout.decode('utf-8', errors='replace').strip()


def save(name, *cmd):
    text = run(*cmd)
    (out / name).write_text(text, encoding='utf-8')
    return text


def shot(name):
    data = subprocess.run(base + ['exec-out', 'screencap', '-p'],
                          capture_output=True, timeout=30, check=True).stdout
    (out / name).write_bytes(data)


if run('get-state') != 'device':
    raise RuntimeError('设备不在线')
boot = run('shell', 'cat', '/proc/sys/kernel/random/boot_id')
save('module.txt', 'shell', 'dumpsys', 'package', 'com.freeform.unbounded')
power = run('shell', 'dumpsys', 'power')
if 'showing=true' not in run('shell', 'dumpsys', 'window', 'policy'):
    run('shell', 'input', 'keyevent', 'KEYCODE_SLEEP')
    time.sleep(1.0)
    power = run('shell', 'dumpsys', 'power')
if 'mWakefulness=Awake' not in power:
    run('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP')
    time.sleep(0.8)
    if 'mWakefulness=Awake' not in run('shell', 'dumpsys', 'power'):
        run('shell', 'input', 'keyevent', 'KEYCODE_POWER')
time.sleep(1.2)
power = save('power-before.txt', 'shell', 'dumpsys', 'power')
window = save('window-before.txt', 'shell', 'dumpsys', 'window', 'policy')
if 'mWakefulness=Awake' not in power or 'showing=true' not in window:
    raise RuntimeError('未确认亮屏锁屏，停止采集，避免把 AOD 脉冲当成锁屏')
shot('lockscreen.png')

remote = f'/data/local/tmp/freeform-{args.label}-aod-{time.time_ns()}.mp4'
timeline = {'serial': args.serial, 'boot_id': boot, 'label': args.label, 'remote_recording': remote}
with (out / 'logcat.txt').open('wb') as logs, (out / 'recorder.txt').open('wb') as recorder_log:
    logger = subprocess.Popen(base + ['logcat', '-b', 'all', '-T', '1', '-v', 'threadtime', '-s',
                              'FreeformUnbounded:I', 'FreeformEvidence:I',
                              'KeyguardPanelViewController:D', 'PowerManagerService:I', 'AndroidRuntime:E'],
                              stdout=logs, stderr=subprocess.STDOUT)
    recorder = subprocess.Popen(base + ['shell', 'screenrecord', '--time-limit', '5', '--bit-rate', '6000000',
                                        '--size', '576x1250', remote], stdout=recorder_log, stderr=subprocess.STDOUT)
    try:
        time.sleep(0.6)
        timeline['power_key_host_ns'] = time.time_ns()
        run('shell', 'log', '-t', 'FreeformEvidence', f'{args.label}_POWER_KEY')
        run('shell', 'input', 'keyevent', 'KEYCODE_POWER')
        time.sleep(0.15)
        shot('transition.png')
        recorder.wait(timeout=18)
        run('pull', remote, str(out / 'transition.mp4'))
        save('power-aod.txt', 'shell', 'dumpsys', 'power')
        save('window-aod.txt', 'shell', 'dumpsys', 'window', 'policy')
        save('systemui-aod.txt', 'shell', 'dumpsys', 'activity', 'service', 'com.android.systemui/.SystemUIService')
        shot('aod.png')
    finally:
        if recorder.poll() is None:
            recorder.terminate()
            recorder.wait(timeout=8)
        logger.terminate()
        logger.wait(timeout=8)
        run('shell', 'rm', '-f', remote)
timeline['boot_after'] = run('shell', 'cat', '/proc/sys/kernel/random/boot_id')
if timeline['boot_after'] != boot:
    raise RuntimeError('boot ID 改变')
timeline['aod_dozing'] = 'mWakefulness=Dozing' in (out / 'power-aod.txt').read_text(encoding='utf-8')
(out / 'evidence.json').write_text(json.dumps(timeline, ensure_ascii=False, indent=2), encoding='utf-8')
print(json.dumps({'output': str(out), **timeline}, ensure_ascii=False, indent=2))
