# Master execution plan

The source directive is the attached autonomous engineering master directive. Implement in verified batches, without claiming empty scaffolds as features.

| Phase | Capability | Gate |
| --- | --- | --- |
| A | Build, conventions, CI, architecture | Clean build and dependency boundary check |
| B | Kernel primitives and contracts | Unit tests and source compilation |
| C | PostgreSQL infrastructure | Real database integration tests |
| D | Sites and applications | Isolation and manifest tests |
| E–F | Metadata and attribute types | Persistence, versioning, validation tests |
| G–I | Schema, records, query | Dynamic Customer table/record slice |
| J–L | Security and runtime | Authorization, outbox, jobs, workflows, advanced features |
| M–P | APIs, Studio, ecosystem | End-to-end tests against real site |
| Q | Release readiness | Security, recovery, performance, installation review |

Use ADRs before consequential design choices. Do not add empty modules. Keep CLI and IDE implementations in their separate future repositories.
