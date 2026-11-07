package red.framework.kernel.time

import java.time.Instant

/** Injectable time source for domain decisions and tests. */
fun interface Clock {
    fun now(): Instant
}

object SystemClock : Clock {
    override fun now(): Instant = Instant.now()
}
