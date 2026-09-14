plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
}

subprojects {
    configurations.configureEach {
        resolutionStrategy {
            // kotlinx-coroutines 1.10.2 requests kotlin-stdlib 2.1.0; keep the
            // compiler/plugin line at 2.0.21. Do not force jdk7/jdk8 artifacts —
            // Kotlin 2.0 folded those into stdlib and 2.0.21 variants are absent.
            force("org.jetbrains.kotlin:kotlin-stdlib:2.0.21")
            force("androidx.compose.ui:ui:1.7.6")
            force("androidx.compose.runtime:runtime:1.7.6")
            force("androidx.collection:collection:1.4.4")
            force("org.jetbrains.kotlinx:kotlinx-coroutines-slf4j:1.9.0")
        }
    }
}
