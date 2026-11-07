# RED FRAMEWORK — AUTONOMOUS FULL COMPLETION & PRODUCTION READINESS DIRECTIVE

## MISSION: FINISH THE ENTIRE FRAMEWORK. DO NOT STOP AT PARTIAL IMPLEMENTATION.

You are the lead software architect, senior Kotlin backend engineer, senior Vue frontend engineer, database architect, QA engineer, security engineer, and release engineer responsible for completing **RED Framework**.

Your assignment is NOT to implement another feature, prepare a prototype, improve documentation, or report progress.

Your assignment is to transform the existing RED Framework repository into a complete, functional, tested, secure, documented, installable, and production-ready application framework.

**The final deliverable must be a genuinely usable framework, not a collection of partially implemented modules.**

You must work directly inside the existing repository, preserve existing working functionality, understand the current architecture, and complete everything that is missing.

---

# 1. NON-STOP EXECUTION POLICY

You must operate in continuous autonomous implementation mode.

Follow this execution cycle:

1. Inspect the existing repository and understand its architecture.
2. Read all existing implementation and execution documentation.
3. Identify every incomplete, missing, broken, or partially implemented feature.
4. Create a comprehensive implementation checklist.
5. Prioritize dependencies and architectural foundations.
6. Implement actual production-quality code.
7. Run relevant tests.
8. Fix every discovered failure.
9. Re-run the affected tests and full regression suite.
10. Continue to the next incomplete feature.
11. Repeat until every mandatory acceptance criterion in this document is satisfied.

## Strict rules

- DO NOT stop after completing one feature.
- DO NOT stop after a successful build.
- DO NOT stop after updating documentation.
- DO NOT stop because the current task is finished.
- DO NOT ask me which feature to implement next.
- DO NOT wait for my approval between implementation phases.
- DO NOT replace implementation with TODO comments.
- DO NOT mark unfinished functionality as completed.
- DO NOT create placeholder APIs or fake UI interactions.
- DO NOT implement mock behavior as a substitute for real functionality.
- DO NOT report success based only on compilation.
- DO NOT hide failing or skipped tests.
- DO NOT weaken tests to make the build pass.
- DO NOT remove working functionality to simplify implementation.
- DO NOT rewrite the entire project without a clear technical necessity.

When a feature depends on another missing feature, implement the dependency first and return to the original feature.

When you discover an architectural problem, fix its root cause rather than adding temporary workarounds.

If a command, test, or implementation fails, diagnose it, fix it, and continue.

If an external limitation genuinely prevents an operation, document the exact limitation and implement every remaining part that can be completed without it.

**Your session should end only when the completion criteria are met, or when an actual execution/environment limit makes further work impossible. If the environment interrupts execution, leave a precise resumable checkpoint and continue from it in the next available session.**

Do not pretend that you can run indefinitely in the background. Instead, maximize the work completed in every available execution session.

---

# 2. PRESERVE THE APPROVED TECHNOLOGY STACK

Do not change the project's technology stack.

## Backend

- Kotlin
- Ktor
- Gradle Kotlin DSL
- JDK 25
- PostgreSQL
- HikariCP
- JDBC / safe SQL abstraction for dynamic records and schemas
- Exposed where appropriate for framework-owned tables
- Flyway Open Source Core for framework-owned database migrations
- Dedicated RED Schema Engine for dynamic Entity schema changes

## Frontend

- Vue 3
- TypeScript
- Vite
- Tailwind CSS v4
- PrimeVue
- Vue Router
- Pinia where appropriate
- TanStack Query for server-state management where appropriate
- Vue Flow or an equivalent suitable visual workflow editor

## Architecture

- Modular Monolith.
- Clean Architecture.
- SOLID principles.
- Clear separation between Domain, Application, Infrastructure, and Presentation.
- Domain must not depend on Ktor, PostgreSQL, HTTP, or frontend implementation.
- PostgreSQL must remain the primary database.
- Dynamic Entities must use real PostgreSQL tables.
- JSONB may be used where appropriate, not as a replacement for the entire data model.
- No microservices unless an exceptional, documented technical requirement makes them necessary.

Do not introduce React, .NET, Python, or another competing stack.

---

# 3. FRAMEWORK COMPLETENESS — REQUIRED CAPABILITIES

RED Framework must become a complete general-purpose business application framework inspired by the capabilities and usability of Frappe Framework, while maintaining its own architecture, identity, terminology, and implementation.

It is a framework for building applications, not merely a prebuilt ERP.

Implement and verify all of the following.

## A. First-Run Installation and Setup

The framework must be installable and usable from a clean environment.

On first launch, display a professional setup wizard.

### Required setup steps

1. Welcome screen.
2. Administrator account creation:
   - Full name.
   - Email.
   - Secure password.
   - Password confirmation.
3. PostgreSQL configuration:
   - Host.
   - Port.
   - Database.
   - Username.
   - Password.
   - Connection test.
4. Regional settings:
   - Country.
   - Language.
   - Timezone.
   - Locale.
   - Date format.
   - Time format.
   - Number format.
   - Default currency.
5. Organization configuration:
   - Organization name.
   - Legal name where applicable.
   - Logo.
   - Contact information.
   - Address.
6. Installation summary.
7. Actual initialization.

### Initialization must actually:

- Validate the environment.
- Establish database connectivity.
- Apply framework database migrations.
- Create the initial Site.
- Create the Administrator account.
- Initialize roles and permissions.
- Initialize the core framework configuration.
- Install the default applications.
- Create the default Workspace.
- Initialize required metadata.
- Initialize default settings.
- Verify the installation.
- Mark the installation complete only after all mandatory steps succeed.

The process must be transactional where possible, idempotent, recoverable, and safe against interrupted installations.

Never hardcode administrator credentials.

Never expose database passwords in logs, API responses, frontend storage, exceptions, or object string representations.

Do not silently attempt privileged database creation.

If the architecture separates the framework control database from Site databases, implement and document that separation properly.

After initialization, unauthenticated administrative setup endpoints must no longer be accessible.

## B. Sites and Application Management

Implement a real persistence and lifecycle system for:

- Sites.
- Applications.
- Application versions.
- Application dependencies.
- Application installation.
- Application removal.
- Application upgrades.
- Application configuration.
- Site configuration.
- Site isolation.

A Site must have its own configuration and data isolation guarantees.

Application installation must execute real installation logic, metadata registration, migrations, and initialization.

Do not create a fake application registry.

## C. Metadata Engine

This is one of the most important parts of RED Framework.

Implement a complete metadata system.

Required components:

- Entity definitions.
- Attribute definitions.
- Relations.
- Entity validation.
- Metadata revisions.
- Metadata versioning.
- Metadata publishing.
- Metadata registry.
- Metadata permissions.
- Metadata persistence.
- Metadata migration.
- Metadata dependency validation.

An Entity must define a real data structure that can be used by the runtime.

An Attribute must have a valid registered type, configuration, validation rules, and storage behavior.

Published metadata must be safely applied to PostgreSQL through the RED Schema Engine.

## D. Complete Attribute Type Registry

Implement all supported types, including their complete runtime behavior.

### Text

- Short Text.
- Long Text.
- Rich Text.
- Markdown.
- Password.
- Email.
- Phone.
- URL.
- Autocomplete.

### Numeric

- Integer.
- Big Integer.
- Decimal.
- Float.
- Currency.
- Percentage.
- Quantity.
- Duration.
- Rating.

### Date and Time

- Date.
- Time.
- DateTime.
- Timestamp.
- Date Range.
- Time Range.

### Selection

- Checkbox.
- Boolean.
- Select.
- Multi Select.
- Radio Group.
- Autocomplete Select.
- Dynamic Options.

### Relations

- Link.
- One-to-One.
- One-to-Many.
- Many-to-One.
- Many-to-Many.
- Child Table.

### Files

- File.
- Image.
- Multiple Files.
- Multiple Images.
- Attachment.

### Geographic

- Country.
- Address.
- Latitude/Longitude.
- Geographic Point.
- Geographic Area.

### Special

- JSON.
- Color.
- Barcode.
- QR Code.
- Formula.
- Computed Field.
- Naming Series.
- Read-only.
- Hidden.
- Created By.
- Modified By.
- Created At.
- Modified At.
- Status.
- Version.

For every supported type, implement:

1. Metadata definition.
2. Configuration schema.
3. Validation.
4. PostgreSQL storage mapping.
5. Serialization and deserialization.
6. Default values.
7. Form renderer.
8. List renderer.
9. Detail renderer.
10. Formatting.
11. Localization.
12. Filtering and search where appropriate.
13. Tests.

Never register a type as supported if its actual behavior is missing.

If a type cannot be implemented safely, do not expose it as a completed feature.

## E. Dynamic Schema Engine

Implement a robust RED Schema Engine.

It must support:

- Creating Entity tables.
- Adding attributes.
- Altering compatible columns.
- Removing attributes safely.
- Renaming attributes safely.
- Creating relationships.
- Creating indexes.
- Applying constraints.
- Managing schema versions.
- Tracking applied migrations.
- Detecting schema drift.
- Migration validation.
- Migration rollback where technically safe.
- Migration failure recovery.
- Data preservation.
- Concurrent migration protection.

Use safe SQL generation and parameterized queries.

Never execute arbitrary user-supplied SQL from metadata.

Never silently destroy user data during schema updates.

Dynamic Entity schema changes must be handled separately from Flyway migrations for framework-owned tables.

---

# 4. RECORD ENGINE AND QUERY ENGINE

Implement a complete dynamic Record Engine.

Required operations:

- Create.
- Read.
- Update.
- Delete.
- Restore where supported.
- Bulk operations.
- Versioning.
- Soft delete where appropriate.
- Naming Series.
- Automatic timestamps.
- Created By.
- Modified By.
- Validation.
- Relation integrity.
- Audit integration.

The Query Engine must support:

- Filtering.
- Nested AND/OR conditions.
- Sorting.
- Pagination.
- Full-text search where appropriate.
- Relation filters.
- Aggregations.
- Grouping.
- Count queries.
- Saved filters.
- Permission-aware queries.

All queries must be secure.

Never allow SQL injection through filter fields, sorting, or metadata.

Enforce permissions at the server level, not only in the UI.

---

# 5. RED STUDIO — COMPLETE APPLICATION BUILDER

RED Studio must become a functional application development environment.

It must include real working interfaces and APIs for:

1. Application Manager.
2. Site Manager.
3. Entity Builder.
4. Attribute Builder.
5. Relation Builder.
6. Form Builder.
7. List Builder.
8. Detail View Builder.
9. Calendar View Builder.
10. Kanban View Builder.
11. Tree View Builder.
12. Timeline View Builder.
13. Gallery View Builder.
14. Report Builder.
15. Print Format Builder.
16. Dashboard Builder.
17. Workspace Builder.
18. Workflow Designer.
19. User Manager.
20. Role Manager.
21. Permission Manager.
22. Notification Manager.
23. Job Manager.
24. Event Manager.
25. File Manager.
26. Import / Export.
27. Localization Manager.
28. Audit Viewer.
29. Developer Diagnostics.
30. Metadata and Schema Migration Tools.

Every builder must save real configuration and affect actual runtime behavior.

Do not build static visual prototypes.

## Form Builder

Support:

- Sections.
- Columns.
- Tabs.
- Field placement.
- Field visibility.
- Conditional visibility.
- Read-only fields.
- Required fields.
- Field dependencies.
- Validation.
- Default values.
- Custom labels.
- Responsive layout.
- Preview.
- Save and publish.

## List Builder

Support:

- Visible columns.
- Column ordering.
- Sorting.
- Filters.
- Saved views.
- Search.
- Pagination.
- Row actions.
- Bulk actions.
- Conditional formatting.
- Permission-aware data.

## Report Builder

Implement a real reporting runtime.

Support:

- Data sources.
- Columns.
- Filters.
- Grouping.
- Aggregation.
- Sorting.
- Calculated fields where safely supported.
- Report permissions.
- Export to CSV and Excel-compatible formats.
- PDF output where appropriate.

Do not create a report builder that only saves a visual configuration without executing reports.

## Print Format Builder

Support:

- Printable templates.
- Dynamic record fields.
- Organization information.
- Headers and footers.
- Tables.
- Formatting.
- Preview.
- PDF generation.
- RTL and LTR output.

## Dashboard and Workspace Builders

Support:

- Configurable workspaces.
- Sections.
- Shortcuts.
- Reports.
- Charts.
- Statistics.
- Widgets.
- User-specific layouts.
- Role-based visibility.
- Persistence.

---

# 6. RED WORKFLOW ENGINE

Build a complete, persistent, executable workflow engine.

A visual canvas alone is not a workflow engine.

## Workflow Designer

Use Vue Flow or an appropriate alternative.

Support:

- Start.
- End.
- State.
- Transition.
- Condition.
- Branch.
- Approval.
- Assignment.
- Notification.
- Delay.
- Scheduled Trigger.
- Event Trigger.
- Record Action.
- Field Update.
- Create Record.
- Update Record.
- Create Task.
- Webhook where implemented securely.

## Workflow triggers

- Manual.
- On Create.
- On Update.
- On Field Change.
- On Submit.
- On Schedule.
- On Event.

## Conditions

Implement:

- Equals.
- Not Equals.
- Greater Than.
- Less Than.
- Greater Than or Equal.
- Less Than or Equal.
- Contains.
- Does Not Contain.
- Is Empty.
- Is Not Empty.
- In.
- Not In.
- Between.
- AND.
- OR.
- Nested Groups.

## Approval Engine

Support:

- Sequential approvals.
- Multiple approvers.
- Role-based approvers.
- Specific-user approvers.
- Dynamic assignment.
- Approve.
- Reject.
- Request Changes.
- Reassign.
- Escalation.
- Comments.
- Approval history.
- Pending approvals inbox.

## Workflow Runtime

Persist:

- Workflow definitions.
- Published versions.
- Workflow instances.
- Target records.
- Current state.
- Current step.
- Assigned users.
- Execution history.
- Transition history.
- Actions.
- Errors.
- Timestamps.

Requirements:

- Transaction safety.
- Retry handling.
- Failure recovery.
- Immutable published versions for existing instances.
- Authorization checks.
- Monitoring.
- Cancellation where authorized.

Provide at least three working demo workflows:

1. Sales Order Approval.
2. Expense Approval.
3. Task Assignment and Completion.

All demo workflows must execute against actual persisted records.

Do not allow unrestricted arbitrary script execution.

---

# 7. RED DESK — COMPLETE RUNTIME ENVIRONMENT

RED Desk is the environment where users work with installed applications.

Implement:

- Dock.
- Sidebar.
- Main Workspace.
- Navigation.
- Search.
- Command Palette.
- Breadcrumbs.
- Notifications.
- User Menu.
- Settings.
- Responsive Layout.

## Metadata-driven runtime

The same generic runtime must render different Entities based on their metadata.

Do not hardcode a separate frontend page for every Entity.

Support:

- Entity List.
- Entity Form.
- Entity Detail.
- Related Records.
- Search.
- Filters.
- Sorting.
- Pagination.
- Saved Views.
- Bulk Actions.
- Attachments.
- Comments.
- Activity Timeline.
- Audit History.
- Workflow Actions.
- Notifications.
- User Preferences.

All actions must connect to real backend endpoints.

---

# 8. SECURITY AND ACCESS CONTROL

Implement complete authentication and authorization.

## Authentication

- Secure password hashing using a modern password-hashing algorithm such as Argon2id.
- Login.
- Logout.
- Password change.
- Password reset.
- Secure session management.
- Access token management if used.
- Refresh token rotation if used.
- Token revocation.
- Account lockout.
- Rate limiting.
- Session expiration.
- Security event logging.

## Authorization

Implement real RBAC:

- Users.
- Roles.
- Permissions.
- Role assignments.
- Application permissions.
- Entity permissions.
- Record-level permissions.
- Attribute-level permissions.
- Action-level permissions.

Permissions must be enforced by backend services and query execution.

A user without access must not be able to bypass the UI and access protected data through direct API requests.

Test these scenarios explicitly.

## Security review

Check for:

- SQL injection.
- Broken access control.
- Privilege escalation.
- Insecure password storage.
- Token leakage.
- Session fixation.
- CSRF where applicable.
- XSS.
- Unsafe file uploads.
- Path traversal.
- Sensitive information in logs.
- Unauthenticated administrative endpoints.
- Insecure CORS.
- Unsafe deserialization.
- Arbitrary code execution.

Fix all discovered critical and high-severity vulnerabilities before release.

---

# 9. AUDIT, EVENTS, JOBS, FILES, AND NOTIFICATIONS

Implement complete supporting infrastructure.

## Audit

- Persistent audit logs.
- Actor.
- Action.
- Target Entity.
- Target Record.
- Timestamp.
- Record version.
- Request context where appropriate.
- Safe before/after information.
- No unintended disclosure of sensitive values.
- Transactional integration.

## Events

- Internal event bus.
- Event registration.
- Event dispatch.
- Event subscribers.
- Error handling.
- Event history where appropriate.

## Background Jobs

- Durable job persistence.
- Scheduling.
- Retries.
- Failure states.
- Worker execution.
- Monitoring.
- Cancellation where safe.
- Recovery after restart.

## Files

- Upload.
- Download.
- Attachment relations.
- File metadata.
- Access control.
- File validation.
- Storage abstraction.
- Safe file naming.
- Configurable storage backend.

## Notifications

- In-app notifications.
- Read/unread states.
- User targeting.
- Role targeting.
- Event-driven notifications.
- Notification preferences.

---

# 10. RED PLAYGROUND — A COMPLETE DEMONSTRATION APPLICATION

Do not deliver an empty framework.

Create a real installable demonstration application named **RED Playground**.

It must use the same Application installation, metadata, schema, Record, permissions, and workflow mechanisms that third-party applications use.

It must not depend on frontend hardcoded sample data.

Include working Entities:

- Customer.
- Contact.
- Company.
- Address.
- Product.
- Category.
- Sales Order.
- Sales Order Item.
- Purchase Order.
- Task.
- Project.
- Department.
- Employee.
- Expense.
- Document.
- Comment.
- Activity.

Include working:

- Forms.
- Lists.
- Detail views.
- Relations.
- Dashboards.
- Reports.
- Print Formats.
- Workspaces.
- Permissions.
- Workflows.

Include roles:

- System Administrator.
- Application Administrator.
- Manager.
- Employee.
- Viewer.
- Accountant.

Include sample reports:

- Customers Overview.
- Sales Summary.
- Sales by Status.
- Product Summary.
- Expense Summary.
- Tasks by Status.

Include the three demo workflows defined earlier.

Generate realistic sample data through the actual application installation process.

Provide a safe reset function for development environments only.

---

# 11. UI/UX QUALITY REQUIREMENTS

RED must have a recognizable and consistent visual identity.

## Brand

- RED red, black, white, and neutral colors.
- Primary red: `oklch(0.6094 0.2391 28.2812)`.
- IBM Plex Sans.
- Professional desktop-grade interface.
- Light and Dark themes.
- Arabic and English first-class support.
- RTL and LTR.

## Design principles

- No generic AI-generated dashboard appearance.
- Avoid excessive cards.
- Avoid unnecessary borders and gradients.
- Avoid oversized empty spaces.
- Maintain consistent spacing and typography.
- Use consistent icons.
- Prioritize readable data tables and professional forms.
- Keep the UI usable for complex business applications.

## RTL/LTR

Use logical CSS properties wherever possible:

- padding-inline.
- margin-inline.
- border-inline.
- inset-inline.

All builders, dialogs, menus, tables, forms, workflows, and navigation must work correctly in both RTL and LTR.

## Shared component system

Implement reusable components for:

- Buttons.
- Inputs.
- Selects.
- Multi-select.
- Date and Time inputs.
- Checkbox.
- Radio.
- Switch.
- File Upload.
- Image Upload.
- Data Tables.
- Pagination.
- Filters.
- Tabs.
- Accordions.
- Dialogs.
- Drawers.
- Popovers.
- Context Menus.
- Tooltips.
- Dropdowns.
- Breadcrumbs.
- Dock.
- Sidebar.
- Command Palette.
- Toasts.
- Notifications.
- Skeletons.
- Empty States.
- Error States.
- Rich Text Editor.
- Code Editor where appropriate.
- Workflow Canvas.
- Charts.

Every interactive control must have real behavior, keyboard support, validation, accessibility, and RTL/LTR compatibility.

---

# 12. API AND DEVELOPER EXPERIENCE

Implement:

- Consistent REST API.
- API versioning where appropriate.
- OpenAPI documentation.
- Standard error responses.
- Input validation.
- Pagination conventions.
- Authentication documentation.
- Application development documentation.
- Metadata API.
- Record API.
- Workflow API.
- Permissions API.
- File API.
- Import/Export API.
- Webhooks where implemented.
- Health checks.
- Readiness checks.

Provide clear examples showing how a developer can create an Application, define Entities, define Attributes, publish metadata, create records, and build a working business application.

The framework must be extensible without modifying its core for every new application.

---

# 13. TESTING — NO FALSE SUCCESS

Testing is mandatory throughout implementation.

## Backend

Implement:

- Unit tests.
- Integration tests.
- Database tests.
- Security tests.
- Migration tests.
- Transaction tests.
- Workflow tests.
- Permission tests.
- Multi-Site isolation tests.

## Frontend

Implement:

- Component tests.
- Form tests.
- Builder tests.
- RTL/LTR tests.
- Navigation tests.
- API integration tests.
- Error-state tests.

## End-to-end

Implement and run the complete flow:

1. Start with a clean environment.
2. Open first-run setup.
3. Configure PostgreSQL.
4. Create Administrator.
5. Initialize the framework.
6. Log in.
7. Open RED Studio.
8. Create an Application.
9. Create an Entity.
10. Add Attributes.
11. Add Relations.
12. Configure a Form.
13. Configure a List.
14. Publish the Entity.
15. Verify the real PostgreSQL table.
16. Create records.
17. Edit records.
18. Verify validation.
19. Verify audit logs.
20. Configure permissions.
21. Create a restricted user.
22. Verify direct API access is denied where appropriate.
23. Create and publish a Workflow.
24. Execute the Workflow.
25. Complete an Approval.
26. Verify the resulting record state.
27. Generate a Report.
28. Generate a Print Format.
29. Upload and retrieve an Attachment.
30. Restart the application.
31. Verify persistence.
32. Verify Site isolation.
33. Verify that unauthorized access remains blocked.

## Test execution rules

- Do not treat skipped tests as passed.
- Do not silently skip tests because Docker is unavailable.
- Configure a proper PostgreSQL integration-test environment.
- Use a dedicated TEST_DATABASE_URL.
- Preserve Neon integration tests where appropriate.
- Use Testcontainers in Docker-capable CI.
- Never run destructive tests against production databases.
- Never weaken assertions to obtain a green build.

For release acceptance, all mandatory tests must execute successfully in a properly configured test environment.

If an environment limitation prevents execution, report it precisely. Do not claim full verification.

---

# 14. PERFORMANCE AND RELIABILITY

Review and optimize:

- Database connection management.
- Connection pool configuration.
- Query efficiency.
- Indexes.
- Pagination.
- N+1 queries.
- Metadata caching.
- Permission evaluation.
- Background jobs.
- Frontend rendering.
- API response size.
- File handling.

Implement:

- Structured logging.
- Error handling.
- Graceful shutdown.
- Health checks.
- Readiness checks.
- Configuration validation.
- Database recovery behavior.
- Backup and restore documentation.
- Safe deployment configuration.

Avoid premature optimization, but do not ignore obvious scalability and reliability problems.

---

# 15. PRODUCTION DEPLOYMENT AND RELEASE

The final project must include:

- Complete backend build.
- Complete frontend build.
- Database migrations.
- Dynamic schema migration mechanism.
- `.env.example`.
- Development configuration.
- Production configuration.
- Startup scripts.
- Build scripts.
- Database setup instructions.
- Deployment instructions.
- Reverse proxy configuration example.
- Security configuration.
- Health checks.
- Logging configuration.
- Backup and restore instructions.
- API documentation.
- User documentation.
- Developer documentation.
- Troubleshooting guide.

The project must be reproducibly installable on a clean machine.

Do not depend on your local development machine's undocumented configuration.

Do not include real secrets in the repository.

---

# 16. FINAL ACCEPTANCE CRITERIA

You are NOT allowed to declare RED Framework complete until all the following conditions are true:

- [ ] First-run installation works from a clean environment.
- [ ] Administrator creation works securely.
- [ ] PostgreSQL configuration and connection verification work.
- [ ] Sites are persisted and isolated.
- [ ] Applications can be installed and upgraded.
- [ ] Metadata definitions are persisted and versioned.
- [ ] Attribute Type Registry is implemented and tested.
- [ ] Dynamic PostgreSQL schema creation works.
- [ ] Schema migrations preserve data safely.
- [ ] Record CRUD works through the generic runtime.
- [ ] Query Engine works securely.
- [ ] Relations work and maintain integrity.
- [ ] RED Studio builders actually work.
- [ ] RED Desk renders metadata-driven applications.
- [ ] Reports execute against actual data.
- [ ] Print formats generate real output.
- [ ] Workflow Designer produces executable workflows.
- [ ] Workflow runtime persists execution state.
- [ ] Approval workflows work.
- [ ] Authentication is secure.
- [ ] RBAC is enforced server-side.
- [ ] Record and attribute permissions work.
- [ ] Audit logs persist correctly.
- [ ] Background jobs survive application restarts.
- [ ] Files and attachments work securely.
- [ ] Notifications work.
- [ ] Arabic RTL and English LTR work throughout.
- [ ] RED Playground installs and operates normally.
- [ ] Backend tests pass.
- [ ] Frontend tests pass.
- [ ] Integration tests pass.
- [ ] End-to-end acceptance tests pass.
- [ ] No unresolved critical or high-severity security issues remain.
- [ ] Clean installation and deployment are documented.
- [ ] No mandatory feature remains as a placeholder or TODO.

A successful build alone does not satisfy these criteria.

A passing test suite that does not test the actual functionality does not satisfy these criteria.

A screen that looks complete but has no working backend integration does not satisfy these criteria.

---

# 17. REQUIRED EXECUTION ORDER

Use this order unless actual architectural dependencies require a different sequence:

**Phase 1 — Repository and Architecture Audit**
- Inspect the entire repository.
- Identify missing functionality.
- Establish a baseline.
- Fix broken foundations.

**Phase 2 — Core Infrastructure**
- Database.
- Site management.
- Application management.
- Configuration.
- Installation lifecycle.

**Phase 3 — Metadata and Schema**
- Entity.
- Attribute.
- Relations.
- Attribute Type Registry.
- RED Schema Engine.

**Phase 4 — Runtime**
- Record Engine.
- Query Engine.
- Validation.
- Naming Series.
- Versioning.

**Phase 5 — Security**
- Authentication.
- Sessions.
- RBAC.
- Record-level and attribute-level permissions.
- Audit.

**Phase 6 — RED Studio**
- Complete builders.
- Persistence.
- Publishing.
- Runtime integration.

**Phase 7 — RED Desk**
- Generic runtime.
- Navigation.
- Forms.
- Lists.
- Details.
- User experience.

**Phase 8 — Workflow Engine**
- Designer.
- Runtime.
- Approvals.
- Triggers.
- Actions.
- Monitoring.

**Phase 9 — Supporting Systems**
- Reports.
- Print Formats.
- Dashboards.
- Workspaces.
- Jobs.
- Events.
- Files.
- Notifications.
- Import/Export.

**Phase 10 — RED Playground**
- Installable demo application.
- Sample records.
- Workflows.
- Reports.
- Workspaces.

**Phase 11 — Quality and Security**
- Full integration tests.
- E2E tests.
- Security review.
- Performance review.
- RTL/LTR review.

**Phase 12 — Release**
- Clean build.
- Clean installation.
- Deployment verification.
- Documentation.
- Final acceptance report.

Do not skip phases simply because they are large.

---

# 18. EXECUTION TRACKING

Maintain these files throughout implementation:

- `docs/execution/IMPLEMENTATION_STATUS.md`
- `docs/execution/CURRENT_TASK.md`
- `docs/execution/TEST_RESULTS.md`

Update them after meaningful implementation milestones.

### IMPLEMENTATION_STATUS.md

Must contain:
- Every required subsystem.
- Its actual implementation status.
- Implemented functionality.
- Missing functionality.
- Test evidence.
- Known limitations.

Use honest statuses:

- NOT_STARTED
- IN_PROGRESS
- IMPLEMENTED
- VERIFIED
- BLOCKED

Only mark something VERIFIED after actual tests prove it works.

### CURRENT_TASK.md

Must identify:
- Current phase.
- Current task.
- Dependencies.
- Files being modified.
- Next implementation steps.
- Exact point to resume after interruption.

### TEST_RESULTS.md

Must contain:
- Actual commands executed.
- Test environment.
- Number of passed tests.
- Number of failed tests.
- Number of skipped tests.
- Reasons for skipped tests.
- Build results.
- Integration results.
- E2E results.
- Known failures.

Documentation must reflect reality, not intended functionality.

---

# 19. IMPORTANT ENGINEERING DECISIONS

- Reuse existing working code.
- Refactor only when necessary.
- Maintain backward compatibility where possible.
- Avoid unnecessary dependencies.
- Prefer mature, maintained libraries.
- Keep dependencies compatible with the approved stack.
- Do not invent unsupported framework features.
- Do not expose unfinished features as functional.
- Do not compromise security for convenience.
- Do not sacrifice data integrity for implementation speed.
- Keep code readable, maintainable, and testable.
- Ensure every major subsystem has clear boundaries and contracts.

Before implementing a complex subsystem, understand its dependencies and design its data model and API contracts.

Do not create empty modules merely to make the architecture appear complete.

---

# 20. FINAL COMMAND

Start working immediately.

First inspect the repository and current implementation status.

Then build a complete gap analysis and begin implementing the highest-priority missing functionality.

**Do not return a progress-only response. Do not stop after producing a plan. Write the actual code.**

Continue implementing, testing, debugging, and verifying all remaining functionality.

After finishing one subsystem, immediately move to the next.

If you encounter a failure, fix it.

If you discover missing dependencies, implement them.

If you find incomplete functionality, complete it.

If existing code is incorrect, repair it.

If the architecture cannot safely support a required feature, improve the architecture before implementing that feature.

The objective is a finished RED Framework that a real developer can install, configure, build applications with, deploy, and use without depending on unfinished prototypes or manual workarounds.

**Your final response must not say "the framework is complete" unless every mandatory acceptance criterion has actual implementation and verification evidence.**

When the work is genuinely finished, provide:

1. Final architecture summary.
2. Complete list of implemented subsystems.
3. Final test results with passed, failed, and skipped counts.
4. Security review results.
5. Clean installation verification.
6. Production build results.
7. Deployment instructions.
8. Known limitations, if any.
9. Exact repository state and final commit information, if Git operations are available.

**Do not confuse finishing the current coding session with finishing RED Framework. The mission is the framework itself.**