plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Room is not involved here, but the Compose BOM and androidx in general are
// only published on the Google repository.
repositories {
    google()
    mavenCentral()
}

android {
    namespace = "dev.celitracker.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.celitracker.app"
        // java.time.LocalDate, used by :engine, does not exist below API 26
        // without core library desugaring. Raising the floor costs less.
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    // AGP 9 ships its own Kotlin support: this section replaces the
    // top-level `kotlin { }` block of the removed org.jetbrains.kotlin.android plugin.
    kotlin {
        compilerOptions {
            // Strict in CI (-PwarningsAsErrors=true), lenient locally.
            allWarningsAsErrors.set(
                providers.gradleProperty("warningsAsErrors").map { it.toBoolean() }.orElse(false),
            )
        }
    }
}

dependencies {
    // Repository only exposes :engine types (Profile, Transaction, ...): both
    // dependencies are needed, :data alone is not enough to compile
    // a call to repository.profile().
    implementation(project(":engine"))
    implementation(project(":data"))
    // :data no longer builds the database (androidx.room is `implementation`
    // there, so invisible here): it's :app that calls Room.databaseBuilder with
    // the Android overload, so it needs its own Room dependency.
    // Generic coordinate: resolved to the -android variant since :app is an
    // Android module.
    implementation("androidx.room:room-runtime:2.8.4")

    // compose-bom 2026.08.00+ (compose-ui 1.12.0), navigation-compose 2.10.0,
    // lifecycle-*-compose 2.11.0 and core-ktx 1.19.0 require compileSdk 37 (their
    // AAR declares it). The installed SDK and the CI runner only have android-36:
    // we stay on the latest stable version of each dependency that still
    // compiles against API 36, rather than raising compileSdk against
    // the brief's instruction.
    implementation(platform("androidx.compose:compose-bom:2025.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    // Piggy bank, receipt, deposit and withdrawal arrows: the base set only has
    // generic icons. R8 strips the ones that go unused in release builds.
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.navigation:navigation-compose:2.9.8")
    implementation("androidx.core:core-ktx:1.18.0")

    debugImplementation("androidx.compose.ui:ui-tooling")

    // No org.jetbrains.kotlin.* plugin applied on this module (AGP 9's native
    // Kotlin support): the kotlin("test") shortcut is not guaranteed, so the
    // coordinate is pointed to explicitly.
    testImplementation("org.jetbrains.kotlin:kotlin-test:2.4.10")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5:2.4.10")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    // TestRepository builds a real Room database in a JVM test (no Android
    // Context available outside instrumentation): it therefore calls the
    // JVM (contextless) overload of Room.databaseBuilder itself.
    // This module's `implementation` dependency resolves to the -android
    // variant (Context required); these explicit -jvm coordinates remain
    // necessary to expose the other overload to the test code. Verified:
    // without them, compileDebugUnitTestKotlin fails with
    // "No value passed for parameter 'context'".
    testImplementation("androidx.room:room-runtime-jvm:2.8.4")
    testImplementation("androidx.sqlite:sqlite-bundled-jvm:2.7.0")
    testImplementation("androidx.sqlite:sqlite-jvm:2.7.0")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

// No `kover {}` block here and the plugin is not applied: this module's
// Compose code is only testable with instrumentation, so it's excluded from
// verification rather than lowering the threshold for the tested modules.
