# File Extension Changer - Android App

An Android application that lets you change file extensions for any file on your device. Select files, set new extensions, and save them — all from one clean interface.

## Features

- **Pick any file** from your device using the system file picker
- **Change extensions individually** — each file gets its own extension field
- **Batch rename** — set one extension for all files at once
- **Share files into the app** — use "Share" from any other app to send files directly
- **Modern Material 3 UI** with dynamic color theming (Android 12+)
- **Dark mode** support (follows system setting)

## Installation

1. Download the `FileExtensionChanger-debug.apk` file from this repository
2. Transfer it to your Android device (via USB, cloud storage, email, etc.)
3. On your device, open the APK file
4. If prompted, allow installation from unknown sources:
   - Go to **Settings > Security > Install unknown apps**
   - Enable permission for the app you're using to install (e.g., Files, Chrome)
5. Tap **Install**
6. Once installed, open **File Extension Changer** from your app drawer

> **Requirements:** Android 8.0 (API 26) or higher

## Usage Guide

### Step 1: Add Files

There are two ways to add files to the app:

**Option A — From within the app:**
1. Open the app
2. Tap the **"Add Files"** button (blue button in the bottom-right corner)
3. The system file picker opens — navigate to and select one or more files
4. Tap **Open** / **Select**

**Option B — Share from another app:**
1. In any app (file manager, gallery, browser, etc.), long-press or select a file
2. Tap **Share**
3. Choose **File Extension Changer** from the share menu

### Step 2: Set New Extensions

After adding files, each file appears as a card with:
- The original filename and file size
- The current extension shown on the left (e.g., `.jpg`)
- A text field on the right where you enter the new extension

**To change a single file's extension:**
- Type the desired extension in the text field next to the file (e.g., `png`, `pdf`, `txt`)
- Do NOT include the dot — just the extension name

**To change ALL files at once:**
- Use the **"Set extension for all files"** panel at the top
- Type the extension (e.g., `mp4`) and tap **Apply**
- This fills in the extension field for every file in the list

### Step 3: Save Files

1. Once you've set the desired extensions, tap the **check mark button** (small purple button above "Add Files")
2. The app copies each file with its new extension to the output folder
3. A status message shows how many files were saved successfully

### Step 4: Find Your Files

Converted files are saved to:

```
Internal Storage / Downloads / FileExtensionChanger /
```

You can find them using any file manager app on your device.

### Managing Files

- **Remove a file** — tap the red X button on the file card
- **Clear all** — tap the trash icon in the top-right toolbar
- **See status** — check the colored banner below the global extension panel

## Building from Source

### Prerequisites

- JDK 17+
- Android SDK (API 34)
- Android Build Tools 34.0.0

### Build Steps

```bash
export ANDROID_HOME=/path/to/your/android-sdk

cd FileExtensionChanger
./gradlew assembleDebug
```

The APK will be generated at:

```
app/build/outputs/apk/debug/app-debug.apk
```

For a release build (requires signing configuration):

```bash
./gradlew assembleRelease
```

## Project Structure

```
FileExtensionChanger/
├── app/
│   ├── build.gradle.kts          # App-level build config
│   └── src/main/
│       ├── AndroidManifest.xml    # App manifest with permissions & intent filters
│       ├── java/com/fileextchanger/app/
│       │   ├── MainActivity.kt    # Entry point, handles share intents
│       │   ├── MainViewModel.kt   # State management & business logic
│       │   ├── FileItem.kt        # Data model for files
│       │   ├── FileOperations.kt  # File I/O: reading metadata, saving with new extension
│       │   └── ui/
│       │       ├── MainScreen.kt          # Main screen composable
│       │       ├── theme/Theme.kt         # Material 3 theming
│       │       └── components/
│       │           └── FileItemCard.kt    # File card UI component
│       └── res/
│           ├── values/            # Strings, colors, themes
│           ├── drawable/          # Vector icons
│           └── mipmap-*/          # Adaptive launcher icons
├── build.gradle.kts               # Root build config
├── settings.gradle.kts            # Project settings
├── gradle.properties              # Build properties
└── gradle/wrapper/                # Gradle wrapper
```

## Technical Details

- **Language:** Kotlin
- **UI Framework:** Jetpack Compose with Material 3
- **Architecture:** MVVM (ViewModel + Compose state)
- **Min SDK:** 26 (Android 8.0)
- **Target SDK:** 34 (Android 14)
- **File storage:** Uses MediaStore API for Android 10+ (scoped storage compliant), falls back to direct file access on older versions

## License

This project is open source and available for personal and commercial use.
