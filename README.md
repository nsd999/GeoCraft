# GeoCraft

GeoCraft is a minimal Android mock-location toolkit for development, QA, demos, and location-aware UI testing.

## What it does

- Lets you enter latitude and longitude.
- Registers GeoCraft as a standard Android test-location provider.
- Applies a mock GPS location when Android allows the app to act as the selected mock-location provider.
- Includes a shortcut to Android Developer Options.
- Remembers the last test coordinate locally.

## What it intentionally does not do

GeoCraft does not attempt to hide mock-location state, bypass anti-spoofing systems, forge telemetry, defeat security controls, or make a mock location indistinguishable from a genuine hardware/GNSS location.

## Build

The GitHub Actions workflow builds the debug APK on pushes to main and on version tags.

Local Android Studio:

```bash
cd android
gradle assembleDebug
```

## Web companion

The root index.html is a small Vercel-ready landing page.

## Branding

GeoCraft uses the included neon eye/location mark in public/geoforge.svg and the matching Android vector launcher artwork.
