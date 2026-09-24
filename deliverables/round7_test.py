import subprocess, time, sys, drive
S = ["adb","-s","emulator-5554"]
def run(args, t=120):
    try:
        r = subprocess.run(args, capture_output=True, text=True, timeout=t); return (r.stdout+r.stderr).strip()
    except subprocess.TimeoutExpired: return "TIMEOUT"
def f(): sys.stdout.flush()
def tags(n=8):
    o = run(S+["logcat","-d","-s","WorldGallery.VM:I","WorldGallery.Stage:I","WorldGallery.Main:I","WorldGallery.Dbg:I"], 150)
    return "\n".join(l[6:140] for l in o.splitlines()[-n:] if "Dbg" not in l) if o != "TIMEOUT" else "(timeout)"
def wins(): return run(S+["shell","dumpsys","window","windows"], 90).lower().count("worldgallery")
def clear(): run(S+["logcat","-c"], 90)
def fresh():
    run(S+["shell","am","force-stop","com.worldgallery.spatial"], 60); time.sleep(2)
    run(S+["shell","monkey","-p","com.worldgallery.spatial","-c","android.intent.category.LAUNCHER","1"], 90); time.sleep(14)
    drive.cmd("wipe"); drive.cmd("lang","en"); drive.cmd("hintSeen"); time.sleep(2)
    drive.cmd("start","essentials"); time.sleep(8)
def roundtrip(label, leave):
    clear(); drive.cmd("immersive"); time.sleep(22)
    t = tags(6); alive = "stage disposed" not in t and "immersive opened" in t
    print(f"[{label}] enter: stage alive={alive}"); f()
    if not alive: print(t); print("  windows:", wins()); return False
    clear(); leave(); time.sleep(16)
    print(f"[{label}] leave:"); print(tags(8)); print("  windows:", wins()); f()
    return wins() > 0

print("install:", run(S+["install","-r","-t","../app/build/outputs/apk/debug/app-debug.apk"], 900)[-12:]); f()
print("\n##### default (minimize/restore)"); f()
fresh(); base = wins(); print("baseline windows:", base)
ok1 = roundtrip("min r1", lambda: drive.cmd("leaveImmersive")); print(drive.shot("r7-01-after-r1"))
ok2 = roundtrip("min r2", lambda: drive.cmd("leaveImmersive")); print(drive.shot("r7-02-after-r2"))
print("\n### frame round-trip"); f()
drive.cmd("frame"); time.sleep(12); print("windows with frame:", wins()); print(drive.shot("r7-03-frame-open"))
ok3 = roundtrip("min frame", lambda: drive.cmd("leaveImmersive")); print(drive.shot("r7-04-frame-back"))
print("\n### system BACK"); f()
drive.cmd("closeFrame"); time.sleep(5)
ok4 = roundtrip("min back", lambda: run(S+["shell","input","keyevent","BACK"], 60)); print(drive.shot("r7-05-after-back"))
if wins() == 0: run(S+["shell","monkey","-p","com.worldgallery.spatial","-c","android.intent.category.LAUNCHER","1"], 90); time.sleep(12)

print("\n##### context (close/reopen, for the record)"); f()
fresh(); drive.cmd("reopenMode","context"); time.sleep(1)
roundtrip("ctx r1", lambda: drive.cmd("leaveImmersive")); roundtrip("ctx r2", lambda: drive.cmd("leaveImmersive"))
if wins() == 0: run(S+["shell","monkey","-p","com.worldgallery.spatial","-c","android.intent.category.LAUNCHER","1"], 90); time.sleep(12)
drive.cmd("reopenMode","minimize")

print("\n### crossfade (window, long waits) + zh"); f()
time.sleep(4); print(drive.shot("r7-06-before-next"))
clear(); drive.cmd("next"); t0=time.time()
for d,name in ((1.5,"r7-07-next-1s5"),(5.0,"r7-08-next-5s"),(12.0,"r7-09-next-12s")):
    while time.time()-t0 < d: time.sleep(0.05)
    print(f"+{time.time()-t0:.1f}s", drive.shot(name)); f()
drive.cmd("lang","zh"); time.sleep(12); print(drive.shot("r7-10-viewer-zh")); f()
drive.cmd("info"); time.sleep(12); print(drive.shot("r7-11-info-zh")); drive.cmd("closeInfo"); f()
print("CRASH lines:", len([l for l in run(S+["logcat","-d","-b","crash"], 120).splitlines() if "worldgallery" in l]))
print("DONE")
