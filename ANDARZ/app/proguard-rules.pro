# R8 rules for :app (release). §8.4: no Log calls survive into release, and no analytics or crash SDK
# exists to keep rules for.

# Strip every android.util.Log call: a release build never logs amounts, notes or passwords (§8.4).
-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
    public static *** wtf(...);
}

# kotlinx-serialization keeps its generated serializers reachable through reflection.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Room generates implementations that are only referenced by name.
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# The engine is pure Kotlin and fully reachable; keeping its names makes a stack trace readable without
# shipping a mapping file to anyone.
-keep class ir.andarz.engine.** { *; }
