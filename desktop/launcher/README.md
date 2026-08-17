# Weaverse Windows launcher

Tiny Go helper that becomes `Weaverse.exe`. It finds Java 17+ and starts `Weaverse.jar` with `--data` next to the EXE.

Rebuild:

```bash
GOOS=windows GOARCH=amd64 CGO_ENABLED=0 go build -ldflags="-s -w" -o ../../Weaverse/Weaverse.exe .
```
