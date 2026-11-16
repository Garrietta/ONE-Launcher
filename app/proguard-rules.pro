# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# kotlinx-serialization (v1.8.7, R8): keep the generated serializers
# and every @Serializable data class — R8 must not strip or rename
# them, or the catalog / pairing / remote-listing caches fail to
# decode at runtime.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers @kotlinx.serialization.Serializable class dev.one.launcher.** {
    *** Companion;
}
-keep class dev.one.launcher.**$$serializer { *; }
-keepclassmembers class dev.one.launcher.** {
    *** serializer(...);
}

# Compose: keep the entry points R8 cannot see through reflection.
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**
