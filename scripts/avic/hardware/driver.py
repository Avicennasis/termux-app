"""Actual visible Gboard controls for the full Termux-Avic app, outside its APK.

Always inspect the current production layout before assigning coordinates.
`setup_command` is only for synthetic test setup; IME claims must use taps/glide.
"""
import pathlib
import re
import subprocess
import time
import xml.etree.ElementTree as E

ADB = ["/home/avicennasis/Android/Sdk/platform-tools/adb", "-s", "R5CXB20H1PE"]
PACKAGE = "com.termuxavic"
KEYS = {}
BUTTONS = {}

def inspected_portrait_gboard():
    """S24 Ultra docked portrait layout inspected in production on 2026-10-02.

    Reinspect screenshots before using this after a keyboard/layout change.
    """
    KEYS.clear()
    for labels, xs, y in [
        ("1234567890", [60,164,271,380,487,595,700,810,918,1027], 1560),
        ("qwertyuiop", [60,164,271,380,487,595,700,810,918,1027], 1693),
        ("asdfghjkl", [109,218,325,433,541,649,756,864,974], 1836),
        ("zxcvbnm", [218,325,433,541,649,756,864], 1978),
    ]:
        KEYS.update({label:(x,y) for label,x in zip(labels,xs)})
    KEYS.update({" ":(591,2120), ".":(860,2120), "ENTER":(997,2120),
                 "DEL":(995,1978), "SHIFT":(80,1978), "SYMBOLS":(80,2120),
                 "EMOJI":(325,2120)})

def run(*args):
    return subprocess.check_output(ADB + list(args), text=True).strip()

def unlocked():
    trust = run("shell", "dumpsys", "trust")
    assert re.search(r'User "Owner"[^\n]*deviceLocked=0', trust), "Physical unlock required"

def guard():
    unlocked()
    activity = run("shell", "dumpsys", "activity", "activities")
    top = [line for line in activity.splitlines() if "topResumedActivity" in line]
    assert any(PACKAGE + "/" in line for line in top), "Personal app is not foreground"

def tap(x, y):
    run("shell", "input", "tap", str(x), str(y))
    time.sleep(.12)

def snapshot():
    run("shell", "uiautomator", "dump", "/data/local/tmp/termux-avic-ui.xml")
    raw = subprocess.check_output(ADB + ["exec-out", "cat", "/data/local/tmp/termux-avic-ui.xml"])
    return E.fromstring(raw)

def center(node):
    b = list(map(int, re.findall(r"\d+", node.get("bounds", ""))))
    return ((b[0]+b[2])//2, (b[1]+b[3])//2)

def find_text(text):
    return [n for n in snapshot().iter("node") if n.get("text") == text]

def click_text(text):
    matches = find_text(text)
    assert len(matches) == 1, f"Expected one visible {text} control"
    tap(*center(matches[0]))

def refresh_buttons():
    aliases = {"↹": "TAB", "←": "LEFT", "↓": "DOWN", "↑": "UP", "→": "RIGHT", "⌨": "KEYBOARD", "―":"-"}
    BUTTONS.clear()
    for node in snapshot().iter("node"):
        if node.get("package") == PACKAGE and node.get("class") == "android.widget.Button":
            label = node.get("text", "")
            if label:
                BUTTONS[aliases.get(label, label)] = center(node)
    return BUTTONS

def key(label):
    guard()
    tap(*KEYS[label])

def type_keys(value):
    guard()
    for letter in value:
        tap(*KEYS[letter])

def extra(label):
    guard()
    tap(*BUTTONS[label])

def glide(value):
    guard()
    points = [KEYS[x] for x in value]
    run("shell", "input", "touchscreen", "motionevent", "DOWN", *map(str, points[0]))
    for a,b in zip(points,points[1:]):
        for j in range(1,5):
            run("shell", "input", "touchscreen", "motionevent", "MOVE", str(round(a[0]+(b[0]-a[0])*j/4)), str(round(a[1]+(b[1]-a[1])*j/4)))
    run("shell", "input", "touchscreen", "motionevent", "UP", *map(str, points[-1]))
    time.sleep(.3)

def setup_command(value):
    guard()
    assert "\n" not in value and "\r" not in value, "ADB setup text must be one line"
    # InputManager text injection is used only for harness setup, not keyboard validation.
    import shlex
    run("shell", "input text " + shlex.quote(value.replace(" ", "%s")))
    run("shell", "input", "keyevent", "66")
    time.sleep(.5)

def screenshot(path):
    guard()
    target = pathlib.Path(path)
    target.write_bytes(subprocess.check_output(ADB + ["exec-out", "screencap", "-p"]))
    target.chmod(0o600)
