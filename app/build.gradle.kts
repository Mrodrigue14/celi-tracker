plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Room n'est pas en jeu ici, mais le BOM Compose et androidx en general ne
// sont publies que sur le depot Google.
repositories {
    google()
    mavenCentral()
}

android {
    namespace = "dev.celitracker.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.celitracker.app"
        // java.time.LocalDate, utilise par :engine, n'existe pas sous l'API 26
        // sans core library desugaring. Monter le plancher coute moins cher.
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

    // AGP 9 embarque son propre support Kotlin: cette section remplace le bloc
    // top-level `kotlin { }` du plugin org.jetbrains.kotlin.android, retire.
    kotlin {
        compilerOptions {
            // Strict en CI (-PwarningsAsErrors=true), souple en local.
            allWarningsAsErrors.set(
                providers.gradleProperty("warningsAsErrors").map { it.toBoolean() }.orElse(false)
            )
        }
    }
}

dependencies {
    // Depot n'expose que des types de :engine (Profil, Transaction, ...): les
    // deux dependances sont necessaires, :data seule ne suffit pas a compiler
    // un appel a depot.profil().
    implementation(project(":engine"))
    implementation(project(":data"))
    // :data ne construit plus la base (androidx.room y est `implementation`,
    // donc invisible ici) : c'est :app qui appelle Room.databaseBuilder avec
    // la surcharge Android, il lui faut donc sa propre dependance Room.
    // Coordonnee generique : resolue en variante -android car :app est un
    // module Android.
    implementation("androidx.room:room-runtime:2.8.4")

    // compose-bom 2026.08.00+ (compose-ui 1.12.0), navigation-compose 2.10.0,
    // lifecycle-*-compose 2.11.0 et core-ktx 1.19.0 exigent compileSdk 37 (leur
    // AAR le declare). Le SDK installe et le runner CI n'ont que android-36:
    // on reste sur la derniere version stable de chaque dependance qui
    // compile encore contre l'API 36, plutot que de monter compileSdk contre
    // la consigne du brief.
    implementation(platform("androidx.compose:compose-bom:2025.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.navigation:navigation-compose:2.9.8")
    implementation("androidx.core:core-ktx:1.18.0")

    debugImplementation("androidx.compose.ui:ui-tooling")

    // Pas de plugin org.jetbrains.kotlin.* applique sur ce module (support
    // Kotlin natif d'AGP 9): le raccourci kotlin("test") n'est pas garanti, on
    // pointe la coordonnee explicitement.
    testImplementation("org.jetbrains.kotlin:kotlin-test:2.4.10")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5:2.4.10")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    // DepotDeTest construit une vraie base Room dans un test JVM (pas de
    // Context Android disponible hors instrumentation) : il appelle donc
    // lui-meme la surcharge JVM (contextless) de Room.databaseBuilder.
    // La dependance `implementation` de ce module resout la variante
    // -android (Context requis) ; ces coordonnees -jvm explicites restent
    // necessaires pour exposer l'autre surcharge au code de test. Verifie :
    // sans elles, compileDebugUnitTestKotlin echoue avec
    // "No value passed for parameter 'context'".
    testImplementation("androidx.room:room-runtime-jvm:2.8.4")
    testImplementation("androidx.sqlite:sqlite-bundled-jvm:2.7.0")
    testImplementation("androidx.sqlite:sqlite-jvm:2.7.0")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

// Pas de bloc `kover {}` ici et le plugin n'est pas applique: le Compose de ce
// module n'est testable qu'avec instrumentation, donc exclu de la
// verification plutot que d'abaisser le seuil des modules testes.
