import java.util.Properties

plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("kapt")
}

android {
    compileSdk = 34

    defaultConfig {
        applicationId = "it.vfsfitvnm.vimusic"
        minSdk = 21
        targetSdk = 34
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        versionCode = 20
        versionName = "0.5.4"
    }

    splits {
        abi {
            reset()
            isUniversalApk = true
        }
    }

    namespace = "it.vfsfitvnm.vimusic"

    // Release credentials are supplied locally or by CI; never commit them.
    val signingProperties = Properties()
    val signingPropertiesFile = rootProject.file("keystore.properties")
    if (signingPropertiesFile.isFile) {
        signingPropertiesFile.inputStream().use { signingProperties.load(it) }
    }
    fun signingValue(name: String): String? =
        System.getenv(name)?.takeIf { it.isNotBlank() }
            ?: signingProperties.getProperty(name)?.takeIf { it.isNotBlank() }

    val keystoreFile = rootProject.file(signingValue("KEYSTORE_FILE") ?: "release.keystore")
    val releaseStorePassword = signingValue("KEYSTORE_PASSWORD")
    val releaseKeyAlias = signingValue("KEY_ALIAS")
    val releaseKeyPassword = signingValue("KEY_PASSWORD")
    val releaseSigningRequested = signingPropertiesFile.isFile || keystoreFile.exists() ||
        signingValue("KEYSTORE_FILE") != null || releaseStorePassword != null ||
        releaseKeyAlias != null || releaseKeyPassword != null
    val releaseSigningConfigured = keystoreFile.isFile && releaseStorePassword != null &&
        releaseKeyAlias != null && releaseKeyPassword != null
    require(!releaseSigningRequested || releaseSigningConfigured) {
        "Incomplete WEST release signing configuration. Provide an existing KEYSTORE_FILE " +
            "and KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD through environment variables " +
            "or the ignored keystore.properties file."
    }

    signingConfigs {
        create("release") {
            if (releaseSigningConfigured) {
                storeFile = keystoreFile
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = true
            }
        }
    }

    buildTypes {
        debug {
            val preview = project.hasProperty("westPreview")
            applicationIdSuffix = if (preview) ".preview" else ".debug"
            manifestPlaceholders["appName"] = "WEST"
        }

        release {
            isMinifyEnabled = true
            isShrinkResources = true
            manifestPlaceholders["appName"] = "WEST"
            if (releaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }

        // Separate, locally signed, release-like build for device profiling.
        // It coexists with debug/preview and never uses production credentials.
        create("performance") {
            initWith(getByName("release"))
            applicationIdSuffix = ".performance"
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            manifestPlaceholders["appName"] = "WEST"
        }
    }

    sourceSets.all {
        kotlin.srcDir("src/$name/kotlin")
    }

    buildFeatures {
        compose = true
    }

    // Store DEX entries uncompressed inside the APK (the same packaging the
    // reference WEST debug builds use). This is the documented Play/bundletool
    // layout: identical bytes and classes, no compression on dex entries.
    packagingOptions {
        dex {
            useLegacyPackaging = false
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.compose.compiler.get()
    }

    kotlinOptions {
        freeCompilerArgs += "-Xcontext-receivers"
        jvmTarget = "1.8"
    }
}

kapt {
    arguments {
        arg("room.schemaLocation", "$projectDir/schemas")
    }
}

dependencies {
    implementation(projects.composePersist)
    implementation(projects.composeRouting)
    implementation(projects.composeReordering)

    implementation(libs.compose.activity)
    implementation("androidx.compose.animation:animation:${libs.versions.compose.asProvider().get()}")
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.util)
    implementation(libs.compose.ripple)
    implementation(libs.compose.shimmer)
    implementation(libs.compose.coil)

    implementation(libs.palette)

    implementation(libs.exoplayer)

    implementation(libs.room)
    kapt(libs.room.compiler)

    implementation(projects.innertube)
    implementation(projects.kugou)

    // Spotify import unit tests: JUnit for the runner and a real org.json on
    // the JVM, because the android.jar org.json is a throwing stub and the
    // importer's parsing is exactly what the tests exercise. Test scope only.
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:${libs.versions.compose.asProvider().get()}")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:core:1.6.1")
    testImplementation(testLibs.junit)
    testImplementation("org.json:json:20220320")
    // Listening statistics unit tests run the Room migration against a real
    // SQLite engine on the JVM. Test scope only: not packaged in the APK.
    testImplementation("org.xerial:sqlite-jdbc:3.42.0.0")

    coreLibraryDesugaring(libs.desugaring)
}
