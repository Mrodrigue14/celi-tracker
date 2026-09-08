pluginManagement {
    repositories {
        // Le plugin Gradle androidx.room n'est pas publie sur le portail Gradle.
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "celi-tracker"

include(":engine")
include(":data")
include(":app")
