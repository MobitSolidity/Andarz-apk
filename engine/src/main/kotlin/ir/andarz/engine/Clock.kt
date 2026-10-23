package ir.andarz.engine

import java.time.Instant

/** The only source of "now" (10). Production uses [System]; every time-dependent test injects a fake. */
fun interface Clock {
    fun now(): Instant

    companion object {
        val System: Clock = Clock { Instant.now() }
    }
}
