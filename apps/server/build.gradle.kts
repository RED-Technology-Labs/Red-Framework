plugins {
    id("buildsrc.convention.kotlin-jvm")
    application
}

dependencies {
    implementation(project(":infrastructure:database-postgres"))
    implementation(project(":infrastructure:database-migrations"))
    implementation(project(":core:application"))
    implementation(project(":core:identity"))
    implementation(project(":core:site"))
    implementation(project(":core:metadata"))
    implementation(project(":core:schema"))
    implementation(project(":core:records"))
    implementation(libs.kotlinxSerialization)
    implementation(libs.ktorServerCore)
    implementation(libs.ktorServerNetty)
    testImplementation(libs.ktorServerTestHost)
    testImplementation(kotlin("test"))
}

application { mainClass.set("red.framework.server.ServerKt") }
