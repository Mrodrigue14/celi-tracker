plugins {
    kotlin("jvm")
    id("com.google.devtools.ksp")
    id("androidx.room")
    id("org.jetbrains.kotlinx.kover")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Room is not published on mavenCentral for all of its dependencies.
repositories {
    google()
    mavenCentral()
}

kotlin {
    jvmToolchain(21)

    compilerOptions {
        allWarningsAsErrors.set(
            providers.gradleProperty("warningsAsErrors").map { it.toBoolean() }.orElse(false),
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
    // Without it, Room warns about the schema export and CI (-PwarningsAsErrors=true) fails.
    schemaDirectory("$projectDir/schemas")
}

tasks.test {
    useJUnitPlatform()
}

kover {
    reports {
        filters {
            // @Generated has SOURCE retention, invisible to the bytecode Kover instruments:
            // Room's generated classes are excluded by name instead.
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
