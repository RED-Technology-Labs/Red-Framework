// The code in this file is a convention plugin - a Gradle mechanism for sharing reusable build logic.
// `buildSrc` is a Gradle-recognized directory and every plugin there will be easily available in the rest of the build.
package buildsrc.convention

import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    // Apply the Kotlin JVM plugin to add support for Kotlin in JVM projects.
    kotlin("jvm")
}

kotlin {
    // Compile and test with JDK 25 even if the Gradle daemon runs on another JDK.
    jvmToolchain(25)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_25)
    }
}

tasks.withType<Test>().configureEach {
    // Configure all test Gradle tasks to use JUnitPlatform.
    useJUnitPlatform()

    // External database tests must never reuse a prior skipped or successful result.
    // The URL value is deliberately not a task input because it contains credentials.
    val externalDatabaseConfigured = providers.environmentVariable("TEST_DATABASE_URL")
        .map { it.isNotBlank() }
        .orElse(false)
    inputs.property("externalDatabaseConfigured", externalDatabaseConfigured)
    outputs.cacheIf("External database tests must execute for every configured database") {
        !externalDatabaseConfigured.get()
    }
    outputs.upToDateWhen { !externalDatabaseConfigured.get() }

    // Log information about all test results, not only the failed ones.
    testLogging {
        events(
            TestLogEvent.FAILED,
            TestLogEvent.PASSED,
            TestLogEvent.SKIPPED
        )
    }
}
