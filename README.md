# GeoCraft

**GeoCraft** is a minimal Android mock-location toolkit for controlled QA, development and location-aware app testing.

## Download

- **[Download GeoCraft v1.0.0 APK](https://github.com/nsd999/GeoCraft/releases/latest/download/GeoCraft-v1.0.0.apk)**
- **[Open the landing website](https://geocraft.vercel.app)**
- **[View the GitHub repository](https://github.com/nsd999/GeoCraft)**
- **[View releases](https://github.com/nsd999/GeoCraft/releases)**

## Features

- Precise latitude/longitude target
- Quick location presets
- Foreground mock-location service
- Optional floating control
- Android-native mock-location workflow
- Open-source Android implementation
- Automated APK builds with GitHub Actions

## Setup

1. Install the APK.
2. Enable Android Developer Options.
3. Set **GeoCraft** as the device's **Mock Location App**.
4. Open GeoCraft and enter coordinates or select a preset.
5. Start the mock-location service.
6. Run your location-aware app and verify its behaviour.

## Important

Android exposes injected locations as mock locations. Applications can detect or reject mock locations. GeoCraft is intended for legitimate testing and does not attempt to bypass app anti-spoofing, integrity checks or other security controls.

## Project structure

- `android/` — Android application
- `.github/workflows/` — automated Android build and release publishing
- `index.html` — landing website

## Licence

Add the project's preferred open-source licence before public redistribution if you want to define reuse terms.
