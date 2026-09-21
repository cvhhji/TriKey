# TriKey

TriKey maps one hardware key to independent single-click, double-click and long-press actions on Android.

## Requirements

- Android 8.0 or newer
- A libxposed API 102 compatible framework
- The module scope must include `System Framework (system)`

## Build

```bash
gradle :app:assembleRelease
```

GitHub Actions builds the release APK and uploads it as the `TriKey-release` artifact.

## Setup

1. Install the APK.
2. Enable TriKey in the framework manager.
3. Confirm that `System Framework (system)` is selected, then reboot.
4. Open TriKey and configure the key code and three gestures.

The default key code is `219` (`KEYCODE_ASSIST`). Hardware mappings vary by model. If the key is not detected, use `adb shell getevent -l` to identify the physical event and its Android key mapping.

## Supported actions

- Launch an application by package name
- Launch a custom Intent URI
- WeChat payment code
- WeChat scanner
- ColorOS full-screen OCR

## Design

The module hooks the system policy key dispatch path. It has no polling loop and does not read `/dev/input` directly. Settings are loaded only when the configured key is pressed.

## License

Apache License 2.0
