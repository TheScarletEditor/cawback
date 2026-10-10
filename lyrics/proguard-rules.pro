# SQLCipher's native bridge (the Library database).
-keep class net.zetetic.database.** { *; }
-dontwarn net.zetetic.database.**

# Bouncy Castle: only Argon2 is used (Library backups); keep it and ignore the rest of the provider.
-keep class org.bouncycastle.crypto.generators.Argon2BytesGenerator { *; }
-keep class org.bouncycastle.crypto.params.Argon2Parameters** { *; }
-dontwarn org.bouncycastle.**

# Release builds strip every android.util.Log call (spec 0.5: no content logging).
-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
    public static *** wtf(...);
    public static *** println(...);
    public static boolean isLoggable(...);
}

