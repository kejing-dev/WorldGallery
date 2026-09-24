import subprocess, time, sys, drive
S = ["adb","-s","emulator-5554"]
def run(args, t=120):
    try:
        r = subprocess.run(args, capture_output=True, text=True, timeout=t); return (r.stdout+r.stderr).strip()
    except subprocess.TimeoutExpired: return "TIMEOUT"
def f(): sys.stdout.flush()
def tags(n=6, pat="WorldGallery.VM:I WorldGallery.Stage:I WorldGallery.Main:I WorldGallery.Scene:I WorldGallery.Dbg:I"):
    o = run(S+["logcat","-d","-s"]+pat.split(), 150)
    return "\n".join(l[19:150] for l in o.splitlines()[-n:]) if o != "TIMEOUT" else "(timeout)"
def windows():
    o = run(S+["shell","dumpsys","window","windows"], 90)
    return sorted(set(l.strip()[:90] for l in o.splitlines() if "worldgallery" in l.lower() and ("Window #" in l or "mActivityRecord" in l)))[:6]

print("install:", run(S+["install","-r","-t","../app/build/outputs/apk/debug/app-debug.apk"], 900)[-20:]); f()
run(S+["logcat","-c"], 90)
run(S+["shell","monkey","-p","com.worldgallery.spatial","-c","android.intent.category.LAUNCHER","1"], 90)
time.sleep(16)
drive.cmd("wipe"); drive.cmd("lang","en"); drive.cmd("hintSeen"); time.sleep(3)
drive.cmd("start","essentials"); time.sleep(8)
print(drive.shot("r2-01-viewer-en")); f()
run(S+["logcat","-c"], 90)
drive.cmd("next"); time.sleep(0.15); print(drive.shot("r2-02-next-midfade")); time.sleep(3); print(drive.shot("r2-03-next-done")); f()
drive.cmd("info"); time.sleep(5); print(drive.shot("r2-04-info-en")); drive.cmd("closeInfo"); time.sleep(2); f()

print("\n### Full Space"); f()
drive.cmd("immersive"); time.sleep(24)
print(drive.shot("r2-05-fullspace-strip")); print(tags(8)); print("windows:", windows()); f()
run(S+["logcat","-c"], 90)
drive.cmd("next"); time.sleep(6); print(drive.shot("r2-06-fullspace-next")); print(tags(6)); f()
run(S+["logcat","-c"], 90)
drive.cmd("exitImmersive"); time.sleep(10)
print(drive.shot("r2-07-back-in-gallery")); print(tags(10)); print("windows:", windows()); print(drive.cmd("state")); f()

print("\n### Frame round-trip"); f()
drive.cmd("frame"); time.sleep(12); print(drive.shot("r2-08-frame-open")); f()
run(S+["logcat","-c"], 90)
drive.cmd("immersive"); time.sleep(22); print(drive.shot("r2-09-fullspace-with-frame")); print(tags(8)); f()
run(S+["logcat","-c"], 90)
drive.cmd("exitImmersive"); time.sleep(12); print(drive.shot("r2-10-both-back")); print(tags(10)); print("windows:", windows()); f()

print("\n### zh"); f()
drive.cmd("lang","zh"); time.sleep(4); print(drive.shot("r2-11-viewer-zh")); f()
drive.cmd("info"); time.sleep(5); print(drive.shot("r2-12-info-zh")); drive.cmd("closeInfo"); f()
print("DONE")
