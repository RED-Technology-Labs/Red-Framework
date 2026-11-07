package red.framework.kernel

import red.framework.kernel.context.RequestContext
import red.framework.kernel.context.SiteContext
import red.framework.kernel.errors.DomainError
import red.framework.kernel.events.DomainEvent
import red.framework.kernel.events.DomainEventRecorder
import red.framework.kernel.identity.SiteId
import red.framework.kernel.result.Outcome
import red.framework.kernel.time.Clock
import red.framework.kernel.versioning.Revision
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame

class KernelContractsTest {
    @Test
    fun `identifiers round trip and remain distinct types`() {
        val site = SiteId.new()
        assertEquals(site, SiteId.parse(site.toString()))
        assertFailsWith<IllegalArgumentException> { SiteId.parse("not-a-uuid") }
        assertFailsWith<IllegalArgumentException> {
            SiteId.parse(site.toString().uppercase())
        }
    }

    @Test
    fun `revision enforces positive monotonic values and overflow`() {
        assertFailsWith<IllegalArgumentException> { Revision.of(0) }
        assertEquals(Revision.of(2), Revision.INITIAL.next())
        assertEquals(-1, Revision.of(2).compareTo(Revision.of(3)))
        assertFailsWith<IllegalStateException> { Revision.of(Long.MAX_VALUE).next() }
    }

    @Test
    fun `outcome maps success and preserves typed failure`() {
        assertEquals(Outcome.Success(4), Outcome.Success(2).map { it * 2 })
        val failure: Outcome<Int> = Outcome.Failure(DomainError.Forbidden("denied"))
        val mapped = failure.map { it * 2 }
        assertIs<Outcome.Failure>(mapped)
        assertEquals("forbidden", mapped.error.code)
    }

    @Test
    fun `system request keeps its site and no principal`() {
        val site = SiteContext(SiteId.new())
        val context = RequestContext.forSystem(site)
        assertEquals(site, context.site)
        assertEquals(null, context.principalId)
    }

    @Test
    fun `clock is replaceable without wall clock access`() {
        val fixed = Instant.parse("2026-01-01T00:00:00Z")
        val clock = Clock { fixed }
        assertEquals(fixed, clock.now())
    }

    @Test
    fun `domain event recorder receives the exact site scoped event`() {
        val siteId = SiteId.new()
        val event = object : DomainEvent {
            override val eventId = UUID.randomUUID()
            override val siteId = siteId
            override val occurredAt = Instant.parse("2026-01-01T00:00:00Z")
            override val eventType = "kernel.test"
        }
        val recorded = mutableListOf<DomainEvent>()
        val recorder = DomainEventRecorder { recorded.add(it) }
        recorder.record(event)
        assertSame(event, recorded.single())
    }
}
