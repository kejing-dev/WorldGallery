import subprocess, time, sys, drive
S = ["adb","-s","emulator-5554"]
def run(args, t=120):
    try:
        r = subprocess.run(args, capture_output=True, text=True, timeout=t); return (r.stdout+r.stderr).strip()
    except subprocess.TimeoutExpired: return "TIMEOUT"
def f(): sys.stdout.flush()
def tags(n=8):
    o = run(S+["logcat","-d","-s","WorldGallery.VM:I","WorldGallery.Stage:I","WorldGallery.Main:I","WorldGallery.Scene:I","WorldGallery.Dbg:I"], 150)
    return "\n".join(l[6:150] for l in o.splitlines()[-n:]) if o != "TIMEOUT" else "(timeout)"
def wins(): return run(S+["shell","dumpsys","window","windows"], 90).lower().count("worldgallery")
def clear(): run(S+["logcat","-c"], 90)
def relaunch():
    run(S+["shell","monkey","-p","com.worldgallery.spatial","-c","android.intent.category.LAUNCHER","1"], 90); time.sleep(12)

print("install:", run(S+["install","-r","-t","../app/build/outputs/apk/debug/app-debug.apk"], 900)[-12:]); f()
run(S+["shell","am","force-stop","com.worldgallery.spatial"], 60); time.sleep(2)
relaunch(); time.sleep(4)
drive.cmd("wipe"); drive.cmd("lang","en"); drive.cmd("hintSeen"); time.sleep(3)
drive.cmd("start","essentials"); time.sleep(8); print(drive.shot("r3-01-viewer")); f()

print("\n### crossfade timing (window)"); f()
clear(); t0=time.time(); drive.cmd("next")
for d,name in ((0.6,"r3-02-next-0s6"),(1.6,"r3-03-next-1s6"),(3.5,"r3-04-next-3s5")):
    while time.time()-t0 < d: time.sleep(0.05)
    print(f"+{time.time()-t0:.1f}s", drive.shot(name)); f()
print(tags(3)); f()

print("\n### Full Space → strip exit"); f()
clear(); drive.cmd("immersive"); time.sleep(24); print(drive.shot("r3-05-fullspace")); print(tags(6)); f()
clear(); drive.cmd("next"); time.sleep(7); print(drive.shot("r3-06-fullspace-next")); print(tags(4)); f()
clear(); drive.cmd("leaveImmersive"); time.sleep(12); print(drive.shot("r3-07-after-leave")); print(tags(10)); print("windows:", wins()); f()
if wins() == 0: print("!! no window — relaunching"); relaunch()

print("\n### frame round-trip"); f()
drive.cmd("frame"); time.sleep(12); print(drive.shot("r3-08-frame-open")); f()
clear(); drive.cmd("immersive"); time.sleep(22); print(drive.shot("r3-09-fullspace-with-frame")); print(tags(4)); f()
clear(); drive.cmd("leaveImmersive"); time.sleep(14); print(drive.shot("r3-10-both-back")); print(tags(10)); print("windows:", wins()); f()
if wins() == 0: print("!! no window — relaunching"); relaunch()

print("\n### system BACK path"); f()
drive.cmd("closeFrame"); time.sleep(3)
clear(); drive.cmd("immersive"); time.sleep(22); print(drive.shot("r3-11-fullspace-again")); f()
clear(); run(S+["shell","input","keyevent","BACK"], 60); time.sleep(14); print(drive.shot("r3-12-after-back")); print(tags(10)); print("windows:", wins()); f()
if wins() == 0: print("!! no window — relaunching"); relaunch()

print("\n### zh"); f()
drive.cmd("lang","zh"); time.sleep(4); print(drive.shot("r3-13-viewer-zh")); f()
drive.cmd("info"); time.sleep(5); print(drive.shot("r3-14-info-zh")); drive.cmd("closeInfo"); f()
print("CRASH lines:", len([l for l in run(S+["logcat","-d","-b","crash"], 120).splitlines() if "worldgallery" in l]))
print("DONE")
