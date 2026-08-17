# Weaverse sync — through the web version

There are **three versions**:

| Version | What it is |
|---------|------------|
| **Web** | The sync hub. Issues the **one password**. Open this page first. |
| **Desktop EXE** | Windows app. Opens the web link. |
| **Android APK** | Phone app. Opens the same web link, then Push / Pull. |

## How sync works

1. Start the **desktop EXE** (`Weaverse.exe`) — it opens the web hub.
2. The **web page** shows the single sync password.
3. On the phone: **Settings → Sync through the web version → Open web sync**.
4. Type the password from the web page into **Password from the web page**.
5. Leave **Auto-sync on**, or tap **Push to web** / **Pull from web**.
6. After a Pull the Android app reloads so the library is live — no manual restart.

The password is only issued on the web page. Do not use a different PIN on each device.

## Same Wi‑Fi

- Desktop web link: `http://<pc-ip>:8787`
- Put that link in the Android **Web link** field, then **Open web sync**.

## Remote

Keep the desktop (or phone web hub) running. Tunnel port **8787**. Use that `https://…` URL as the web link. Same password from the page.

## Phone as the hub

If the PC is off: Android **Start web hub** → **Open web sync**. The password appears on that page. On the desktop, open the same link in a browser.

## Package format

ZIP: `weaverse.db` + `media/**` + `manifest.json`. Last successful push/pull wins for the whole library.
