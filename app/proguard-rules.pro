# Disable obfuscation (we use Proguard exclusively for optimization)
-dontobfuscate

# Keep `Companion` object fields of serializable classes.
# This avoids serializer lookup through `getDeclaredClasses` as done for named companion objects.
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}

# Keep `serializer()` on companion objects (both default and named) of serializable classes.
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep `INSTANCE.serializer()` of serializable objects.
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# @Serializable and @Polymorphic are used at runtime for polymorphic serialization.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault

# SQLCipher loads native-backed database classes reflectively through androidx.sqlite.
-keep class net.zetetic.database.** { *; }

# ROADMAP §6 N7.4 — SQLCipher pulls in Google Tink which references
# errorprone + javax.annotation classes that aren't on the Android classpath.
# These are compile-time annotations only; R8 warns about missing classes but
# the runtime is unaffected. Suppress so release builds succeed without
# adding a runtime dependency on annotations that don't ship to Android.
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn javax.annotation.concurrent.**
