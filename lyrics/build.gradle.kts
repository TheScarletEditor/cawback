plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// The commit this build came from (e.g. 0.1.0+712d74c, "-dirty" with uncommitted changes), so versions.ps1 can spot drift.
val gitRev: String = runCatching {
    fun git(vararg a: String) = ProcessBuilder("git", *a).directory(rootDir).start().inputStream.bufferedReader().readText().trim()
    val h = git("rev-parse", "--short", "HEAD")
    if (h.isEmpty()) "nogit" else h + if (git("status", "--porcelain").isNotEmpty()) "-dirty" else ""
}.getOrDefault("nogit")

// The Library's schema history, kept in the repo so every migration can be checked.
ksp { arg("room.schemaLocation", "$projectDir/schemas") }

android {
    namespace = "com.thescarletcoder.lyrics"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.thescarletcoder.lyrics"
        minSdk = 34
        targetSdk = 36
        versionCode = 5
        versionName = "0.5.0"
        buildConfigField("boolean", "TUNING", "false")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // SQLCipher ships a native library per CPU type. No Android 14 phone is 32-bit x86; x86_64 stays for emulators.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
    }

    buildTypes {
        debug {
            versionNameSuffix = "+$gitRev"
            buildConfigField("boolean", "TUNING", "true")
        }
        release {
            // Public releases only: signed with the release key by release.ps1.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        // The test copy ("Lyrics Lab"): its own app id, so it installs next to the regular Lyrics with its own database,
        // settings and key, and can never see or change its data. Stripped to nothing but what tests need
        // (src/labtest/AndroidManifest.xml). On-device tests run against it, on a real device: labtest.ps1.
        create("labtest") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".labtest"
            versionNameSuffix = "-labtest+$gitRev"
            matchingFallbacks += listOf("debug")
        }
        // The owner's own devices: release-optimized (R8, not debuggable) but signed with this laptop's debug key,
        // so it installs over debug installs and keeps saved lyrics. Never install `release` on those devices.
        create("personal") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            versionNameSuffix = "+$gitRev"
            buildConfigField("boolean", "TUNING", "true")
        }
        // The smoke copy ("Lyrics Smoke"): release-optimized exactly like the public build, but with its own app id
        // and the debug key, so it installs next to the installed Lyrics without touching it. Driven over adb by smoke.ps1
        // during /finalize, then uninstalled.
        create("smoke") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            applicationIdSuffix = ".smoke"
            versionNameSuffix = "-smoke+$gitRev"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    // On-device tests run against the test copy, never against the real app's id.
    testBuildType = "labtest"
    // Bouncy Castle and jspecify both carry an OSGi manifest no one reads on Android.
    packaging {
        resources { excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF" }
        // SQLCipher's native libraries zipped in the APK (owner, 2026-10-11): about 9.5 MB to download instead of
        // 17.4; Android unzips the one it needs at install.
        jniLibs { useLegacyPackaging = true }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    // Dominant colour of the album art (from the media session's bitmap; art is never fetched).
    implementation("androidx.palette:palette-ktx:1.0.0")
    // Installs Compose's bundled baseline profiles for sideloaded builds (no Play cloud profiles).
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    // Flex mode on flip phones: where the fold is when it's half-open (no permission, no network).
    implementation("androidx.window:window:1.3.0")
    // The Library (0.4): Room on SQLCipher, key wrapped by Android Keystore, same as Scarlet Command.
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.sqlcipher.android)
    implementation(libs.androidx.sqlite)
    // Library backups: Argon2id turns the backup passphrase into a key (the same as Scarlet Command's backups).
    implementation(libs.bcprov)
    testImplementation("junit:junit:4.13.2")
    // The real org.json for JVM tests (Android's copy is a stub there).
    testImplementation("org.json:json:20240303")
    // The Library on a real Android (SQLCipher, Keystore): run on the emulator, never on personal devices.
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
