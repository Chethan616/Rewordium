# Flutter-specific rules.
-keep class io.flutter.app.** { *; }
-keep class io.flutter.plugin.**  { *; }
-keep class io.flutter.util.**  { *; }
-keep class io.flutter.view.**  { *; }
-keep class io.flutter.embedding.**  { *; }
-keep class io.flutter.plugins.** { *; }

# More comprehensive rules for Flutter plugins
-keep public class * extends io.flutter.plugin.common.MethodChannel.MethodCallHandler
-keep public class * extends io.flutter.plugin.common.EventChannel.StreamHandler
-keep class io.flutter.plugin.platform.PlatformViewsController {
    public <init>(android.content.Context, io.flutter.embedding.engine.FlutterEngine, io.flutter.embedding.engine.dart.DartExecutor);
}
-keepclassmembers class * extends io.flutter.plugin.common.PluginRegistry.Registrar {
    public <init>();
}
-keepclassmembers class * extends java.lang.Object {
    @io.flutter.plugin.common.MethodChannel.MethodCallHandler
    public *;
}
-keep class * implements io.flutter.plugin.common.PluginRegistry.PluginRegistrantCallback {
    public <init>();
    public void registerWith(io.flutter.plugin.common.PluginRegistry);
}
-keep class io.flutter.embedding.engine.plugins.shim.ShimPluginRegistry {
    public <init>(io.flutter.embedding.engine.FlutterEngine);
}
-keep class io.flutter.embedding.engine.plugins.FlutterPluginV1 {
    public <init>();
}
-keep class io.flutter.embedding.engine.plugins.activity.ActivityAware {
    public <init>();
}
-keep class io.flutter.embedding.engine.plugins.service.ServiceAware {
    public <init>();
}
-keep class io.flutter.embedding.engine.plugins.broadcastreceiver.BroadcastReceiverAware {
    public <init>();
}
-keep class io.flutter.embedding.engine.plugins.contentprovider.ContentProviderAware {
    public <init>();
}
-keep class io.flutter.plugins.GeneratedPluginRegistrant {
    public static void registerWith(io.flutter.embedding.engine.FlutterEngine);
}
-dontwarn io.flutter.embedding.**

# Keep the entire application package. This is a broad rule to ensure no app code is stripped.
-keep class com.example.yc_startup.** { *; }
-keep interface com.example.yc_startup.** { *; }

# Keep the Accessibility Service from being obfuscated, as the system interacts with it via specific method names.
-keep class com.example.yc_startup.service.MyAccessibilityService { *; }

# Keep GSON specific classes to prevent TypeToken errors.
-keep class com.google.gson.reflect.TypeToken
-keep class * extends com.google.gson.reflect.TypeToken
-keep class com.google.gson.Gson
-keep class com.google.gson.JsonElement
-keep class com.google.gson.JsonObject
-keep class com.google.gson.JsonArray
-keep class com.google.gson.JsonPrimitive
-keep class com.google.gson.JsonNull
-keep class com.google.gson.internal.** {* ;}

# Keep attributes required for serialization.
-keepattributes Signature
-keepattributes *Annotation*

# Keep Kotlin Coroutines suspend functions used by Retrofit
-keepclassmembers,allowobfuscation interface * {
    suspend <methods>;
}
-keepclassmembers,allowobfuscation class * {
    suspend <methods>;
}
-keep,allowobfuscation class kotlin.coroutines.Continuation

# For Retrofit and OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-keepclasseswithmembers class * {
    @retrofit2.http.GET <methods>;
    @retrofit2.http.POST <methods>;
    @retrofit2.http.PUT <methods>;
    @retrofit2.http.DELETE <methods>;
    @retrofit2.http.PATCH <methods>;
    @retrofit2.http.OPTIONS <methods>;
    @retrofit2.http.HEAD <methods>;
}
