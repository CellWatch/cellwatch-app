import java.util.Properties
import java.net.URI

plugins {
    id("com.android.application")
    id("com.google.android.gms.oss-licenses-plugin")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.kapt")
    id("com.google.relay") version "0.3.09"
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
}

val cellwatchPropertiesFile = rootProject.file("cellwatch.properties")
val cellwatchProperties = Properties().apply {
    if (cellwatchPropertiesFile.exists()) {
        cellwatchPropertiesFile.inputStream().use { load(it) }
    }
}

fun propValue(name: String): String? = cellwatchProperties[name]?.toString()?.trim()?.removeSurrounding("\"")

fun requireProp(name: String): String =
    propValue(name) ?: throw GradleException("Missing required property '$name' in cellwatch.properties")

fun quoteBuildConfig(value: String): String = "\"" + value.replace("\"", "\\\"") + "\""

fun isLocalSupabaseUrl(url: String): Boolean {
    val uri = runCatching { URI(url) }.getOrNull() ?: return false
    val host = uri.host?.lowercase() ?: return false
    if (uri.scheme !in setOf("http", "https")) return false
    return host == "localhost" ||
        host == "127.0.0.1" ||
        host == "::1" ||
        host == "10.0.2.2" ||
        host == "host.docker.internal"
}

val localSupabaseUrl = propValue("SUPABASE_LOCAL_URL") ?: "http://10.0.2.2:54321"
val localSupabaseApiKey = propValue("SUPABASE_LOCAL_API_KEY")
    ?: "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZS1kZW1vIiwicm9sZSI6ImFub24iLCJleHAiOjE5ODM4MTI5OTZ9.CRXP1A7WOeoJeXxjNni43kdQwgnWNReilDMblYTn_I0"

if (!isLocalSupabaseUrl(localSupabaseUrl)) {
    throw GradleException(
        "SUPABASE_LOCAL_URL must point to a local Supabase instance for debug/test builds. " +
            "Got '$localSupabaseUrl'. Allowed hosts: localhost, 127.0.0.1, ::1, 10.0.2.2, host.docker.internal."
    )
}

android {
    namespace = "edu.gatech.cc.cellwatch"
    compileSdk = 34

    defaultConfig {
        applicationId = "edu.gatech.cc.cellwatch"
        minSdk = 29
        targetSdk = 34
        versionCode = 8
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += listOf("armeabi", "armeabi-v7a", "arm64-v8a")
        }

        javaCompileOptions {
            annotationProcessorOptions {
                // If you keep annotation processors elsewhere, keep this mapping here.
                // Room schema location is still useful for kapt as well.
                arguments["room.schemaLocation"] = "$projectDir/schemas"
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            buildConfigField("String", "TCP_TUPLE_URL", quoteBuildConfig(requireProp("TCP_TUPLE_URL")))
            buildConfigField("String", "SUPABASE_URL", quoteBuildConfig(localSupabaseUrl))
            buildConfigField("String", "SUPABASE_API_KEY", quoteBuildConfig(localSupabaseApiKey))
            buildConfigField("String", "MSAK_SERVER_ENV", quoteBuildConfig(requireProp("MSAK_SERVER_ENV")))
            buildConfigField("String", "MSAK_LOCAL_SERVER_HOST", quoteBuildConfig(requireProp("MSAK_LOCAL_SERVER_HOST")))
            buildConfigField("Boolean", "MSAK_LOCAL_SERVER_SECURE", requireProp("MSAK_LOCAL_SERVER_SECURE"))
            buildConfigField("Integer", "MSAK_LATENCY_PORT", requireProp("MSAK_LOCAL_LATENCY_PORT"))
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            buildConfigField("String", "TCP_TUPLE_URL", quoteBuildConfig(requireProp("TCP_TUPLE_URL")))
            buildConfigField("String", "SUPABASE_URL", quoteBuildConfig(requireProp("SUPABASE_URL")))
            buildConfigField("String", "SUPABASE_API_KEY", quoteBuildConfig(requireProp("SUPABASE_API_KEY")))
            buildConfigField("String", "MSAK_SERVER_ENV", "\"prod\"")
            buildConfigField("String", "MSAK_LOCAL_SERVER_HOST", "\"\"")
            buildConfigField("Boolean", "MSAK_LOCAL_SERVER_SECURE", "true")
            buildConfigField("Integer", "MSAK_LATENCY_PORT", requireProp("MSAK_LATENCY_PORT"))
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/LICENSE.md",
                "META-INF/LICENSE-notice.md"
            )
        }
    }

    lint {
        abortOnError = false
    }
}

dependencies {
    implementation(project(":shared"))

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.crashlytics)

    implementation(libs.commons.io)
    implementation(libs.conscrypt.android)
    implementation(libs.androidx.test.ext.junit.ktx)
    implementation(libs.play.services.maps)
    androidTestImplementation(libs.mockwebserver)
    androidTestImplementation(libs.mockk.android)
    androidTestImplementation(libs.mockk.agent)

// ViewModel
    implementation(libs.lifecycle.viewmodel)
// LiveData
    implementation(libs.lifecycle.livedata)

// Keep only the modern core-ktx
    implementation(libs.core.ktx)
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.constraintlayout)
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)
    implementation(libs.okhttp)
    implementation(libs.gson)
    implementation(files("../lib/AndroidPing.aar"))
    implementation(files("../lib/DeviceInformation-release.aar"))
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.espresso.core)

    implementation(libs.supabase)
    implementation(libs.ktor.client.cio)
    implementation(libs.serialization.json)

    implementation(libs.easydeviceinfo)
    implementation(libs.device.names)
    implementation(libs.play.services.location)
    implementation(libs.speedviewlib)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    kapt(libs.room.compiler)
// optional extras
    implementation(libs.room.rxjava2)
    implementation(libs.room.rxjava3)
    implementation(libs.room.guava)
    testImplementation(libs.room.testing)
    implementation(libs.room.paging)

    implementation(libs.datastore)
    implementation(libs.h3)
    implementation(libs.commons.validator)
    implementation(libs.oss.licenses)
    implementation(libs.flexbox)

    implementation(libs.play.services.maps)

    // core
    implementation(libs.mapbox.maps)

    implementation(libs.mapbox.search)
    implementation(libs.mapbox.search.ui)

    implementation(libs.cellwatch.msak)
}
