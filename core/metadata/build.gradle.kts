plugins { id("buildsrc.convention.kotlin-jvm") }

dependencies {
    api(project(":core:kernel"))
    api(project(":core:attribute-types"))
    testImplementation(kotlin("test"))
}
