# InfoWedge Scan Guard

**Fixes a hardware barcode scanner that goes dead after the camera is used** on MUNBYN Android handhelds
(Chainway-based, with the *InfoWedge* scanning service) — for example when a **Microsoft Power Apps** canvas app takes a photo.

## The problem

On a MUNBYN IPDA101 / IPDA101P (Android 13, Zebra SE4710 engine, InfoWedge 1.55.SH) we saw this, several times a day:

- An app takes a photo (Power Apps *Camera* control, or *Add picture* via the system camera).
- Afterwards the scan engine is **dead everywhere on the device**: the trigger does nothing, no aimer, no laser — also outside the app, and also with the app in the background.
- It only comes back when you **open the InfoWedge app**, or **open and close Android's Camera app**.
- A native app with its own camera screen (it switches activity) did not show the problem on the same devices.

What did **not** help: battery *Unrestricted* for InfoWedge / ScannerService / Power Apps; switching the camera off inside the app before leaving the photo screen; using the system camera (Add picture); an InfoWedge profile with scanning disabled for the camera app.

MUNBYN and Zebra both document that the camera and the scan engine cannot run at the same time; the wedge evidently does not re-acquire the engine afterwards when the camera was used inside the foreground app.

## The fix

InfoWedge accepts Android broadcasts (MUNBYN *InfoWedgeAPI Programming Guide* v1.4, in the InfoWedge SDK on MUNBYN's support site).
Turning the InfoWedge service **off and on again** brings the scanner back:

```
adb shell am broadcast -a com.symbol.infowedge.api.ACTION --ez com.symbol.infowedge.api.ENABLE_INFOWEDGE false
adb shell am broadcast -a com.symbol.infowedge.api.ACTION --ez com.symbol.infowedge.api.ENABLE_INFOWEDGE true
```

Tested: it revives the dead scanner without opening InfoWedge or the camera, and sending it **while a camera preview is running does not disturb the camera**.

Apps like Power Apps cannot send broadcasts themselves. This small app does it for them.

## What the app does

- **Background guard.** A foreground service watches the device cameras (`CameraManager.AvailabilityCallback`).
  When a camera goes from *in use* to *free*, it waits 0.8 s and restarts InfoWedge (off, 0.7 s, on).
  Camera changes caused by its own restart are learned and ignored, so it cannot loop.
- **Link for apps.** Opening `scanguard://restart` restarts the scanner and returns straight to the calling app.
  In Power Apps: `Launch("scanguard://restart")` (for example right after a photo).
- Starts again after a reboot. The status screen shows the last 30 events and has a *Restart scanner now* button.

No data is collected or sent anywhere; the app only talks to InfoWedge on the device.

## Build

GitHub Actions builds it on every push: **Actions → Build APK → artifact `InfoWedgeScanGuard-apk`** (contains `app-debug.apk`).
Locally: JDK 17 + Gradle 8.7 + Android SDK 34, then `gradle assembleDebug`.

## Install

```
adb install app-debug.apk
```

Then open **InfoWedge Scan Guard** once (allow notifications) and set its battery use to **Unrestricted**.
Update with `adb install -r app-debug.apk`; if Android refuses because the signature changed (each CI build uses its own debug key), run `adb uninstall com.scanguard.infowedge` first.

## Compatibility

Written for MUNBYN IPDA101 / IPDA101P with InfoWedge. Other Chainway-based devices that use InfoWedge (`com.rscja.infowedge`) should behave the same; reports welcome.
The broadcast is sent to `com.rscja.infowedge` and `com.rscja.scanservice`.

## License

MIT — see `LICENSE`.
