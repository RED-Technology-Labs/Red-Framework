package red.framework.application

import red.framework.kernel.identity.ApplicationId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ApplicationManifestTest {
    @Test
    fun `versions order numerically and parse canonically`() {
        assertEquals(ApplicationVersion(2, 1, 0), ApplicationVersion.parse("2.1.0"))
        assertEquals(true, ApplicationVersion(2, 10, 0) > ApplicationVersion(2, 9, 9))
        for (bad in listOf("01.0.0", "1.0", "1.-1.0", "1.0.0-beta")) {
            assertFailsWith<IllegalArgumentException> { ApplicationVersion.parse(bad) }
        }
    }

    @Test
    fun `manifest rejects self and duplicate dependencies`() {
        val id = ApplicationId.new()
        val version = ApplicationVersion(1, 0, 0)
        assertFailsWith<IllegalArgumentException> {
            ApplicationManifest(id, "red-crm", version, listOf(ApplicationDependency(id, version)))
        }
        val dependency = ApplicationDependency(ApplicationId.new(), version)
        assertFailsWith<IllegalArgumentException> {
            ApplicationManifest(id, "red-crm", version, listOf(dependency, dependency))
        }
    }

    @Test
    fun `manifest copies dependency collection`() {
        val dependencies = mutableListOf(ApplicationDependency(ApplicationId.new(), ApplicationVersion(1, 0, 0)))
        val manifest = ApplicationManifest(ApplicationId.new(), "red-crm", ApplicationVersion(1, 0, 0), dependencies)
        dependencies.clear()
        assertEquals(1, manifest.dependencies.size)
        assertFailsWith<UnsupportedOperationException> {
            (manifest.dependencies as MutableList).clear()
        }
    }
}
