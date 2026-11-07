# Modules

| Path | State | Responsibility | Proposed dependencies |
| --- | --- | --- | --- |
| `core/kernel` | Implemented and unit tested foundation | Universal domain primitives | None |
| `core/site` | Implemented and unit tested foundation | Site descriptor and registry port | Kernel |
| `core/application` | Implemented and unit tested foundation | Manifest and dependency invariants | Kernel |
| `core/metadata` | Implemented foundation, integration tested via adapter | Definition invariants and repository port | Kernel |
| `core/attributes` | Reserved folder | Attribute type contracts | Kernel |
| `core/database` | Reserved folder | Legacy name under review; use explicit schema and infrastructure modules | None yet |
| `core/records` | Reserved folder | Record use cases and ports | Kernel, metadata, attributes |
| `apps/server` | Reserved folder | HTTP composition | Core use cases, infrastructure |
| `apps/worker` | Reserved folder | Job composition | Core use cases, infrastructure |
| `infrastructure/database-postgres` | Tested transaction slice | PostgreSQL pool and transaction adapter | HikariCP, pgJDBC; no project dependencies yet |
| `infrastructure/database-migrations` | Tested baseline | Flyway system-table migrations | Flyway Core and PostgreSQL module; uses `DataSource` contract |
| `studio`, `sdk` | Reserved folders | Future separate deliverables | Undecided |

Proposed later modules: `core/schema-engine`. Add them only with cohesive code and tests. Avoid direct core-to-infrastructure edges and cycles. The current `app` and `utils` starter modules are excluded from the build but retained for review.
