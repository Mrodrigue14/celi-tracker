// Le plugin Kotlin est declare ici pour epingler sa version en un seul endroit,
// mais applique uniquement dans les modules qui en ont besoin.
plugins {
    kotlin("jvm") version "2.2.0" apply false
    id("org.jetbrains.kotlinx.kover") version "0.9.9" apply false
}
