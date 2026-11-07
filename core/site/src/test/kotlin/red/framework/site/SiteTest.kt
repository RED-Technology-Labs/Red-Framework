package red.framework.site

import red.framework.kernel.identity.SiteId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SiteTest {
    @Test
    fun `accepts canonical site name and explicit status`() {
        val id = SiteId.new()
        val site = Site(id, "red-dev", SiteStatus.ACTIVE)
        assertEquals(id, site.id)
        assertEquals(SiteStatus.ACTIVE, site.status)
    }

    @Test
    fun `rejects ambiguous site names`() {
        for (name in listOf("RED", "a", "bad_name", "-bad", "bad-", "a".repeat(64))) {
            assertFailsWith<IllegalArgumentException>(name) { Site(SiteId.new(), name, SiteStatus.ACTIVE) }
        }
    }
}
