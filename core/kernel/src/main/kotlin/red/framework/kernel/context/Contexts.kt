package red.framework.kernel.context

import red.framework.kernel.identity.SiteId
import java.util.UUID

/** Site identity resolved and authenticated by the delivery boundary. Never construct from an unchecked request field. */
data class SiteContext(val siteId: SiteId)

/** Correlation and principal information propagated through one operation. */
data class RequestContext(
    val requestId: UUID,
    val site: SiteContext,
    val principalId: UUID?,
) {
    companion object {
        fun forSystem(site: SiteContext): RequestContext = RequestContext(UUID.randomUUID(), site, null)
    }
}
