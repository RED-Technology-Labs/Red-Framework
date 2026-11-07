plugins { id("buildsrc.convention.kotlin-jvm") }
dependencies {
    api(project(":core:metadata"))
    testImplementation(kotlin("test"))
}
