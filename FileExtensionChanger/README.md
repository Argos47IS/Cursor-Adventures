# File Extension Changer — Android-приложение

Приложение для Android, которое позволяет менять расширения файлов. Выберите файлы, укажите новые расширения — и сохраните.

> **English version below** / Английская версия ниже

---

## Возможности

- **Выбор любых файлов** через системный файловый менеджер
- **Индивидуальная смена расширения** — у каждого файла своё поле ввода
- **Массовая смена** — одно расширение для всех файлов сразу
- **Приём файлов через «Поделиться»** — отправьте файл из любого приложения
- **Современный Material 3 UI** с динамическими цветами (Android 12+)
- **Тёмная тема** — следует за настройками системы
- **Русский и английский** интерфейс (переключается автоматически)

---

## Установка

1. Скачайте файл `FileExtensionChanger-debug.apk` из этого репозитория
2. Перенесите его на устройство (USB, облако, почта, мессенджер)
3. Откройте файл APK на устройстве
4. Если появится запрос — разрешите установку из неизвестных источников:
   - **Настройки → Безопасность → Установка из неизвестных источников**
   - Включите разрешение для приложения, через которое устанавливаете (Файлы, Chrome и т.д.)
5. Нажмите **Установить**
6. Найдите **«Смена расширений»** в списке приложений

> **Требования:** Android 8.0 (API 26) или выше

---

## Руководство пользователя

### Шаг 1: Добавить файлы

**Способ А — из приложения:**
1. Откройте приложение
2. Нажмите кнопку **«Добавить файлы»**
3. В файловом менеджере выберите один или несколько файлов
4. Нажмите **«Открыть»**

**Способ Б — через «Поделиться»:**
1. В любом приложении (файловый менеджер, галерея, браузер) выберите файл
2. Нажмите **«Поделиться»**
3. Выберите **«Смена расширений»** из списка

### Шаг 2: Указать новые расширения

Каждый файл отображается карточкой:
- Слева — текущее расширение (например `.jpg`)
- Справа — поле для нового расширения

**Для одного файла:**
- Введите расширение в поле справа (например `png`, `pdf`, `txt`)
- Точку ставить НЕ нужно — только название расширения

**Для всех файлов сразу:**
- Используйте панель **«Расширение для всех файлов»** вверху экрана
- Введите расширение и нажмите **«Применить»**

### Шаг 3: Сохранить

1. Нажмите кнопку **«Сохранить»** в нижней панели
2. Подтвердите действие в диалоговом окне
3. Приложение скопирует каждый файл с новым расширением
4. Статус покажет, сколько файлов сохранено

### Шаг 4: Найти файлы

Файлы сохраняются в папку:

```
Внутреннее хранилище / Downloads / FileExtensionChanger /
```

Найдите их через любой файловый менеджер.

### Управление файлами

- **Удалить файл из списка** — красная кнопка ✕ на карточке
- **Очистить всё** — иконка корзины в верхнем правом углу
- **Ошибки** — если файл не обработался, причина отображается красным на карточке

---

## Сборка из исходников

### Необходимо

- JDK 17+
- Android SDK (API 34)
- Android Build Tools 34.0.0

### Команды

```bash
export ANDROID_HOME=/путь/к/android-sdk

cd FileExtensionChanger
./gradlew assembleDebug
```

APK будет создан в:

```
app/build/outputs/apk/debug/app-debug.apk
```

---

# English

## Features

- **Pick any file** from your device using the system file picker
- **Change extensions individually** — each file gets its own extension field
- **Batch rename** — set one extension for all files at once
- **Share files into the app** — use "Share" from any other app
- **Modern Material 3 UI** with dynamic color theming (Android 12+)
- **Dark mode** support (follows system setting)
- **Russian and English** UI (switches automatically based on device language)

## Installation

1. Download `FileExtensionChanger-debug.apk` from this repository
2. Transfer it to your Android device
3. Open the APK file on your device
4. Allow installation from unknown sources if prompted
5. Tap **Install**

> **Requirements:** Android 8.0 (API 26) or higher

## Usage Guide

### Step 1: Add Files

**Option A — From within the app:**
1. Open the app
2. Tap **"Add Files"**
3. Select one or more files in the system file picker
4. Tap **Open**

**Option B — Share from another app:**
1. In any app, select a file and tap **Share**
2. Choose **File Extension Changer**

### Step 2: Set New Extensions

Each file card shows:
- Current extension on the left (e.g. `.jpg`)
- Input field on the right for the new extension

**Single file:** type the extension next to it (e.g. `png`)

**All files at once:** use the top panel, type the extension, tap **Apply**

### Step 3: Save

1. Tap **"Save"** in the bottom bar
2. Confirm in the dialog
3. Files are copied with new extensions

### Step 4: Find Your Files

Saved to: `Internal Storage / Downloads / FileExtensionChanger /`

---

## Project Structure

```
FileExtensionChanger/
├── app/src/main/
│   ├── AndroidManifest.xml
│   ├── java/com/fileextchanger/app/
│   │   ├── MainActivity.kt          # Entry point, share intent handling
│   │   ├── MainViewModel.kt         # State management & business logic
│   │   ├── FileItem.kt              # Data model
│   │   ├── FileOperations.kt        # File I/O, MediaStore API
│   │   └── ui/
│   │       ├── MainScreen.kt        # Main screen with permissions & dialogs
│   │       ├── theme/Theme.kt       # Material 3 theming
│   │       └── components/
│   │           └── FileItemCard.kt  # File card with error states
│   └── res/
│       ├── values/strings.xml       # English strings
│       ├── values-ru/strings.xml    # Russian strings
│       ├── values/themes.xml
│       └── drawable/, mipmap-*/     # Icons
├── build.gradle.kts
├── settings.gradle.kts
└── gradle/
```

## Technical Details

| Detail | Value |
|--------|-------|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Architecture | MVVM |
| Min SDK | 26 (Android 8.0) |
| Target SDK | 34 (Android 14) |
| Storage | MediaStore API (Android 10+), direct file access (Android 8–9) |
| Localization | English, Russian |
