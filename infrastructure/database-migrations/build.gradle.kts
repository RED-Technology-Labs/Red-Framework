plugins { id("buildsrc.convention.kotlin-jvm") }

dependencies {
    implementation(libs.flywayCore)
    implementation(libs.flywayPostgresql)
    testImplementation(project(":infrastructure:database-postgres"))
    testImplementation(project(":core:metadata"))
    testImplementation(project(":core:application"))
    testImplementation(project(":core:identity"))
    testImplementation(kotlin("test"))
    testRuntimeOnly(libs.postgresql)
}
