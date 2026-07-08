// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
}

// Force lifecycle max version across all subprojects to avoid compileSdk 37 requirement.
// lifecycle 2.9+ pulls lifecycle-runtime-compose-android which requires compileSdk >= 37.
subprojects {
    configurations.all {
        resolutionStrategy.eachDependency {
            if (requested.group == "androidx.lifecycle") {
                val v = requested.version ?: ""
                // Only cap if it would exceed 2.8.x
                if (v.isNotEmpty() && v > "2.8.99") {
                    useVersion("2.8.7")
                    because("compileSdk 36 max — lifecycle 2.9+ requires SDK 37")
                }
            }
        }
    }
}
