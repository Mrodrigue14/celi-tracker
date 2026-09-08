// Le plugin Kotlin est declare ici pour epingler sa version en un seul endroit,
// mais applique uniquement dans les modules qui en ont besoin.
plugins {
    kotlin("jvm") version "2.4.10" apply false
    id("org.jetbrains.kotlinx.kover") version "0.9.9" apply false
}
