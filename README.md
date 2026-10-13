# PhotoPickerError

Minimal repro app for a partial photo access (`READ_MEDIA_VISUAL_USER_SELECTED`) issue on Android 16.

**Symptom:** after the app is uninstalled and reinstalled, the system photo picker shows images saved by the
previous install as already selected. Tapping **Allow / Done** records no media grant, so a MediaStore query from the app
does not return those images.

## Background

[Access media files from shared storage — App attribution of media files](https://developer.android.com/training/data-storage/shared/media#app-attribution)
documents two behaviors:

1. For apps targeting SDK 36+ on Android 16+, when the user limits access to selected media, photos owned by the app
   are pre-selected in the photo picker. Deselecting them revokes access.
2. After an uninstall and reinstall, files created by the previous install are attributed to that install,
   so the new install cannot access them without a photo permission.

In this repro, the picker treats the previous install's files as owned (pre-selected), but completing the selection
does not grant access to them.

## What the app does

- Requests `READ_MEDIA_IMAGES` + `READ_MEDIA_VISUAL_USER_SELECTED` + `ACCESS_MEDIA_LOCATION` together
- Queries `MediaStore.Images.Media.EXTERNAL_CONTENT_URI` with an empty selection, sorted by `date_added DESC`
- In its own photo grid, under partial access, shows a banner with **Select photos** (re-requests the same
  permission array, which opens the system photo selection screen) and **Settings**
- Saves test images in two ways for comparison:
    - `MediaSaver` — MediaStore insert into `Pictures/PhotoPickerError`
    - `DownloadSaver` — DownloadManager into `Pictures/PhotoPickerErrorDM`, served by an in-app 127.0.0.1 HTTP server (`LocalImageServer`)

targetSdk 36 · minSdk 28

## Screens

- **Main:** `Add photos`, `Save 5 images via MediaStore`, `Save 5 images via DownloadManager`, diagnostic panel
- **Picker:** photo grid, partial access banner (`Select photos` / `Settings`), `Reload`, diagnostic panel
- **Diagnostic panel:** granted state per permission, derived status, query counts
  (all / latest selection only / MediaStore folder / DownloadManager folder), log
- logcat: `adb logcat -s PPE`

## Steps to reproduce

1. Install, tap `Add photos` → `Allow limited access` → select 1 existing photo → Done (grid shows 1)
2. Back → `Save 5 images via MediaStore` → `Add photos` (grid shows 6 = 1 granted + 5 owned)
3. Uninstall and reinstall
   ```
   adb uninstall com.jingom.photopickererror
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```
4. `Add photos` → `Allow limited access`
   - The system photo picker shows the 5 saved images as **selected** (count 5)
5. Tap Done without changing anything
   - **Expected:** the 5 images are granted and appear in the grid
   - **Actual:** the grid shows **0**, also after `Reload`
   - logcat: `PickerDbFacade: 0 grants synced`, `MediaGrants: Successfully added 0 media_grants`

## Results (Galaxy S25+ SM-S936N · Android 16 · One UI 8.5)

| Step | System picker | Grants (MediaGrants) | App query |
|---|---|---|---|
| Before reinstall | 6 selected | 1 | 6 (1 granted + 5 owned) |
| After reinstall, first request → Done | 5 selected | added 0 | 0 |
| Deselect the 5 → reselect → Done | 5 | added 0 | 0 |
| Deselect the 5 → Done → reopen | 5 selected again | 0 deleted | 0 |
| Add 1 other photo → Done | 6 | added 1 | 1 |

## MediaStore insert vs DownloadManager

Same device, same steps (grant 1 photo → save 5 → uninstall / reinstall → `Add photos` → `Allow limited access`).

| | MediaStore insert (`MediaSaver`) | DownloadManager (`DownloadSaver`) |
|---|---|---|
| Owner right after saving | this app | this app (`is_download=1`) |
| App query right after saving | 6 (1 granted + 5 owned) | 6 (1 granted + 5 owned) |
| System picker before reinstall | 6 selected | 6 selected |
| Owner after reinstall | NULL | NULL |
| System picker after reinstall | **5 selected (wrong)** — reproduced twice | **0 selected (correct)** — reproduced twice |
| App query after reinstall → Done | 0 | 0 |

- DownloadManager-saved files are also owned by the app and visible without permission right after saving
- The wrong pre-selection after reinstall appeared only for files saved with a MediaStore insert
- Why the two differ is unknown (the picker's internal database is not readable without root). Verified on one device only
