package com.ihy2ln.weaverse.shared

actual class Platform actual constructor() {
    actual val name: String = "JVM ${System.getProperty("java.version")}"
}
