plugins {
    kotlin("jvm")
}

// Gradle n'a aucun depot par defaut: sans ce bloc, kotlin-stdlib et
// kotlin-test ne peuvent pas etre resolus.
repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    // Seule dependance du moteur. Il ne connait ni Android, ni Room, ni reseau.
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
