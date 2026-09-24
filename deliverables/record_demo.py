#!/usr/bin/env python3
"""Record a ~40 s demo of World Gallery on the PICO emulator through the debug channel (debug APK installed).

Refuses to run while another app has focus (the emulator is shared between sessions). Output:
deliverables/WorldGallery-demo.mp4 (1080x1080 H.264). The emulator's display only refreshes at ~1 Hz when nobody
operates its window, so expect a slideshow-like recording; a headset gives a smooth one.
"""
import subprocess, sys, time, drive

S = ["adb", "-s", "emulator-5554"]
def run(args, t=120):
    return subprocess.run(args, capture_output=True, text=True, timeout=t).stdout.strip()

focus = run(S + ["shell", "dumpsys", "window"], 90)
front = next((l.strip() for l in focus.splitlines() if "mCurrentFocus" in l), "")
if "worldgallery" not in front and "com.pxr" not in front and "launcher" not in front.lower():
    sys.exit(f"emulator busy: {front} — not recording")

run(S + ["shell", "monkey", "-p", "com.worldgallery.spatial", "-c", "android.intent.category.LAUNCHER", "1"], 90)
time.sleep(14)
drive.cmd("lang", "en"); drive.cmd("hintSeen"); drive.cmd("tab", "paintings"); time.sleep(4)

rec = subprocess.Popen(S + ["shell", "screenrecord", "--time-limit", "42", "--bit-rate", "8000000", "/sdcard/wg-demo.mp4"])
steps = [("start", "essentials", 6), ("next", "", 5), ("zoomIn", "", 2), ("zoomIn", "", 4), ("fit", "", 3),
         ("info", "", 5), ("closeInfo", "", 2), ("frame", "", 6), ("immersive", "", 8), ("next", "", 4), ("leaveImmersive", "", 5)]
for cmd, arg, wait in steps:
    drive.cmd(cmd, arg) if arg else drive.cmd(cmd)
    time.sleep(wait)
rec.wait(timeout=60)
run(S + ["pull", "/sdcard/wg-demo.mp4", "raw-demo.mp4"], 300)
subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", "raw-demo.mp4",
                "-vf", "scale=1080:1080:force_original_aspect_ratio=decrease,pad=1080:1080:(ow-iw)/2:(oh-ih)/2",
                "-c:v", "libx264", "-crf", "24", "-pix_fmt", "yuv420p", "-movflags", "+faststart", "-an", "WorldGallery-demo.mp4"], check=True)
print("wrote deliverables/WorldGallery-demo.mp4")
