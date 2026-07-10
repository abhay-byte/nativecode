# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep Compose and Material Icons
-dontwarn androidx.compose.**
-keep class androidx.compose.** { *; }

# Keep Material Icons that are used (R8 should handle this automatically)
-keep class androidx.compose.material.icons.** { *; }

# OkHttp rules
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep class okio.** { *; }

# Keep data classes used with intents
-keepclassmembers class * implements android.os.Parcelable {
    static ** CREATOR;
}

# Keep app classes
-keep class com.ivarna.nativecode.** { *; }

# Termux-X11 CmdEntryPoint uses hidden platform APIs via reflection / framework stubs
-keep class com.termux.x11.** { *; }
-dontwarn android.app.ActivityThread
-dontwarn android.app.ContextImpl
-dontwarn android.app.IActivityManager
-dontwarn android.content.IIntentReceiver
-dontwarn android.content.IIntentReceiver$Stub
-dontwarn android.content.IIntentSender
-dontwarn android.content.pm.IPackageManager

# Ignore missing compile-time annotations
-dontwarn com.google.errorprone.annotations.**
-dontwarn com.google.crypto.tink.**
