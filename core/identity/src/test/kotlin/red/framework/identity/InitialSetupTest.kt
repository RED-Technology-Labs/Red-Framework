package red.framework.identity

import kotlin.test.Test
import kotlin.test.assertFailsWith

class InitialSetupTest {
    @Test fun `rejects weak or mismatched passwords`() {
        assertFailsWith<IllegalArgumentException> { InitialSetup("A User", "a@example.com", "weak", "weak", "first-site") }
        assertFailsWith<IllegalArgumentException> { InitialSetup("A User", "a@example.com", "Strong#Pass123", "different", "first-site") }
    }
}
