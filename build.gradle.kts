plugins {
    kotlin("jvm") version "2.4.20" apply false
    id("org.jetbrains.kotlinx.kover") version "0.9.9" apply false
    id("com.google.devtools.ksp") version "2.3.12" apply false
    id("androidx.room") version "2.8.4" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.20" apply false
    // AGP 9 has built-in Kotlin support: no org.jetbrains.kotlin.android plugin.
    id("com.android.application") version "9.4.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20" apply false
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
}

allprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    // ktlint-cli is resolved per project, and the root project declares no repository.
    repositories {
        mavenCentral()
    }

    configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        version.set("1.8.0")
        filter {
            exclude { it.file.invariantSeparatorsPath.contains("/build/") }
        }
    }
}
