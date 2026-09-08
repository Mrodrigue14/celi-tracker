plugins {
    kotlin("jvm")
    id("com.google.devtools.ksp")
    id("androidx.room")
    id("org.jetbrains.kotlinx.kover")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Room n'est pas publie sur mavenCentral pour toutes ses dependances.
repositories {
    google()
    mavenCentral()
}

kotlin {
    jvmToolchain(21)

    compilerOptions {
        allWarningsAsErrors.set(
            providers.gradleProperty("warningsAsErrors").map { it.toBoolean() }.orElse(false)
        )
    }
}

dependencies {
    implementation(project(":engine"))

    implementation("androidx.room:room-runtime:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")
    implementation("androidx.sqlite:sqlite-bundled:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")

    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
}

room {
    // Sans schemaDirectory, Room emet un avertissement sur l'export du schema
    // et la CI (-PwarningsAsErrors=true) echoue.
    schemaDirectory("$projectDir/schemas")
}

tasks.test {
    useJUnitPlatform()
}

kover {
    reports {
        filters {
            // Code genere par Room (RoomProcessor), jamais ecrit ni relu a la
            // main: exclu de la couverture, pas le seuil qui baisse.
            // @Generated est @Retention(SOURCE), invisible au bytecode que
            // Kover instrumente: on exclut par nom de classe genere.
            excludes {
                classes("dev.celitracker.data.*_Impl", "dev.celitracker.data.*_Impl\$*")
            }
        }
        verify {
            rule {
                minBound(95)
            }
        }
    }
}
