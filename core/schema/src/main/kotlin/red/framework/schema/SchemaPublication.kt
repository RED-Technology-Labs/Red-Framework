package red.framework.schema

import red.framework.kernel.context.SiteContext
import red.framework.kernel.identity.EntityId
import red.framework.kernel.versioning.Revision
import red.framework.metadata.EntityDefinition

data class SchemaPublication(val entityId: EntityId, val publishedRevision: Revision, val tableName: String)

interface SchemaPublisher {
    /** Creates a table or applies a safe additive migration. Re-publishing is idempotent. */
    fun publish(site: SiteContext, definition: EntityDefinition): SchemaPublication
    fun findPublication(site: SiteContext, entityId: EntityId): SchemaPublication?
}
