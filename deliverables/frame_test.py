import subprocess, time, sys, drive
def run(args, t=120):
    try:
        r = subprocess.run(args, capture_output=True, text=True, timeout=t); return (r.stdout+r.stderr).strip()
    except subprocess.TimeoutExpired: return "TIMEOUT"
def f(): sys.stdout.flush()
def tags(n=4):
    o = run(["adb","-s","emulator-5554","logcat","-d","-s","WorldGallery.VM:I","WorldGallery.Dbg:I"], 150)
    return "\n".join(l[:130] for l in o.splitlines()[-n:]) if o != "TIMEOUT" else "(timeout)"

print("install:", run(["adb","-s","emulator-5554","install","-r","-t","../app/build/outputs/apk/debug/app-debug.apk"], 900)[-30:]); f()
run(["adb","-s","emulator-5554","logcat","-c"], 90)
run(["adb","-s","emulator-5554","shell","monkey","-p","com.worldgallery.spatial","-c","android.intent.category.LAUNCHER","1"], 90)
time.sleep(16)
drive.cmd("wipe"); drive.cmd("lang","en"); drive.cmd("hintSeen"); time.sleep(3)
drive.cmd("start","essentials"); time.sleep(6)
drive.cmd("frame"); time.sleep(12)
print(drive.shot("f-01-frame-default")); print(tags(2)); f()
drive.cmd("frameSize","in"); time.sleep(8); print(drive.shot("f-02-frame-larger")); print(tags(2)); f()
drive.cmd("frameSize","in"); time.sleep(8); print(drive.shot("f-03-frame-largest")); print(tags(2)); f()
drive.cmd("frameSize","out"); drive.cmd("frameSize","out"); drive.cmd("frameSize","out"); time.sleep(10)
print(drive.shot("f-04-frame-small")); print(tags(2)); f()
drive.cmd("frameLoop","essentials"); time.sleep(6)
drive.cmd("frameSettings","on"); time.sleep(8); print(drive.shot("f-05-frame-settings")); f()
print("DONE")
