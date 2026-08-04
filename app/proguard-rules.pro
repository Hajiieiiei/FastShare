# --- FastShare ProGuard / R8 rules ---
-keepattributes Signature,InnerClasses,EnclosingMethod,RuntimeVisibleAnnotations,AnnotationDefault

# kotlinx.serialization
-keepclassmembers class * {
    *** Companion;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.fastshare.app.**$$serializer { *; }
-keepclassmembers class com.fastshare.app.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}

# Ktor / CIO
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**
-dontwarn kotlinx.coroutines.debug.**
-keepclassmembers class io.ktor.server.engine.** { *; }

# BouncyCastle (self-signed TLS certificate generation)
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**
-dontwarn javax.naming.**

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**

# Room
-keep class * extends androidx.room.RoomDatabase { <init>(); }

# Hilt / Dagger generated
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.lifecycle.HiltViewModelFactory { *; }

# SLF4J used by Ktor logging facade
-dontwarn org.slf4j.**
-keep class org.slf4j.** { *; }
