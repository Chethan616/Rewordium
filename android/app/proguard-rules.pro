# Flutter-specific rules.
-keep class io.flutter.app.** { *; }
-keep class io.flutter.plugin.**  { *; }
-keep class io.flutter.util.**  { *; }
-keep class io.flutter.view.**  { *; }
-keep class io.flutter.embedding.**  { *; }
-keep class io.flutter.plugins.**  { *; }
-dontwarn io.flutter.embedding.**

# Keep the entire application package. This is a broad rule to ensure no app code is stripped.
-keep class com.example.yc_startup.** { *; }
-keep interface com.example.yc_startup.** { *; }

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
