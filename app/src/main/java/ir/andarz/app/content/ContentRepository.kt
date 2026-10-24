package ir.andarz.app.content

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import ir.andarz.data.settings.Language
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loads the wording once per language and answers from memory afterwards.
 *
 * Callable from any thread and from a broadcast receiver in a cold process (K4): the load is lazy,
 * synchronous, guarded by a lock, and touches nothing but the asset manager and one small preferences file.
 * That matters because market notifications are built in a receiver, long before any screen exists.
 *
 * Language (D-90): [bundle] answers in the current display language. The language is mirrored into a tiny
 * SharedPreferences file by [setLanguage] (called by the app whenever the DataStore setting changes), so a cold
 * receiver can read it synchronously without touching DataStore.
 */
@Singleton
class ContentRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val loaded = HashMap<Language, ContentBundle>()
    private val lock = Any()

    @Volatile
    private var current: Language? = null

    /** The current display language, read once from the mirror and then kept in memory. */
    val language: Language
        get() = current ?: synchronized(lock) {
            current ?: readMirror().also { current = it }
        }

    /** Records the display language (from the settings flow) so every later lookup uses it. */
    fun setLanguage(l: Language) {
        if (current == l) return
        synchronized(lock) {
            current = l
            runCatching { context.getSharedPreferences(MIRROR, Context.MODE_PRIVATE).edit().putString(KEY, l.name).apply() }
        }
    }

    /** The bundle of [l], loading it on this thread if it is not in memory yet. */
    fun bundleFor(l: Language): ContentBundle = synchronized(lock) {
        loaded.getOrPut(l) { read(context, l) }
    }

    /** The bundle of the current language. */
    val bundle: ContentBundle get() = bundleFor(language)

    /** Warms the cache off the main thread, e.g. from `Application.onCreate`. */
    suspend fun preload(): ContentBundle = withContext(Dispatchers.IO) { bundle }

    /** The wording of [key] with its `{placeholder}` arguments already formatted by the caller. */
    fun ui(key: String, args: Map<String, String> = emptyMap()): String = bundle.ui(key, args)

    /** The wording of [key], or null when no document defines it. */
    fun uiOrNull(key: String): String? = bundle.uiOrNull(key)

    /** Convenience form: `ui("mkt.consent.hosts", "hosts" to text)`. */
    fun ui(key: String, vararg args: Pair<String, String>): String = bundle.ui(key, args.toMap())

    /** The app's own name, from `meta.app.name`. */
    val appName: String get() = bundle.appName

    private fun readMirror(): Language = runCatching {
        val v = context.getSharedPreferences(MIRROR, Context.MODE_PRIVATE).getString(KEY, null)
        Language.entries.firstOrNull { it.name == v }
    }.getOrNull() ?: Language.FA

    companion object {
        const val BASE_ASSET = "content_fa.json"
        const val MARKET_ASSET = "content_market_fa.json"
        const val BASE_ASSET_EN = "content_en.json"
        const val MARKET_ASSET_EN = "content_market_en.json"
        const val GLOSSARY_ASSET = "glossary.json"
        const val APP_ASSET = "content_app_fa.json"
        const val APP_ASSET_EN = "content_app_en.json"
        private const val MIRROR = "andarz_language"
        private const val KEY = "language"

        fun assetsOf(l: Language): Pair<String, String> = when (l) {
            Language.FA -> BASE_ASSET to MARKET_ASSET
            Language.EN -> BASE_ASSET_EN to MARKET_ASSET_EN
        }

        private fun read(context: Context, l: Language): ContentBundle {
            val assets = context.assets
            val (b, m) = assetsOf(l)
            val base = runCatching { assets.open(b).use { it.readBytes().decodeToString() } }
                .getOrElse { assets.open(BASE_ASSET).use { s -> s.readBytes().decodeToString() } }
            // The market document is optional: a build without Market still has to start.
            val market = runCatching { assets.open(m).use { it.readBytes().decodeToString() } }.getOrNull()
            val glossary = runCatching { assets.open(GLOSSARY_ASSET).use { it.readBytes().decodeToString() } }.getOrNull()
            val app = runCatching { assets.open(if (l == Language.EN) APP_ASSET_EN else APP_ASSET).use { it.readBytes().decodeToString() } }.getOrNull()
            return ContentBundle.parse(base, market, glossary, app)
        }
    }
}
