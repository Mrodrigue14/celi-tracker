// Le plugin Kotlin est declare ici pour epingler sa version en un seul endroit,
// mais applique uniquement dans les modules qui en ont besoin.
plugins {
    kotlin("jvm") version "2.4.10" apply false
    id("org.jetbrains.kotlinx.kover") version "0.9.9" apply false
    id("com.google.devtools.ksp") version "2.3.11" apply false
    id("androidx.room") version "2.8.4" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.10" apply false
    // AGP 9 embarque son propre support Kotlin (plus besoin du plugin
    // org.jetbrains.kotlin.android): voir https://kotl.in/gradle/agp-built-in-kotlin.
    id("com.android.application") version "9.4.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10" apply false
}
