# SQLCipher's native bridge (the Library database).
-keep class net.zetetic.database.** { *; }
-dontwarn net.zetetic.database.**

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

