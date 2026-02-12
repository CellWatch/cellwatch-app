import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.gradle.api.tasks.testing.Test
import java.io.File

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {

    // Use JDK 17 toolchain for Kotlin
    jvmToolchain(17)

// Target declarations - add or remove as needed below. These define
// which platforms this KMP module supports.
// See: https://kotlinlang.org/docs/multiplatform-discover-project.html#targets
    androidTarget()
    jvm()

// For iOS targets, this is also where you should
// configure native binary output. For more information, see:
// https://kotlinlang.org/docs/multiplatform-build-native-binaries.html#build-xcframeworks

// A step-by-step guide on how to include this library in an XCode
// project can be found here:
// https://developer.android.com/kotlin/multiplatform/migrate
    val xcfName = "sharedKit"

    iosX64 {
        binaries.framework {
            baseName = xcfName
        }
    }

    iosArm64 {
        binaries.framework {
            baseName = xcfName
        }
    }

    iosSimulatorArm64 {
        binaries.framework {
            baseName = xcfName
        }
    }


// Source set declarations.
// Declaring a target automatically creates a source set with the same name. By default, the
// Kotlin Gradle Plugin creates additional source sets that depend on each other, since it is
// common to share sources between related targets.
// See: https://kotlinlang.org/docs/multiplatform-hierarchy.html
    sourceSets {

        commonMain {
            dependencies {
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.datetime)
                implementation(libs.serialization.core)
                implementation(libs.serialization.json)
                implementation(libs.supabase)
                // Add KMP dependencies here
                implementation(libs.sqldelight.runtime)
                implementation(libs.sqldelight.coroutines)
                implementation(libs.benasher.uuid)
                implementation(libs.cryptography.core)
                implementation("edu.gatech.cc.cellwatch:msak-client-kmp:0.2.0")
            }
        }

        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }

        androidMain {
            dependencies {
                // Add Android-specific dependencies here. Note that this source set depends on
                // commonMain by default and will correctly pull the Android artifacts of any KMP
                // dependencies declared in commonMain.
                implementation(libs.kotlinx.coroutines.android)
                implementation(libs.sqldelight.android.driver)
                implementation(libs.cryptography.provider.jdk)
            }
        }

        val androidUnitTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.junit)
                implementation(libs.robolectric)
                implementation(libs.sqldelight.android.driver)
                implementation(libs.androidx.test.core)
            }
        }

        val androidInstrumentedTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.androidx.test.junit)
                implementation(libs.androidx.runner)
                implementation(libs.androidx.test.core)
            }
        }

        iosMain {
            dependencies {
                // Add iOS-specific dependencies here. This a source set created by Kotlin Gradle
                // Plugin (KGP) that each specific iOS target (e.g., iosX64) depends on as
                // part of KMP’s default source set hierarchy. Note that this source set depends
                // on common by default and will correctly pull the iOS artifacts of any
                // KMP dependencies declared in commonMain.
                implementation(libs.sqldelight.native.driver)
                implementation(libs.cryptography.provider.openssl3.prebuilt)
            }
        }
        iosTest {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.sqldelight.native.driver)
            }
        }

        val jvmTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.sqldelight.sqlite.driver)
            }
        }

        jvmMain {
            dependencies {
                implementation(libs.cryptography.provider.jdk)
                implementation(libs.ktor.client.cio)
            }
        }
    }

}

android {
    namespace = "edu.gatech.cc.cellwatch"
    compileSdk = 35
    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

sqldelight {
    databases {
        create("CellwatchDatabase") {
            packageName.set("edu.gatech.cc.cellwatch.db")
        }
    }
}

val iosSimulatorResultsDir = layout.buildDirectory.dir("test-results/iosSimulatorArm64Test")

// Ensure stale iOS test XMLs do not mask a skipped/non-executed run.
tasks.named("iosSimulatorArm64Test") {
    doFirst {
        delete(iosSimulatorResultsDir)
    }
}

tasks.register("verifyIosSimulatorArm64Results") {
    description = "Fails if iOS simulator tests did not execute any test cases."
    group = "verification"
    dependsOn("iosSimulatorArm64Test")

    doLast {
        val resultsDir = iosSimulatorResultsDir.get().asFile
        val xmlFiles = resultsDir
            .listFiles { file: File -> file.isFile && file.extension == "xml" }
            ?.toList()
            .orEmpty()

        if (xmlFiles.isEmpty()) {
            throw GradleException(
                "iOS simulator tests produced no XML results at ${resultsDir.absolutePath}. " +
                    "Treating this as a failure."
            )
        }

        val testsRegex = Regex("""tests="(\d+)"""")
        val totalExecuted = xmlFiles.sumOf { file ->
            testsRegex.find(file.readText())?.groupValues?.get(1)?.toIntOrNull() ?: 0
        }

        if (totalExecuted <= 0) {
            throw GradleException(
                "iOS simulator tests reported zero executed tests. Treating this as a failure."
            )
        }
    }
}

tasks.register("verifyAllPlatforms") {
    description = "Strict cross-platform verification (Android unit, JVM, iOS simulator)."
    group = "verification"
    dependsOn(
        "testDebugUnitTest",
        "jvmTest",
        "verifyIosSimulatorArm64Results",
    )
}

tasks.register("verifyAndroidEmulator") {
    description = "Runs Android instrumentation tests on a connected emulator/device."
    group = "verification"
    dependsOn("connectedDebugAndroidTest")
}

tasks.register("verifyLightweightPlatforms") {
    description = "Tier 1: fast checks (Android unit + JVM + iOS simulator K/N tests)."
    group = "verification"
    dependsOn("verifyAllPlatforms")
}

tasks.named<Test>("jvmTest") {
    exclude("**/*LocalSupabaseIntegrationTest*")
}

tasks.register<Test>("jvmLocalSupabaseIntegrationTest") {
    description = "Runs JVM tests that exercise local Docker Supabase integration."
    group = "verification"

    val jvmTest = tasks.named<Test>("jvmTest").get()
    testClassesDirs = jvmTest.testClassesDirs
    classpath = jvmTest.classpath
    include("**/*LocalSupabaseIntegrationTest*")
    shouldRunAfter(jvmTest)
}

tasks.register("verifyLocalSupabaseJvmIntegration") {
    description = "Runs shared JVM local Supabase integration tests (local Docker only)."
    group = "verification"
    dependsOn("jvmLocalSupabaseIntegrationTest")
}

tasks.register("verifyAndroidPublicMsakLocalSupabaseSmoke") {
    description = "Tier 2: Android smoke for public MSAK + local Supabase runtime profile."
    group = "verification"
    doLast {
        exec {
            commandLine(
                "./gradlew",
                ":androidTestApp:testDebugUnitTest",
                "--tests",
                "edu.gatech.cc.cellwatch.androidtestapp.PublicMsakLocalSupabaseSmokeTest",
            )
            environment("CELLWATCH_RUN_PUBLIC_MSAK_LOCAL_SUPABASE_SMOKE", "1")
            workingDir = rootProject.projectDir
        }
    }
}

tasks.register("verifyAndroidLocalMsakPhase3Smoke") {
    description = "Tier 2: Android smoke for local MSAK sequence path."
    group = "verification"
    doLast {
        exec {
            commandLine(
                "./gradlew",
                ":androidTestApp:testDebugUnitTest",
                "--tests",
                "edu.gatech.cc.cellwatch.androidtestapp.LocalMsakPhase3SequenceSmokeTest",
            )
            environment("CELLWATCH_RUN_LOCAL_MSAK_SMOKE", "1")
            workingDir = rootProject.projectDir
        }
    }
}

tasks.register("verifyAndroidFailureStatusSmoke") {
    description = "Tier 2: Android smoke for surfaced failure status in shared sync harness path."
    group = "verification"
    doLast {
        exec {
            commandLine(
                "./gradlew",
                ":androidTestApp:testDebugUnitTest",
                "--tests",
                "edu.gatech.cc.cellwatch.androidtestapp.AndroidFailureStatusSmokeTest",
            )
            environment("CELLWATCH_RUN_ANDROID_FAILURE_STATUS_SMOKE", "1")
            workingDir = rootProject.projectDir
        }
    }
}

tasks.register("verifyIosHostedKeychain") {
    description = "Tier 2: iOS host-app Keychain tests (requires an Xcode project/test target)."
    group = "verification"
    val projectPath = rootProject.file("iosSharedIntegrationHost/iosSharedIntegrationHost.xcodeproj")
    doFirst {
        if (!projectPath.exists()) {
            throw GradleException(
                "Missing iOS host test project at ${projectPath.absolutePath}."
            )
        }
    }
    doLast {
        exec {
            commandLine(
                "xcodebuild",
                "-project",
                projectPath.absolutePath,
                "-scheme",
                "iosSharedIntegrationHost",
                "-destination",
                "platform=iOS Simulator,name=iPhone 17 Pro,OS=26.2",
                "test",
            )
            workingDir = rootProject.projectDir
        }
    }
}

tasks.register("verifyIosTestAppHosted") {
    description = "Tier 2: iOS host-app parity tests in iosTestApp (Keychain + sync harness behaviors)."
    group = "verification"
    dependsOn("refreshIosSimulatorCurrentFramework")
    val projectPath = rootProject.file("iosTestApp/iosTestApp.xcodeproj")
    doFirst {
        if (!projectPath.exists()) {
            throw GradleException(
                "Missing iOS hosted test project at ${projectPath.absolutePath}."
            )
        }
    }
    doLast {
        exec {
            commandLine(
                "xcodebuild",
                "-project",
                projectPath.absolutePath,
                "-scheme",
                "iosTestApp",
                "-destination",
                "platform=iOS Simulator,name=iPhone 17 Pro,OS=26.2",
                "test",
            )
            workingDir = rootProject.projectDir
        }
    }
}

tasks.register("verifyIosTestAppHostedLocalMsakSmoke") {
    description = "Tier 2: iOS hosted local-MSAK smoke test path in iosTestApp."
    group = "verification"
    dependsOn("refreshIosSimulatorCurrentFramework")
    val projectPath = rootProject.file("iosTestApp/iosTestApp.xcodeproj")
    doFirst {
        if (!projectPath.exists()) {
            throw GradleException(
                "Missing iOS hosted test project at ${projectPath.absolutePath}."
            )
        }
    }
    doLast {
        val marker = file("/tmp/cellwatch-ios-local-msak-smoke-required")
        marker.writeText("1\n")
        try {
            exec {
                commandLine(
                    "xcodebuild",
                    "-project",
                    projectPath.absolutePath,
                    "-scheme",
                    "iosTestAppLocalMsakSmoke",
                    "-destination",
                    "platform=iOS Simulator,name=iPhone 17 Pro,OS=26.2",
                    "-only-testing:iosTestAppTests/LocalMsakPhase3HostedTests/testHostedLocalMsakPhase3Sequence_whenEnabled",
                    "test",
                )
                workingDir = rootProject.projectDir
            }
        } finally {
            marker.delete()
        }
    }
}

tasks.register("verifyIosTestAppHostedPublicMsakLocalSupabaseSmoke") {
    description = "Tier 2: iOS hosted smoke for public MSAK + local Supabase runtime profile."
    group = "verification"
    dependsOn("refreshIosSimulatorCurrentFramework")
    val projectPath = rootProject.file("iosTestApp/iosTestApp.xcodeproj")
    doFirst {
        if (!projectPath.exists()) {
            throw GradleException(
                "Missing iOS hosted test project at ${projectPath.absolutePath}."
            )
        }
    }
    doLast {
        val marker = file("/tmp/cellwatch-ios-public-msak-local-supabase-smoke-required")
        marker.writeText("1\n")
        try {
            exec {
                commandLine(
                    "xcodebuild",
                    "-project",
                    projectPath.absolutePath,
                    "-scheme",
                    "iosTestApp",
                    "-destination",
                    "platform=iOS Simulator,name=iPhone 17 Pro,OS=26.2",
                    "-only-testing:iosTestAppTests/PublicMsakLocalSupabaseHostedTests/testHostedPublicMsak_withLocalSupabaseProfile_whenEnabled",
                    "test",
                )
                workingDir = rootProject.projectDir
            }
        } finally {
            marker.delete()
        }
    }
}

tasks.register("verifyIosTestAppHostedFailureStatusSmoke") {
    description = "Tier 2: iOS hosted smoke for surfaced failure status in shared sync harness path."
    group = "verification"
    val projectPath = rootProject.file("iosTestApp/iosTestApp.xcodeproj")
    doFirst {
        if (!projectPath.exists()) {
            throw GradleException("Missing iOS hosted test app project at ${projectPath.absolutePath}.")
        }
        val marker = file("/tmp/cellwatch-ios-failure-status-smoke-required")
        marker.writeText("required")
    }
    doLast {
        try {
            exec {
                commandLine(
                    "xcodebuild",
                    "-project",
                    projectPath.absolutePath,
                    "-scheme",
                    "iosTestApp",
                    "-destination",
                    "platform=iOS Simulator,name=iPhone 17 Pro,OS=26.2",
                    "-only-testing:iosTestAppTests/SyncHarnessParityTests/testHostedFailureSurface_forInvalidSupabaseCredentials_whenEnabled",
                    "test",
                )
                workingDir = rootProject.projectDir
            }
        } finally {
            file("/tmp/cellwatch-ios-failure-status-smoke-required").delete()
        }
    }
}

// Hosted iOS simulator tasks share runtime state (simulator process, keychain scope, local services).
// Keep them serialized to avoid flaky failures when Gradle runs tasks in parallel.
tasks.named("verifyIosTestAppHosted") {
    mustRunAfter("verifyIosHostedKeychain")
}
tasks.named("verifyIosTestAppHostedLocalMsakSmoke") {
    mustRunAfter("verifyIosTestAppHosted")
}
tasks.named("verifyIosTestAppHostedPublicMsakLocalSupabaseSmoke") {
    mustRunAfter("verifyIosTestAppHostedLocalMsakSmoke")
}
tasks.named("verifyIosTestAppHostedFailureStatusSmoke") {
    mustRunAfter("verifyIosTestAppHostedPublicMsakLocalSupabaseSmoke")
}

tasks.register("verifyIosHostedTier2Sequential") {
    description = "Tier 2: run all hosted iOS checks sequentially to avoid simulator concurrency flake."
    group = "verification"
    dependsOn(
        "verifyIosHostedKeychain",
        "verifyIosTestAppHosted",
        "verifyIosTestAppHostedLocalMsakSmoke",
        "verifyIosTestAppHostedPublicMsakLocalSupabaseSmoke",
        "verifyIosTestAppHostedFailureStatusSmoke",
    )
}

tasks.register("verifyPhase3Tier2FailureMatrix") {
    description = "Phase 3 Tier 2 matrix: local/public MSAK + local Supabase + failure-status smoke checks."
    group = "verification"
    dependsOn(
        "verifyAndroidLocalMsakPhase3Smoke",
        "verifyAndroidPublicMsakLocalSupabaseSmoke",
        "verifyAndroidFailureStatusSmoke",
        "verifyIosTestAppHostedLocalMsakSmoke",
        "verifyIosTestAppHostedPublicMsakLocalSupabaseSmoke",
        "verifyIosTestAppHostedFailureStatusSmoke",
    )
}

tasks.register("refreshIosSimulatorCurrentFramework") {
    description = "Refreshes sharedKit.framework at iosSimulatorArm64/Current from latest debug framework output."
    group = "verification"
    dependsOn("linkDebugFrameworkIosSimulatorArm64")
    doLast {
        val sourceFramework = rootProject.file("shared/build/bin/iosSimulatorArm64/debugFramework/sharedKit.framework")
        val currentDir = rootProject.file("shared/build/bin/iosSimulatorArm64/Current")
        val targetFramework = rootProject.file("shared/build/bin/iosSimulatorArm64/Current/sharedKit.framework")
        if (!sourceFramework.exists()) {
            throw GradleException("Missing debug framework at ${sourceFramework.absolutePath}")
        }
        delete(currentDir)
        mkdir(currentDir)
        copy {
            from(sourceFramework.parentFile)
            include("sharedKit.framework/**")
            into(currentDir)
        }
        check(targetFramework.exists()) {
            "Failed to create framework at ${targetFramework.absolutePath}"
        }
    }
}

tasks.register("verifyRealisticPlatforms") {
    description = "Tier 2: realistic platform checks (Android emulator/device + iOS hosted app tests)."
    group = "verification"
    dependsOn(
        "verifyAndroidEmulator",
        "verifyIosTestAppHosted",
    )
}
