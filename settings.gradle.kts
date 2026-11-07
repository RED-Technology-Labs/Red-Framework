dependencyResolutionManagement {
    repositories { mavenCentral() }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
rootProject.name = "red-framework"
include(":core:kernel")
include(":core:site")
include(":core:application")
include(":core:metadata")
include(":core:attribute-types")
include(":core:identity")
include(":core:schema")
include(":core:records")
include(":infrastructure:database-postgres")
include(":infrastructure:database-migrations")
include(":apps:server")
