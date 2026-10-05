# HiNotes ProGuard rules.
#
# Release builds currently keep minification disabled (isMinifyEnabled = false) so the shipped
# APK matches what is tested. These rules are here so enabling it later stays a one-line change.

# Keep the ViewModel factory's reflective constructor path usable.
-keepclassmembers class com.hinotes.app.ui.AppViewModel {
    <init>(android.content.Context);
}

# Kotlin metadata and coroutines internals.
-keepattributes *Annotation*, InnerClasses
-dontwarn kotlinx.coroutines.**

# Compose ships its own consumer rules; this keeps the tooling honest about it.
-dontwarn androidx.compose.**

# The app talks to the release feed with plain HttpURLConnection and reads JSON with
# org.json, neither of which needs reflective keep rules.
