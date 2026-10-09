plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// The commit this build came from (e.g. 0.1.0+712d74c, "-dirty" with uncommitted changes), so versions.ps1 can spot drift.
val gitRev: String = runCatching {
    fun git(vararg a: String) = ProcessBuilder("git", *a).directory(rootDir).start().inputStream.bufferedReader().readText().trim()
    val h = git("rev-parse", "--short", "HEAD")
    if (h.isEmpty()) "nogit" else h + if (git("status", "--porcelain").isNotEmpty()) "-dirty" else ""
}.getOrDefault("nogit")

android {
    namespace = "com.thescarletcoder.lyrics"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.thescarletcoder.lyrics"
        minSdk = 34
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        debug { versionNameSuffix = "+$gitRev" }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    testImplementation("junit:junit:4.13.2")
}
