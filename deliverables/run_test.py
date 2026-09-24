import subprocess, time, sys, drive
def run(args, t=90):
    try:
        r = subprocess.run(args, capture_output=True, text=True, timeout=t); return (r.stdout+r.stderr).strip()
    except subprocess.TimeoutExpired: return "TIMEOUT"
def tags(n=5):
    out = run(["adb","-s","emulator-5554","logcat","-d","-s","WorldGallery.Dbg:I","WorldGallery.VM:I","WorldGallery.Stage:I","WorldGallery.Scene:I","WorldGallery.Main:I"], 120)
    return "\n".join(l[:140] for l in out.splitlines()[-n:]) if out != "TIMEOUT" else "(logcat timeout)"
def focus():
    f = run(["adb","-s","emulator-5554","shell","dumpsys activity activities | grep mFocusedApp | head -1"], 90)
    return f.strip()[:95]
def flush(): sys.stdout.flush()

print("install:", run(["adb","-s","emulator-5554","install","-r","-t","../app/build/outputs/apk/debug/app-debug.apk"], 400)[-30:]); flush()
run(["adb","-s","emulator-5554","logcat","-c"], 90)
run(["adb","-s","emulator-5554","shell","monkey","-p","com.worldgallery.spatial","-c","android.intent.category.LAUNCHER","1"], 90)
time.sleep(18); print("focused:", focus()); flush()
drive.cmd("wipe"); drive.cmd("lang","en"); drive.cmd("hintSeen"); time.sleep(3)
drive.cmd("start","essentials"); time.sleep(8); print(drive.shot("t-00-viewer")); flush()

print("\n### Full Space round-trip (Stage closes the windows, reopens them on exit)"); flush()
drive.cmd("immersive"); time.sleep(22)
print(drive.shot("t-01-immersive")); print(tags(4)); flush()
drive.cmd("exitImmersive"); time.sleep(12)
print(drive.shot("t-02-back-in-gallery")); print("focused:", focus()); print(tags(6)); flush()
print("\nCRASH:", len([l for l in run(["adb","-s","emulator-5554","logcat","-d","-b","crash"], 120).splitlines() if "worldgallery" in l]))
print("DONE")
