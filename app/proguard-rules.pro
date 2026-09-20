# ---- Romba Mukiyam release rules ----

# Strip all android.util.Log calls from release builds (defence in depth: SafeLog already redacts).
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static int wtf(...);
}

# SQLCipher for Android (JNI)
-keep class net.zetetic.database.** { *; }
-keep class net.zetetic.database.sqlcipher.** { *; }

# argon2kt (JNI)
-keep class com.lambdapioneer.argon2kt.** { *; }

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.rombamukiyam.vault.**$$serializer { *; }
-keepclassmembers class com.rombamukiyam.vault.** { *** Companion; }
-keepclasseswithmembers class com.rombamukiyam.vault.** { kotlinx.serialization.KSerializer serializer(...); }

# Keep line numbers for safe diagnostics (stack traces are never shown to users, only exception class names).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
