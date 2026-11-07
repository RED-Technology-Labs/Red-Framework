# Current task

The initial single-Site API flow now passed against the dedicated Neon test database: setup, application and Entity creation, publication, record writes, naming, and restricted-user READ/CREATE grants. Studio builds, but a browser end-to-end run is still outstanding. The next source-code task beyond this initial local-use slice is Site provisioning and isolation, followed by complete authorization. Do not allow more than one Site with the current single-database mapping. Keep the server bound to localhost until full RBAC, login throttling, recovery, production cookie configuration, and Site boundaries are in place.

Acceptance for the next slice: trusted Site-to-database mapping persists without exposing credentials, Site operations use an isolated database, cross-Site access is denied by backend tests, and Studio creates/opens a Site through real authenticated APIs.
