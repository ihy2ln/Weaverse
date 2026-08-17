# Weaverse — Windows · Web · Sync

Copy this whole folder to:

```
S:\AI\Novel\Weaverse
```

On Windows, the fastest way is:

```
Right-click INSTALL-TO-S.ps1 → Run with PowerShell
```

Then use the launchers below.

## What’s here

| Item | Purpose |
|------|---------|
| `START-HERE.txt` | Short start card for this folder |
| `Weaverse.exe` | Windows app — starts the desktop / web / sync host |
| `START-DESKTOP.bat` | Same as the EXE (falls back to the JAR) |
| `START-WEB.bat` | Starts the host and opens the web UI |
| `data/` | Local library + sync packages (created on first run) |
| `import/` | Drop a Novelcrafter ZIP here (`novel.md` or `novel.docx` + codex folders) |
| `SYNC.md` | Wi‑Fi and remote sync steps |
| `INSTALL-TO-S.ps1` | Copies / downloads this package onto `S:\AI\Novel\Weaverse` |

`Weaverse.exe` needs **Java 17+** unless you installed the full Windows package from GitHub Releases (bundled runtime).

## Quick start

1. Copy this folder to `S:\AI\Novel\Weaverse` (or run `INSTALL-TO-S.ps1`)
2. Double‑click `Weaverse.exe`
3. Note the **Pair PIN** in the console / web header
4. The **web page** shows the one sync password
5. On Android: **Settings → Open web sync** → enter that password → **Push to web** or **Pull from web**

## Platforms

- **Android APK** — full editor (Novel / Roleplay / Notes)
- **Windows EXE** — sync host + web companion
- **Web** — browser UI served by the desktop (or Android) host

Remote access: expose port `8787` with Tailscale, Cloudflare Tunnel, or similar, then use that URL as the peer host on Android.
