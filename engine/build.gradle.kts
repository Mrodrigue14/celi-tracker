plugins {
    kotlin("jvm")
    id("org.jetbrains.kotlinx.kover")
}

// Gradle has no default repository: without this block, kotlin-stdlib and
// kotlin-test cannot be resolved.
repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(21)

    compilerOptions {
        // Strict in CI (-PwarningsAsErrors=true), lenient locally: a
        // warning should not block iteration, but should not pile up in the
        // stable branch either.
        allWarningsAsErrors.set(
            providers.gradleProperty("warningsAsErrors").map { it.toBoolean() }.orElse(false),
        )
    }
}

dependencies {
    // Only dependency of the engine. It knows nothing about Android, Room, or networking.
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

kover {
    reports {
        verify {
            rule {
                minBound(95)
            }
        }
    }
}
