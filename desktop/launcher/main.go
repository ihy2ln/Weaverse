// Weaverse Windows launcher — finds Java and starts the desktop/web sync host.
package main

import (
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"time"
)

func main() {
	exe, err := os.Executable()
	if err != nil {
		fatal("Could not locate Weaverse.exe: %v", err)
	}
	dir := filepath.Dir(exe)
	jar := filepath.Join(dir, "Weaverse.jar")
	data := filepath.Join(dir, "data")
	if err := os.MkdirAll(data, 0o755); err != nil {
		fatal("Could not create data folder: %v", err)
	}
	if _, err := os.Stat(jar); err != nil {
		fatal("Weaverse.jar is missing next to Weaverse.exe\nExpected: %s\nDownload the desktop zip from GitHub Releases.", jar)
	}
	java, err := findJava()
	if err != nil {
		fatal("%v\n\nInstall a Java 17+ JRE (Eclipse Temurin) or download the full Windows package from:\nhttps://github.com/ihy2ln/weaverse/releases", err)
	}

	fmt.Println("Weaverse Desktop — starting web + Wi-Fi / remote sync host…")
	fmt.Println("Data folder:", data)
	cmd := exec.Command(java, "-jar", jar, "--data="+data)
	cmd.Dir = dir
	cmd.Stdout = os.Stdout
	cmd.Stderr = os.Stderr
	cmd.Stdin = os.Stdin
	if err := cmd.Run(); err != nil {
		fatal("Weaverse exited: %v", err)
	}
}

func findJava() (string, error) {
	if home := strings.TrimSpace(os.Getenv("JAVA_HOME")); home != "" {
		candidate := filepath.Join(home, "bin", "java.exe")
		if fileExists(candidate) {
			return candidate, nil
		}
		candidate = filepath.Join(home, "bin", "java")
		if fileExists(candidate) {
			return candidate, nil
		}
	}
	if path, err := exec.LookPath("java"); err == nil {
		return path, nil
	}
	var roots []string
	for _, key := range []string{"ProgramFiles", "ProgramFiles(x86)", "LOCALAPPDATA"} {
		if v := os.Getenv(key); v != "" {
			roots = append(roots, v)
		}
	}
	patterns := []string{
		`Eclipse Adoptium\jdk-*\bin\java.exe`,
		`Eclipse Adoptium\jre-*\bin\java.exe`,
		`Microsoft\jdk-*\bin\java.exe`,
		`Java\jdk-*\bin\java.exe`,
		`Java\jre-*\bin\java.exe`,
		`Amazon Corretto\*\bin\java.exe`,
	}
	for _, root := range roots {
		for _, pattern := range patterns {
			matches, _ := filepath.Glob(filepath.Join(root, pattern))
			if len(matches) > 0 {
				return matches[len(matches)-1], nil
			}
		}
	}
	return "", fmt.Errorf("Java 17+ was not found on PATH or JAVA_HOME")
}

func fileExists(path string) bool {
	info, err := os.Stat(path)
	return err == nil && !info.IsDir()
}

func fatal(format string, args ...any) {
	fmt.Fprintf(os.Stderr, "\nWeaverse\n")
	fmt.Fprintf(os.Stderr, format+"\n", args...)
	fmt.Fprintln(os.Stderr, "\nThis window will stay open for 20 seconds.")
	time.Sleep(20 * time.Second)
	os.Exit(1)
}
