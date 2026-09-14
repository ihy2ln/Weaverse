import org.gradle.internal.os.OperatingSystem

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("com.ihy2ln.weaverse.desktop.MainKt")
    applicationName = "Weaverse"
}

dependencies {
    implementation(project(":sync-core"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.sqlite.jdbc)
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "com.ihy2ln.weaverse.desktop.MainKt"
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
}

val packageDir = layout.buildDirectory.dir("package")

tasks.register<Copy>("stageDesktopDist") {
    dependsOn(tasks.jar)
    into(packageDir)
    from(tasks.jar) {
        rename { "Weaverse.jar" }
    }
    from("scripts") {
        include("*.bat", "*.sh", "*.ps1")
    }
    from(rootProject.file("Weaverse")) {
        include(
            "README.md",
            "SYNC.md",
            "COPY-HERE.txt",
            "INSTALL-TO-S.ps1",
            "START-HERE.txt",
            "Weaverse.exe",
            "import/**",
        )
    }
    doLast {
        packageDir.get().asFile.resolve("data").mkdirs()
    }
}

tasks.register<Exec>("packageAppImage") {
    group = "distribution"
    description = "Create a native app image with jpackage (current OS)."
    dependsOn("stageDesktopDist")
    val os = OperatingSystem.current()
    val out = layout.buildDirectory.dir("jpackage").get().asFile
    // Portable folder with Weaverse.exe (Windows app-image), not an installer.
    val type = when {
        os.isWindows -> "app-image"
        os.isMacOsX -> "dmg"
        else -> "app-image"
    }
    outputs.dir(out)
    doFirst { out.mkdirs() }
    commandLine(
        "jpackage",
        "--type", type,
        "--name", "Weaverse",
        "--input", packageDir.get().asFile.absolutePath,
        "--main-jar", "Weaverse.jar",
        "--main-class", "com.ihy2ln.weaverse.desktop.MainKt",
        "--dest", out.absolutePath,
        "--app-version", "1.2.0",
        "--description", "Weaverse Desktop — write companion with Wi‑Fi / remote sync",
        "--vendor", "Weaverse",
    )
}

tasks.register<Zip>("packageDesktopZip") {
    group = "distribution"
    description = "Zip desktop fat JAR + launchers for Windows/Linux."
    dependsOn("stageDesktopDist")
    archiveFileName.set("Weaverse-Desktop-v1.2.5-beta.zip")
    destinationDirectory.set(rootProject.layout.projectDirectory.dir("releases/desktop"))
    from(packageDir)
}
