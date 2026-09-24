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

print("install:", run(S+["install","-r","-t","../app/build/outputs/apk/debug/app-debug.apk"], 900)[-12:]); f()
for mode in ("context","navigator","intent"):
    print(f"\n##### reopenMode={mode}"); f()
    fresh(); drive.cmd("reopenMode", mode); time.sleep(1)
    for rnd in (1,2):
        clear(); drive.cmd("immersive"); time.sleep(22)
        t = tags(6); alive = "stage disposed" not in t
        print(f"[{mode} round {rnd}] enter: stage alive={alive}"); print(t); f()
        if not alive:
            print("  → stage died on enter; windows:", wins()); break
        clear(); drive.cmd("leaveImmersive"); time.sleep(16)
        t = tags(8); print(f"[{mode} round {rnd}] leave:"); print(t); print("  windows:", wins()); f()
        if wins() == 0: print("  → no window after leave"); break
print("DONE")
