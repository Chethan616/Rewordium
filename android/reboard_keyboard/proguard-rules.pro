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

# ==============================================================================
# REBOARD KEYBOARD / FLORISBOARD THEME SYSTEM (CRITICAL FOR RELEASE MODE)
# ==============================================================================

# --- Snygg Theme Engine (core theming system) ---
# Snygg uses object singletons, custom serialization/deserialization,
# and interface polymorphism. R8 strips these causing black keyboard backgrounds.
-keep class org.florisboard.lib.snygg.** { *; }
-keep interface org.florisboard.lib.snygg.** { *; }
-keepclassmembers class org.florisboard.lib.snygg.** {
    <fields>;
    <methods>;
}

# Keep Snygg value objects (SnyggInheritValue, SnyggUndefinedValue, etc.)
-keep class org.florisboard.lib.snygg.value.** { *; }
-keepclassmembers class org.florisboard.lib.snygg.value.** {
    public static ** INSTANCE;
    <fields>;
    <methods>;
}

# Keep Snygg UI components
-keep class org.florisboard.lib.snygg.ui.** { *; }

# --- FlorisBoard Color Library ---
-keep class org.florisboard.lib.color.** { *; }
-keepclassmembers class org.florisboard.lib.color.** {
    <fields>;
    <methods>;
}

# --- FlorisBoard Native Library (JNI bridge) ---
-keep class org.florisboard.libnative.** { *; }
-keepclassmembers class org.florisboard.libnative.** {
    native <methods>;
    <fields>;
    <methods>;
}

# --- FlorisBoard Kotlin Utils ---
-keep class org.florisboard.lib.kotlin.** { *; }

# --- FlorisBoard Android Utils ---
-keep class org.florisboard.lib.android.** { *; }

# --- FlorisBoard Compose Utils ---
-keep class org.florisboard.lib.compose.** { *; }

# --- Reboard Keyboard IME Theme Classes ---
-keep class com.noxquill.rewordium.keyboard.ime.theme.** { *; }
-keep interface com.noxquill.rewordium.keyboard.ime.theme.** { *; }
-keepclassmembers class com.noxquill.rewordium.keyboard.ime.theme.** {
    <fields>;
    <methods>;
}

# --- Reboard Keyboard IME Core ---
-keep class com.noxquill.rewordium.keyboard.ime.** { *; }
-keep interface com.noxquill.rewordium.keyboard.ime.** { *; }

# Keep InputMethodService subclasses
-keep public class * extends android.inputmethodservice.InputMethodService {
    public <init>();
}

# --- Reboard App Theme Classes ---
-keep class com.noxquill.rewordium.keyboard.app.apptheme.** { *; }
-keep class com.noxquill.rewordium.keyboard.app.settings.theme.** { *; }

# --- JetPref Datastore (settings storage for FlorisBoard) ---
-keep class dev.patrickgold.jetpref.** { *; }
-keep interface dev.patrickgold.jetpref.** { *; }
-keepclassmembers class dev.patrickgold.jetpref.** {
    <fields>;
    <methods>;
}

# --- Extension system ---
-keep class com.noxquill.rewordium.keyboard.ime.extension.** { *; }
-keep interface com.noxquill.rewordium.keyboard.ime.extension.** { *; }

# ==============================================================================
# MATERIAL YOU / DYNAMIC COLORS / WALLPAPER (CRITICAL FOR SYSTEM COLOR THEMING)
# ==============================================================================

# --- MaterialKolor Library (generates dynamic color schemes from system/wallpaper) ---
-keep class com.materialkolor.** { *; }
-keep interface com.materialkolor.** { *; }
-keepclassmembers class com.materialkolor.** {
    <fields>;
    <methods>;
}
-keep class com.materialkolor.dynamiccolor.** { *; }

# --- Snygg Dynamic Color Values (system color adaption) ---
-keep class org.florisboard.lib.snygg.value.SnyggDynamicColorValue { *; }
-keep class org.florisboard.lib.snygg.value.SnyggDynamicColorDarkColorValue { *; }
-keep class org.florisboard.lib.snygg.value.SnyggDynamicColorLightColorValue { *; }
-keep class org.florisboard.lib.snygg.value.SnyggDynamicLightColorValue { *; }
-keep class org.florisboard.lib.snygg.value.SnyggDynamicDarkColorValue { *; }
-keep class org.florisboard.lib.snygg.value.SnyggStaticColorValue { *; }
-keep class org.florisboard.lib.snygg.value.SnyggAppearanceValue { *; }

# --- Wallpaper Change Receiver ---
-keep class com.noxquill.rewordium.keyboard.ime.theme.WallpaperChangeReceiver { *; }
-keep public class * extends android.content.BroadcastReceiver {
    public <init>();
}

# --- FlorisBoard Color Processing ---
-keep class org.florisboard.lib.color.GboardStyleColorProcessor { *; }
-keep class org.florisboard.lib.color.ColorMappings { *; }
-keep class org.florisboard.lib.color.ColorMappingsKt { *; }
-keep class org.florisboard.lib.color.MaterialYouFlags { *; }
-keep class org.florisboard.lib.color.MaterialYouFlagsSaver { *; }

# --- Cache4k ---
-keep class io.github.reactivecircus.cache4k.** { *; }

# --- FlorisImeService ---
-keep class com.noxquill.rewordium.keyboard.FlorisImeService { *; }
-keep class com.noxquill.rewordium.keyboard.FlorisImeService$* { *; }

# Room safety for clipboard and dictionary internals
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keepclassmembers class * {
    @androidx.room.Query <methods>;
    @androidx.room.Insert <methods>;
    @androidx.room.Update <methods>;
    @androidx.room.Delete <methods>;
    @androidx.room.TypeConverter <methods>;
}

# EmojiCompat safety for emoji panel runtime
-keep class androidx.emoji2.** { *; }
-keep class androidx.emoji.text.** { *; }
