import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
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
