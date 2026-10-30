"""Run against a disposable/local emulator; never clears app data.

Usage: python -X utf8 tests/smoke_android.py --serial 127.0.0.1:16384
The APK must already be installed. XML/screenshots are kept in verification/.
"""
from pathlib import Path
import argparse
import json
import re
import subprocess
import time
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
ADB = ROOT / '.toolchain/platform-tools/platform-tools/adb.exe'
PACKAGE = 'cn.local.focusmate'
parser = argparse.ArgumentParser()
parser.add_argument('--serial', required=True)
parser.add_argument('--running-timer', action='store_true', help='Test an already started disposable timer')
args = parser.parse_args()
OUT = ROOT / 'verification'
OUT.mkdir(exist_ok=True)
results = []

def adb(*args, binary=False):
    r = subprocess.run([str(ADB), '-s', globals()['args'].serial, *args],
                       capture_output=True, timeout=30)
    # This MuMu build sometimes segfaults in uiautomator's process finalizer,
    # after successfully writing the tree. Accept only that confirmed write.
    dumped = args[:3] == ('shell', 'uiautomator', 'dump') and b'UI hierchary dumped to:' in r.stdout
    if r.returncode and not (r.returncode == 139 and dumped):
        raise RuntimeError((args, r.returncode, r.stdout, r.stderr))
    return r.stdout if binary else r.stdout.decode('utf-8', errors='replace')

def snapshot(name):
    adb('shell', 'uiautomator', 'dump', '/sdcard/focus-ui.xml')
    xml = adb('shell', 'cat', '/sdcard/focus-ui.xml')
    (OUT / (name + '.xml')).write_text(xml, encoding='utf-8')
    root = ET.fromstring(xml)
    texts = [n.get('text', '') for n in root.iter('node')]
    assert '专注清单启动失败' not in texts, texts
    assert any(n.get('package') == PACKAGE for n in root.iter('node')), texts
    assert adb('shell', 'pidof', PACKAGE).strip(), 'Process died'
    return root, texts

def check(name, expected):
    root, texts = snapshot(name)
    assert expected in texts, (name, expected, texts)
    results.append(name)
    print('PASS', name, flush=True)
    return root

def click(root, label):
    nodes = [n for n in root.iter('node') if n.get('text') == label]
    assert nodes, 'Missing control: ' + label
    x1, y1, x2, y2 = map(int, re.findall(r'\d+', nodes[-1].get('bounds')))
    adb('shell', 'input', 'tap', str((x1+x2)//2), str((y1+y2)//2))
    time.sleep(.4)

if args.running_timer:
    adb('shell', 'am', 'start', '-W', '-n', PACKAGE + '/.MainActivity', '--ez', 'openTimer', 'true')
    root, texts = snapshot('timer-initial')
    if '准备开始一段专注' in texts:
        for _ in range(5):
            if any(n.get('text') == '开始专注' for n in root.iter('node')):
                break
            pane = next(n for n in root.iter('node') if n.get('scrollable') == 'true')
            x1, y1, x2, y2 = map(int, re.findall(r'\d+', pane.get('bounds')))
            adb('shell', 'input', 'swipe', str((x1+x2)//2), str(y2-80), str((x1+x2)//2), str(y1+80), '400')
            root, texts = snapshot('timer-find-start')
        click(root, '开始专注')
        # The first run may show an optional notification permission prompt.
        adb('shell', 'uiautomator', 'dump', '/sdcard/focus-ui.xml')
        if 'permissioncontroller' in adb('shell', 'cat', '/sdcard/focus-ui.xml'):
            adb('shell', 'input', 'keyevent', '4')
    root = check('timer-running', '暂停')
    click(root, '暂停')
    root = check('timer-paused', '继续专注')
    click(root, '继续专注')
    root = check('timer-resumed', '暂停')
    click(root, '沉浸显示')
    snapshot('timer-immersive')
    (OUT / 'timer-immersive.png').write_bytes(adb('exec-out', 'screencap', '-p', binary=True))
    adb('shell', 'input', 'keyevent', '4')
    adb('shell', 'input', 'keyevent', '3')
    time.sleep(2)
    adb('shell', 'am', 'start', '-W', '-n', PACKAGE + '/.MainActivity', '--ez', 'openTimer', 'true')
    check('timer-background-return', '暂停')
    adb('shell', 'am', 'force-stop', PACKAGE)
    adb('shell', 'am', 'start', '-W', '-n', PACKAGE + '/.MainActivity', '--ez', 'openTimer', 'true')
    root = check('timer-process-restore', '暂停')
    click(root, '完成')
    root = check('timer-finish-dialog', '结束本次专注？')
    click(root, '结束')
    check('timer-finished', '准备开始一段专注')
    (OUT / 'timer-results.json').write_text(json.dumps({'passed': results}, ensure_ascii=False, indent=2), encoding='utf-8')
    print('PASS all', len(results), 'timer checks')
    raise SystemExit(0)

for i in range(3):
    adb('shell', 'am', 'force-stop', PACKAGE)
    adb('shell', 'am', 'start', '-W', '-n', PACKAGE + '/.MainActivity')
    check('cold-start-' + str(i+1), '新建待办')

for extra, expected in [('openTimer', '准备开始一段专注'),
                        ('openFuture', '把重要的日子放在眼前'),
                        ('openStats', '看见积累，也看见节奏'),
                        ('openMe', '全部数据只保存在本机'),
                        ('openTasks', '新建待办')]:
    adb('shell', 'am', 'start', '-W', '-n', PACKAGE + '/.MainActivity', '--ez', extra, 'true')
    check(extra, expected)

root, _ = snapshot('tasks-before-editor')
click(root, '新建待办')
check('task-editor', '保存')
adb('shell', 'input', 'keyevent', '4')
adb('shell', 'am', 'start', '-W', '-n', PACKAGE + '/.MainActivity', '--ez', 'openTimer', 'true')
check('timer-idle', '准备开始一段专注')
# uiautomator waits for an idle UI. The former render/post loop never settled.
time.sleep(2)
check('timer-idle-stable', '准备开始一段专注')
(OUT / 'smoke-results.json').write_text(json.dumps({'passed': results}, ensure_ascii=False, indent=2), encoding='utf-8')
print('PASS all', len(results), 'checks')
