# Disable obfuscation (we use Proguard exclusively for optimization)
-dontobfuscate

# Keep Routes classes and all serialization data
-keep class com.noxquill.rewordium.keyboard.app.Routes { *; }
-keep class com.noxquill.rewordium.keyboard.app.Routes$* { *; }
-keep class com.noxquill.rewordium.keyboard.app.Routes$**$* { *; }
-keepclassmembers class com.noxquill.rewordium.keyboard.app.Routes$** {
    public static ** INSTANCE;
    public static ** Companion;
    kotlinx.serialization.KSerializer serializer(...);
    ** $$serializer;
}

# Keep all @Serializable classes
-keep,includedescriptorclasses @kotlinx.serialization.Serializable class * { *; }

# Keep generated serializers
-keepclassmembers class **$$serializer {
    *** serialDescriptor;
    public static ** INSTANCE;
}

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

# ==============================================================================
# AI MANAGER AND KEYBOARD AI FEATURES (CRITICAL FOR RELEASE MODE)
# ==============================================================================

# Keep AI-related classes
-keep class com.noxquill.rewordium.keyboard.ime.ai.** { *; }
-keep interface com.noxquill.rewordium.keyboard.ime.ai.** { *; }
-keepclassmembers class com.noxquill.rewordium.keyboard.ime.ai.** {
    <fields>;
    <methods>;
}

# Keep AIManager and all its inner/companion classes
-keep class com.noxquill.rewordium.keyboard.ime.ai.AIManager { *; }
-keep class com.noxquill.rewordium.keyboard.ime.ai.AIManager$* { *; }
-keep class com.noxquill.rewordium.keyboard.ime.ai.AIPersona { *; }
-keep class com.noxquill.rewordium.keyboard.ime.ai.AIAction { *; }
-keep class com.noxquill.rewordium.keyboard.ime.ai.AIException { *; }

# Keep GSON-annotated classes for AI API requests/responses
-keep class com.noxquill.rewordium.keyboard.ime.ai.GroqRequest { *; }
-keep class com.noxquill.rewordium.keyboard.ime.ai.GroqResponse { *; }
-keep class com.noxquill.rewordium.keyboard.ime.ai.ChatMessage { *; }
-keep class com.noxquill.rewordium.keyboard.ime.ai.ChatChoice { *; }
-keepclassmembers class com.noxquill.rewordium.keyboard.ime.ai.* {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Keep BuildConfig for API keys
-keep class com.noxquill.rewordium.keyboard.BuildConfig { *; }
-keepclassmembers class com.noxquill.rewordium.keyboard.BuildConfig {
    public static <fields>;
}

# Keep OkHttp and Gson for AI networking
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keep class com.google.gson.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**
