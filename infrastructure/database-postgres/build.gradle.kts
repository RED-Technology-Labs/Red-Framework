plugins { id("buildsrc.convention.kotlin-jvm") }

dependencies {
    implementation(project(":core:metadata"))
    implementation(project(":core:application"))
    implementation(project(":core:identity"))
    implementation(project(":core:site"))
    implementation(project(":core:schema"))
    implementation(project(":core:records"))
    implementation(libs.hikari)
    implementation(libs.kotlinxSerialization)
    runtimeOnly(libs.postgresql)
    testImplementation(kotlin("test"))
    testImplementation(libs.testcontainersPostgresql)
    testImplementation(libs.testcontainersJunit)
    testRuntimeOnly(libs.postgresql)
}
