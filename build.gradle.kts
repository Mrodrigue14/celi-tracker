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
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
}

// Le style vit dans `.editorconfig`, lu par ktlint comme par l'IDE. Applique a
// tous les modules: le code Kotlin est reparti dans :engine, :data et :app.
allprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    // ktlint-cli est resolu par projet, y compris a la racine, qui n'a sinon
    // aucun depot declare.
    repositories {
        mavenCentral()
    }

    configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        version.set("1.8.0")
        // Le code genere par KSP (les implementations Room) n'est pas ecrit a
        // la main: le styler ne dirait rien sur le code du depot.
        filter {
            exclude { it.file.invariantSeparatorsPath.contains("/build/") }
        }
    }
}
