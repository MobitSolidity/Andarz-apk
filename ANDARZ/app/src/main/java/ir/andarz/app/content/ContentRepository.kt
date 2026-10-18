package ir.andarz.app.content

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loads the wording once and answers from memory afterwards.
 *
 * Callable from any thread and from a broadcast receiver in a cold process (K4): the load is lazy,
 * synchronous, guarded by a lock, and touches nothing but the asset manager. That matters because market
 * notifications are built in a receiver, long before any screen exists.
 */
@Singleton
class ContentRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    @Volatile
    private var loaded: ContentBundle? = null
    private val lock = Any()

    /** The bundle, loading it on this thread if it is not in memory yet. */
    val bundle: ContentBundle
        get() = loaded ?: synchronized(lock) {
            loaded ?: read(context).also { loaded = it }
        }

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

    companion object {
        const val BASE_ASSET = "content_fa.json"
        const val MARKET_ASSET = "content_market_fa.json"

        private fun read(context: Context): ContentBundle {
            val assets = context.assets
            val base = assets.open(BASE_ASSET).use { it.readBytes().decodeToString() }
            // The market document is optional: a build without Market still has to start.
            val market = runCatching {
                assets.open(MARKET_ASSET).use { it.readBytes().decodeToString() }
            }.getOrNull()
            return ContentBundle.parse(base, market)
        }
    }
}
