plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// The app's manga sources (MangaDex, Comix, Atsumaru and the public HTML catalogs), shared by
// the APK and Weaverse Desktop's web version so both always run the same code.
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(libs.kotlinx.serialization.json)
    api(libs.kotlinx.coroutines.core)
    api(libs.okhttp)
    api(libs.ktor.client.core)
    api("org.jsoup:jsoup:1.23.1")
    api("com.google.dagger:dagger:2.52")
    ksp("com.google.dagger:dagger-compiler:2.52")
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.jupiter.engine)
}

tasks.test {
    useJUnitPlatform()
}
