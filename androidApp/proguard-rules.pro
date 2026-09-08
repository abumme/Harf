# R8 keep rules for the Harf release build.
# Most libraries ship consumer rules; these cover reflection/ServiceLoader paths R8 can't see and
# the kotlinx.serialization generated serializers our DTOs depend on.

# --- kotlinx.serialization (canonical rules; R8 full mode strips serializers otherwise) ---
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault,*Annotation*,InnerClasses,EnclosingMethod

-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# Belt-and-braces for our own @Serializable DTOs (auth/sync/wordpack live under this package).
-keep,includedescriptorclasses class uz.abumme.harfgame.**$$serializer { *; }
-keepclassmembers class uz.abumme.harfgame.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}

# --- Ktor client (OkHttp engine registered via ServiceLoader) ---
-keep class io.ktor.client.engine.okhttp.** { *; }
-keepnames class io.ktor.** { *; }
-dontwarn io.ktor.**
-dontwarn org.slf4j.**

# --- OkHttp / Okio and optional TLS providers they reference reflectively ---
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# --- KSafe encrypted storage (Tink / DataStore under the hood) ---
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**

# --- RevenueCat (ships consumer rules; silence stripped optional refs) ---
-dontwarn com.revenuecat.**

# --- Coroutines (ships consumer rules; guard reflective internals) ---
-dontwarn kotlinx.coroutines.**
