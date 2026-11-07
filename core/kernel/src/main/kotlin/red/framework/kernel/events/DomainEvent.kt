package red.framework.kernel.events

import red.framework.kernel.identity.SiteId
import java.time.Instant
import java.util.UUID

/** Fact recorded by the domain. Persistence and publication belong to infrastructure. */
interface DomainEvent {
    val eventId: UUID
    val siteId: SiteId
    val occurredAt: Instant
    val eventType: String
}

/** Port for recording events in the same transaction as their source operation. */
fun interface DomainEventRecorder {
    fun record(event: DomainEvent)
}
