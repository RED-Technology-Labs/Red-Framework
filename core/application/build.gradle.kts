plugins { id("buildsrc.convention.kotlin-jvm") }

dependencies {
    api(project(":core:kernel"))
    testImplementation(kotlin("test"))
}
