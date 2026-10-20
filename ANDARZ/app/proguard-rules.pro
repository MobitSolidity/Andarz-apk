# R8 rules for :app (release). §8.4: no Log calls survive into release, and no analytics or crash SDK
# exists to keep rules for.
#
# Reviewed with the r8-analyzer skill (D-80): Room (room-runtime proguard.txt) and kotlinx-serialization
# (META-INF/com.android.tools/r8/*.pro) ship their own consumer rules, so no keep rule for either lives here;
# the engine has no reflection, so it is no longer kept wholesale (stack traces: use the mapping file
# app/build/outputs/mapping/release/mapping.txt with retrace).

# Strip every android.util.Log call: a release build never logs amounts, notes or passwords (§8.4).
-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
    public static *** wtf(...);
}

# Readable line numbers in crash stack traces; the file name itself is hidden.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
