import subprocess, time, sys, drive
S = ["adb","-s","emulator-5554"]
def run(args, t=120):
    try:
        r = subprocess.run(args, capture_output=True, text=True, timeout=t); return (r.stdout+r.stderr).strip()
    except subprocess.TimeoutExpired: return "TIMEOUT"
def f(): sys.stdout.flush()
def wins(): return run(S+["shell","dumpsys","window","windows"], 90).lower().count("worldgallery")
run(S+["shell","am","force-stop","com.worldgallery.spatial"], 60); time.sleep(3)
run(S+["shell","monkey","-p","com.worldgallery.spatial","-c","android.intent.category.LAUNCHER","1"], 90); time.sleep(16)
print("windows:", wins()); f()
drive.cmd("wipe"); drive.cmd("lang","en"); drive.cmd("hintSeen"); time.sleep(2)
drive.cmd("start","essentials"); time.sleep(12); print(drive.shot("r8-01-before-next")); f()
run(S+["logcat","-c"], 90); drive.cmd("next"); t0=time.time()
for d,name in ((1.5,"r8-02-next-1s5"),(5.0,"r8-03-next-5s"),(12.0,"r8-04-next-12s")):
    while time.time()-t0 < d: time.sleep(0.05)
    print(f"+{time.time()-t0:.1f}s", drive.shot(name)); f()
drive.cmd("lang","zh"); time.sleep(12); print(drive.shot("r8-05-viewer-zh")); f()
drive.cmd("info"); time.sleep(12); print(drive.shot("r8-06-info-zh")); drive.cmd("closeInfo"); f()
drive.cmd("tab","sculpture"); time.sleep(12); print(drive.shot("r8-07-sculpture-zh")); f()
print("DONE")
