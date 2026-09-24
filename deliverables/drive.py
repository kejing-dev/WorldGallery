#!/usr/bin/env python3
"""Emulator test driver: python3 drive.py <cmd[:arg]> [shot=name] [wait=N] ...   (see README debug channel)."""
import subprocess, sys, time, re, os
DEV = os.environ.get("DEV", "emulator-5554")
HERE = os.path.dirname(os.path.abspath(__file__))

def run(args, t=60, binary=False):
    try:
        r = subprocess.run(args, capture_output=True, timeout=t)
        return r.stdout if binary else (r.stdout + r.stderr).decode(errors="replace")
    except subprocess.TimeoutExpired:
        return b"" if binary else f"TIMEOUT {t}s"

def cmd(c, arg=""):
    extra = ["--es", "arg", arg] if arg else []      # an empty --es value makes `am` drop the broadcast
    return run(["adb", "-s", DEV, "shell", "am", "broadcast", "-a", "com.worldgallery.spatial.DEBUG", "--es", "cmd", c] + extra, 30)

def shot(name):
    png = run(["adb", "-s", DEV, "exec-out", "screencap", "-p"], 120, binary=True)
    if len(png) > 1000:
        os.makedirs(os.path.join(HERE, "screenshots"), exist_ok=True)
        open(os.path.join(HERE, "screenshots", name + ".png"), "wb").write(png)
        return f"shot {name}: {len(png)} bytes"
    return f"shot {name}: FAILED"

def logs(pattern=r"WorldGallery\.(Scene|Viewer|VM|Model|Main|Stage)|AndroidRuntime|FATAL|am_anr", n=8, exclude="Dbg"):
    out = run(["adb", "-s", DEV, "logcat", "-d", "-t", "1500"], 60)
    lines = [l[:230] for l in out.splitlines() if re.search(pattern, l) and (not exclude or exclude not in l)]
    return "\n".join(lines[-n:])

def state():
    out = run(["adb", "-s", DEV, "logcat", "-d", "-s", "WorldGallery.Dbg:I"], 60)
    ls = [l for l in out.splitlines() if "cmd=" in l]
    return ls[-1][:900] if ls else "(no state)"

if __name__ == "__main__":
    for step in sys.argv[1:]:
        if step.startswith("shot="): print(shot(step[5:]))
        elif step.startswith("wait="): time.sleep(float(step[5:]))
        elif step == "logs": print(logs())
        elif step == "state": print(state())
        else:
            c, _, a = step.partition(":"); cmd(c, a); time.sleep(1.5)
            print(f"> {c} {a}")
