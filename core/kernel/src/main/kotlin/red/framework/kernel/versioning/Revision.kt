package red.framework.kernel.versioning

/** Positive revision of an immutable metadata or record state. */
@JvmInline
value class Revision private constructor(val value: Long) : Comparable<Revision> {
    companion object {
        val INITIAL: Revision = Revision(1)
        fun of(value: Long): Revision {
            require(value > 0) { "Revision must be positive" }
            return Revision(value)
        }
    }
    fun next(): Revision {
        check(value < Long.MAX_VALUE) { "Revision overflow" }
        return Revision(value + 1)
    }
    override fun compareTo(other: Revision): Int = value.compareTo(other.value)
}
