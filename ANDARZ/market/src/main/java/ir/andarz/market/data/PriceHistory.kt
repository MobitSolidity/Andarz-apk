package ir.andarz.market.data

/**
 * In-memory rolling price samples per coin, used to detect sharp moves over 5 min / 15 min / 1 h.
 * Ported from March's `data/PriceHistory.kt` unchanged.
 */
class PriceHistory(private val keepMs: Long = 70 * 60_000L) {
    private class Sample(val t: Long, val p: Double)

    private val data = HashMap<String, ArrayDeque<Sample>>()

    @Synchronized
    fun add(id: String, t: Long, p: Double) {
        if (p <= 0) return
        val q = data.getOrPut(id) { ArrayDeque() }
        q.addLast(Sample(t, p))
        while (q.isNotEmpty() && t - q.first().t > keepMs) q.removeFirst()
    }

    /** (min, max) of samples since [since], or null if fewer than 2 samples. */
    @Synchronized
    fun range(id: String, since: Long): Pair<Double, Double>? {
        val q = data[id] ?: return null
        var min = Double.MAX_VALUE
        var max = 0.0
        var n = 0
        for (s in q) {
            if (s.t < since) continue
            if (s.p < min) min = s.p
            if (s.p > max) max = s.p
            n++
        }
        return if (n >= 2) min to max else null
    }

    @Synchronized
    fun series(id: String): List<Double> = data[id]?.map { it.p } ?: emptyList()
}
