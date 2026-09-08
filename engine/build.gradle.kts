plugins {
    kotlin("jvm")
    id("org.jetbrains.kotlinx.kover")
}

// Gradle n'a aucun depot par defaut: sans ce bloc, kotlin-stdlib et
// kotlin-test ne peuvent pas etre resolus.
repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(21)

    compilerOptions {
        // Strict en CI (-PwarningsAsErrors=true), souple en local: un
        // avertissement ne doit pas bloquer l'iteration, mais ne doit pas non
        // plus s'accumuler dans la branche stable.
        allWarningsAsErrors.set(
            providers.gradleProperty("warningsAsErrors").map { it.toBoolean() }.orElse(false)
        )
    }
}

dependencies {
    // Seule dependance du moteur. Il ne connait ni Android, ni Room, ni reseau.
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
