# PocketMOBAlert Free

A man-overboard alarm that uses the crew's own phones. No extra hardware, no internet: two or more phones on
the same boat watch each other over Bluetooth.

**Crew mode** sends a small Bluetooth signal in the background. **Watch mode** listens for the paired crew
phones and sounds a loud alarm as soon as one goes out of range. Every phone can run both modes at once, so the
crew protect each other; no single "captain's phone" is needed.

**This is a free aid for small crews and day trips, not a replacement for a certified AIS personal locator
beacon (PLB).** A phone is bigger and less waterproof than a PLB, and it only works if the person is carrying it.
A PLB costs USD 250-600 per person; with this app, protecting one more crew member costs nothing, because
everyone already has a phone.

**The alarm never needs internet.** Detection and alarm run only over Bluetooth. The free version does not even
ask for internet permission, so having no signal on deck makes no difference.

Made by **[Trozovka](https://github.com/Trozovka) (John Winsome Munar)**, Master Mariner.

Keywords: man overboard, MOB alarm, crew overboard, Bluetooth, BLE, safety, PLB alternative, offline, Android, boat, ship, seafarers.

## Screenshots

| Crew Mode | Watch Mode |
|---|---|
| ![Crew Mode](gumroad-assets/01_free_crew_mode.png) | ![Watch Mode](gumroad-assets/02_free_watch_mode.png) |

## What it does

- **Crew mode:** sends the Bluetooth signal with the screen off (tested over 65 seconds screen-off on real phones).
- **Watch mode:** listens all the time. Pairing is simple: bring the phone close and give it a name
  ("Chief Engineer", "Skipper").
- A two-step check (about 8 seconds at worst) tells a real man-overboard apart from a normal short loss of
  signal (someone going below deck), without delaying a real alarm.
- Alarm at full volume with vibration, even on silent or Do Not Disturb, and passed on to every other watching
  phone on board.
- Saves the GPS position and time the moment the alarm goes off. Free keeps the latest alert.
- Free forever, no time limit: the full alarm, for up to 2 paired crew phones.

## Install (no building needed)

Coming soon to Google Play. Until then, install the APK:

1. Download it from **[GitHub Releases](https://github.com/Trozovka/pocketmobalert-free/releases)** or
   **[Gumroad](https://trozovka.gumroad.com/l/pocketmobalert-free)** (free).
2. Open it on the phone. Android asks you to allow installs from this source the first time. That is normal
   for apps installed outside Google Play.
3. Allow Bluetooth, location and notifications, and allow the battery exemption when asked.

## How to use it: run both modes on every phone

Crew mode and Watch mode work separately and can run together. For two or more people to protect each other,
**every phone should run both modes**: each phone sends its own signal *and* watches for the others. If one
phone only sends and another only watches, only the sending person is protected.

1. On each phone, start **Crew mode**.
2. On each phone, open **Watch mode**, bring the other phones close one by one and name them.
3. Keep the phones with the crew. If one goes out of range, the watching phones sound the alarm.

## Pro version

**PocketMOBAlert Pro** adds unlimited paired crew, full alert history, and an optional "MOB" waypoint sent to
OpenCPN. Price and download: **[trozovka.gumroad.com/l/pocketmobalert-pro](https://trozovka.gumroad.com/l/pocketmobalert-pro)**.
The Pro source is private. This repo has the full source of the free version under the MIT license.

---

## For developers

### Technical details

- Crew mode = BLE peripheral advertising with a stable per-install identifier; survives Doze once the
  battery-optimization exemption is granted.
- Watch mode = continuous BLE scanning with a two-phase separation check (worst case about 8 s).
- Alarm on the alarm audio stream (bypasses silent/DND) and relayed to every paired Watch device.
- Free limits: 2 paired crew devices per voyage, alert log narrowed to the latest entry. The core alarm loop is never gated.

### Build from source

Requires JDK 17 and the Android SDK (the Gradle wrapper handles the rest).

```
git clone --recurse-submodules https://github.com/Trozovka/pocketmobalert-free.git
cd pocketmobalert-free
echo "sdk.dir=/path/to/your/android-sdk" > local.properties
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Tech stack

- Kotlin + Jetpack Compose, MVVM.
- Gradle multi-module: `:core` (BLE beacon/scan services, pairing, alarm, SQLite alert log via Room,
  entitlement logic, all UI; shared with Pro and the Play Store build) + `:app` (thin Free launcher).
  Internal package names still say `lite`; they are kept so existing installs keep updating.
- The foreground-service/wake-lock/battery-exemption pattern comes from
  [trozovka-android-toolkit](https://github.com/Trozovka/trozovka-android-toolkit) as a git submodule.
- minSdk 26, target latest stable Android API.
- Not a fork: no open-source project did phone-to-phone BLE beacon + watch/alarm for MOB safety; the closest
  were generic BLE proximity libraries, none maritime and none with multi-device alarm relay.

## License

MIT -- see [LICENSE](LICENSE) for the full text.

Copyright (c) 2026 Trozovka (John Winsome Munar). Original Author: Trozovka (John Winsome Munar). All derivative works must retain the
[NOTICE](NOTICE) file's attribution. Not a fork of, or derived from, any other project's source.
