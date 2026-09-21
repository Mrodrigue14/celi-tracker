pluginManagement {
    repositories {
        // The androidx.room Gradle plugin is not published on the Gradle portal.
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "celi-tracker"

include(":engine")
include(":data")
include(":app")
