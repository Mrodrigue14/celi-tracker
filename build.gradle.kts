// Le plugin Kotlin est declare ici pour epingler sa version en un seul endroit,
// mais applique uniquement dans les modules qui en ont besoin.
plugins {
    kotlin("jvm") version "2.4.10" apply false
    id("org.jetbrains.kotlinx.kover") version "0.9.9" apply false
    id("com.google.devtools.ksp") version "2.3.11" apply false
    id("androidx.room") version "2.8.4" apply false
}
