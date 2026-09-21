plugins {
    kotlin("jvm")
    id("org.jetbrains.kotlinx.kover")
}

repositories {
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
    // The engine stays free of Android, Room and networking.
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
