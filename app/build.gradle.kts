import org.gradle.api.tasks.bundling.AbstractArchiveTask

// F-Droid reproducible builds: disable baseline profiles using Groovy script
apply(from = "fix-baseline-profiles.gradle")

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    kotlin("plugin.serialization") version "2.0.20"
}

android {
    namespace = "com.ivarna.nativecode"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.ivarna.nativecode"
        minSdk = 26
        targetSdk = 36
        versionCode = 10
        versionName = "1.7.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        ndk {
            abiFilters += setOf("arm64-v8a")
        }
    }

    androidResources {
        // Disable PNG crunching for reproducible builds
        @Suppress("UnstableApiUsage")
        ignoreAssetsPattern = "!.svn:!.git:.*:!CVS:!thumbs.db:!picasa.ini:!*.scc:*~"
        // AAPT automatically decompresses .gz files; noCompress keeps the
        // original bytes intact so GzipCompressorInputStream can read them.
        @Suppress("UnstableApiUsage")
        noCompress += listOf("gz", "tar", "xz", "zip")
    }

    // Disable dependency metadata block for F-Droid
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    buildTypes {
        debug {
            // Keep debuggable for local dev; device shows a banner on debug APKs.
            isDebuggable = true
            packaging {
                resources.excludes.add("META-INF/**")
            }
        }
        // Install this for production-like devices (no "debuggable app" banner).
        // Minify off by default here: R8 OOMs on this machine for full app+x11 graph.
        // F-Droid/CI can re-enable minify with more heap if needed.
        release {
            signingConfig = signingConfigs.getByName("debug")
            isDebuggable = false
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Disable baseline profiles for F-Droid reproducible builds
            packaging {
                resources.excludes.add("META-INF/**")
                resources.excludes.add("**.prof")
                resources.excludes.add("assets/dexopt/baseline.prof")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            // Keep native libs uncompressed so the OS can mmap/execute them.
            useLegacyPackaging = true
            pickFirsts += listOf("libproot.so", "libproot_loader.so", "libtalloc.so", "libandroid-shmem.so")
        }
    }
}

// Reproducible builds configuration for F-Droid
tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    
    // Glassmorphism FX
    implementation(libs.haze)
    implementation(libs.haze.materials)
    
    // Permissions
    implementation(libs.accompanist.permissions)
    
    // Networking
    implementation(libs.okhttp)

    // Archive extraction for rootfs
    implementation(libs.commons.compress)

    // Encrypted storage for API keys
    implementation(libs.androidx.security.crypto)
    
    // JSON Serialization
    implementation(libs.kotlinx.serialization.json)

    // Embedded Termux runtime (terminal emulator, shell sessions)
    implementation(project(":modules:termux-app:termux-shared"))
    implementation(project(":modules:termux-app:terminal-emulator"))
    implementation(project(":modules:termux-app:terminal-view"))

    // Termux X11 in-app display (LorieView, CmdEntryPoint)
    implementation(project(":modules:termux-x11:app"))


    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
