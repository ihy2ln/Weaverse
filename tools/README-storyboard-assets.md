# Storyboard generated-asset handoff

Weaverse phase 1 intentionally does not generate images inside Android. Codex
or ImageGen produces the artwork, the host stages or pushes ordinary image
files, and Weaverse imports them through Android's document picker into its
durable app-private media storage.

## Workflow

1. Generate panel artwork with Codex/ImageGen and export PNG, JPG/JPEG, WEBP,
   BMP, or GIF files.
2. From the repository root, stage them without changing the source files:

   ```powershell
   .\tools\stage-storyboard-assets.ps1 -ImagePath 'S:\art\panel-1.png','S:\art\panel-2.jpg'
   ```

   Use `-InboxDirectory <path>` to choose another staging directory. Existing
   names are never overwritten; the script creates `name-2.png`, `name-3.png`,
   and so on.
3. Either copy the printed staging paths to Android manually, or ask the script
   to push them into a unique Downloads subfolder:

   ```powershell
   .\tools\stage-storyboard-assets.ps1 `
     -ImagePath 'S:\art\panel-1.png','S:\art\panel-2.jpg' `
     -PushToDevice `
     -AdbPath 'S:\Android\platform-tools\adb.exe' `
     -DeviceSerial 'emulator-5554'
   ```

4. In Weaverse, open **Storyboard**, choose a layout, and tap the desired empty
   panel slot. Select **Import generated panel**, choose one or more staged
   images, then drag, resize, stack, or adjust them normally.

If an occupied panel is selected, Weaverse asks whether to place the import in
the next free slot or explicitly replace the selected artwork. Multiple files
keep picker order. When the active page is full, overflow continues on a new
page rather than overwriting or discarding an image.

## Limitation

A Windows process cannot write directly into another Android app's private
storage without a dedicated Android service or privileged debugging access.
This phase uses the safe supported boundary: stage/push into user-visible
Android storage, then let the user confirm files in the system picker.
Weaverse immediately copies every chosen file into app-private storage, so it
does not depend on the external URI afterward.
