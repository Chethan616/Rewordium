# Flutter-specific rules.
# These rules are required by the Flutter framework.
-keep class io.flutter.app.** { *; }
-keep class io.flutter.plugin.**  { *; }
-keep class io.flutter.util.**  { *; }
-keep class io.flutter.view.**  { *; }
-keep class io.flutter.embedding.**  { *; }
-keep class io.flutter.plugins.** { *; }
-keep class io.flutter.plugins.GeneratedPluginRegistrant { *; }
-dontwarn io.flutter.embedding.**

# ==============================================================================
# KEEP SYSTEM COMPONENTS
# ==============================================================================

# 1. Keep the Main Activity
-keep public class com.noxquill.rewordium.MainActivity {
    public <init>();
}

# 2. Keep the Accessibility Service
-keep public class com.noxquill.rewordium.service.MyAccessibilityService {
    public <init>();
    public void onServiceConnected();
    public void onAccessibilityEvent(android.view.accessibility.AccessibilityEvent);
    public void onInterrupt();
}
-keep public class * extends android.accessibilityservice.AccessibilityService {
    public <init>();
}

# 3. Keep the Keyboard Service (Input Method) - Now using reboard_keyboard module
# The RewordiumAIKeyboardService has been removed, Reboard is the primary keyboard

# ==============================================================================
# CRITICAL FIX FOR NETWORKING IN RELEASE MODE
# ==============================================================================

# Keep ALL classes in the service package (includes API models and interfaces)
-keep class com.noxquill.rewordium.service.** { *; }
-keep interface com.noxquill.rewordium.service.** { *; }

# Specifically keep your API data classes to prevent GSON issues
-keep class com.noxquill.rewordium.service.GroqRequest { *; }
-keep class com.noxquill.rewordium.service.GroqResponse { *; }
-keep class com.noxquill.rewordium.service.Message { *; }
-keep class com.noxquill.rewordium.service.Choice { *; }
-keep class com.noxquill.rewordium.service.ApiService { *; }
-keep class com.noxquill.rewordium.service.RetrofitClient { *; }

# Keep all fields in your data classes
-keepclassmembers class com.noxquill.rewordium.service.** {
    <fields>;
    <methods>;
}

# ==============================================================================
# GSON SERIALIZATION RULES
# ==============================================================================

# Keep GSON specific classes to prevent TypeToken errors
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keep class com.google.gson.Gson { *; }
-keep class com.google.gson.JsonElement { *; }
-keep class com.google.gson.JsonObject { *; }
-keep class com.google.gson.JsonArray { *; }
-keep class com.google.gson.JsonPrimitive { *; }
-keep class com.google.gson.JsonNull { *; }
-keep class com.google.gson.internal.** { *; }

# Keep attributes required for serialization
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# Keep classes with @SerializedName annotations
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep,allowobfuscation @com.google.gson.annotations.SerializedName class *

# Keep all fields that might be serialized
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
    @com.google.gson.annotations.Expose <fields>;
}

# ==============================================================================
# RETROFIT AND OKHTTP RULES
# ==============================================================================

# Keep Retrofit annotations and methods
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
-keepclassmembers,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# Keep Kotlin Coroutines suspend functions used by Retrofit
-keepclassmembers,allowobfuscation interface * {
    suspend <methods>;
}
-keepclassmembers,allowobfuscation class * {
    suspend <methods>;
}
-keep,allowobfuscation class kotlin.coroutines.Continuation

# OkHttp and Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# ==============================================================================
# KOTLIN SPECIFIC RULES
# ==============================================================================

# Keep Kotlin metadata
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeInvisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations
-keepattributes RuntimeInvisibleParameterAnnotations

# Keep Kotlin coroutines
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# Keep data classes (they have synthetic methods that ProGuard might remove)
-keep class * {
    public <init>(...);
}

# ==============================================================================
# ADDITIONAL SAFETY RULES
# ==============================================================================

# Keep all public classes and methods in your main package
-keep public class com.noxquill.rewordium.** {
    public *;
}

# Don't obfuscate stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Remove logging in release
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
    public static int e(...);
}

# ==============================================================================
# GOOGLE PLAY CORE RULES (Fixes R8 missing class errors)
# ==============================================================================

# Keep Google Play Core classes
-keep class com.google.android.play.core.** { *; }
-keep interface com.google.android.play.core.** { *; }

# Keep Flutter Play Store Split Application
-keep class io.flutter.app.FlutterPlayStoreSplitApplication { *; }

# Keep SplitCompatApplication (the missing class from the error)
-keep class com.google.android.play.core.splitcompat.SplitCompatApplication { *; }

# Additional Play Core rules
-dontwarn com.google.android.play.core.**
-keep class com.google.android.play.core.splitcompat.** { *; }
-keep class com.google.android.play.core.splitinstall.** { *; }

# ==============================================================================
# KOTLINX SERIALIZATION RULES (For ReBoard Navigation)
# ==============================================================================

# Keep `Companion` object fields of serializable classes.
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

# Keep all serializers
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep ReBoard Routes serializers (correct package name)
-keep class com.noxquill.rewordium.keyboard.app.Routes { *; }
-keep class com.noxquill.rewordium.keyboard.app.Routes$* { *; }
-keep class com.noxquill.rewordium.keyboard.app.Routes$**$* { *; }
-keepclassmembers class com.noxquill.rewordium.keyboard.app.Routes$** {
    public static ** INSTANCE;
    public static ** Companion;
    kotlinx.serialization.KSerializer serializer(...);
    ** $$serializer;
}

# Keep generated serializers
-keepclassmembers class **$$serializer {
    *** serialDescriptor;
    public static ** INSTANCE;
}

# Keep all @Serializable classes and their generated serializers
-keep,includedescriptorclasses @kotlinx.serialization.Serializable class * { *; }
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}

# ==============================================================================
# AI MANAGER AND KEYBOARD AI FEATURES (CRITICAL FOR RELEASE MODE)
# ==============================================================================

# Keep AI-related classes in the keyboard module
-keep class com.noxquill.rewordium.keyboard.ime.ai.** { *; }
-keep interface com.noxquill.rewordium.keyboard.ime.ai.** { *; }
-keepclassmembers class com.noxquill.rewordium.keyboard.ime.ai.** {
    <fields>;
    <methods>;
}

# Keep AIManager and its inner classes
-keep class com.noxquill.rewordium.keyboard.ime.ai.AIManager { *; }
-keep class com.noxquill.rewordium.keyboard.ime.ai.AIManager$* { *; }
-keep class com.noxquill.rewordium.keyboard.ime.ai.AIPersona { *; }
-keep class com.noxquill.rewordium.keyboard.ime.ai.AIAction { *; }
-keep class com.noxquill.rewordium.keyboard.ime.ai.AIException { *; }

# Keep AI request/response classes for GSON serialization
-keep class com.noxquill.rewordium.keyboard.ime.ai.*Request { *; }
-keep class com.noxquill.rewordium.keyboard.ime.ai.*Response { *; }
-keep class com.noxquill.rewordium.keyboard.ime.ai.*Message { *; }
-keep class com.noxquill.rewordium.keyboard.ime.ai.*Choice { *; }

# Keep BuildConfig for API keys
-keep class com.noxquill.rewordium.keyboard.BuildConfig { *; }
-keep class com.noxquill.rewordium.BuildConfig { *; }
-keepclassmembers class com.noxquill.rewordium.keyboard.BuildConfig {
    public static <fields>;
}
-keepclassmembers class com.noxquill.rewordium.BuildConfig {
    public static <fields>;
}