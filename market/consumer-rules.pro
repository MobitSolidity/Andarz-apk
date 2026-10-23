# Consumer rules :market hands to :app (K4). OkHttp ships its own rules; these cover the few members
# that only the library itself reaches.

# OkHttp / okio: platform classes that are absent on Android and only probed reflectively.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# The public surface of :market is what :app calls by name (MarketModule, MarketHost, MarketTexts).
-keep class ir.andarz.market.MarketModule { *; }
-keep interface ir.andarz.market.MarketHost { *; }
-keep interface ir.andarz.market.MarketTexts { *; }
