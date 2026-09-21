plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// The Compose BOM and androidx in general are only published on the Google repository.
repositories {
    google()
    mavenCentral()
}

android {
    namespace = "dev.celitracker.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.celitracker.app"
        // java.time.LocalDate (used by :engine) needs API 26 without core library desugaring.
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

    // AGP 9 built-in Kotlin: replaces the top-level `kotlin { }` block.
    kotlin {
        compilerOptions {
            allWarningsAsErrors.set(
                providers.gradleProperty("warningsAsErrors").map { it.toBoolean() }.orElse(false),
            )
        }
    }
}

dependencies {
    // Repository exposes :engine types (Profile, Transaction, ...): :data alone is not enough to compile.
    implementation(project(":engine"))
    implementation(project(":data"))
    // :app calls Room.databaseBuilder (Android overload) and :data keeps Room as `implementation`.
    // The generic coordinate resolves to the -android variant.
    implementation("androidx.room:room-runtime:2.8.4")

    // compose-bom 2026.08.00+, navigation-compose 2.10.0, lifecycle-*-compose 2.11.0 and
    // core-ktx 1.19.0 require compileSdk 37, but the installed SDK and the CI runner only have android-36.
    implementation(platform("androidx.compose:compose-bom:2025.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    // The base set lacks the piggy bank, receipt and deposit/withdrawal icons. R8 strips the unused ones.
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.navigation:navigation-compose:2.9.8")
    implementation("androidx.core:core-ktx:1.18.0")

    debugImplementation("androidx.compose.ui:ui-tooling")

    // No org.jetbrains.kotlin.* plugin on this module (AGP 9 built-in Kotlin): kotlin("test") is not guaranteed.
    testImplementation("org.jetbrains.kotlin:kotlin-test:2.4.10")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5:2.4.10")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    // TestRepository calls the contextless JVM overload of Room.databaseBuilder. `implementation` resolves
    // to the -android variant, so without these -jvm coordinates compileDebugUnitTestKotlin fails with
    // "No value passed for parameter 'context'".
    testImplementation("androidx.room:room-runtime-jvm:2.8.4")
    testImplementation("androidx.sqlite:sqlite-bundled-jvm:2.7.0")
    testImplementation("androidx.sqlite:sqlite-jvm:2.7.0")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

// No Kover here: the Compose code is only testable with instrumentation, so it is excluded from verification.
