import java.util.Properties

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

            buildConfigField("String", "TCP_TUPLE_URL", cellwatchProperties["TCP_TUPLE_URL"] as String)
            buildConfigField("String", "SUPABASE_URL", cellwatchProperties["SUPABASE_LOCAL_URL"] as String)
            buildConfigField("String", "SUPABASE_API_KEY", cellwatchProperties["SUPABASE_LOCAL_API_KEY"] as String)
            buildConfigField("String", "MSAK_SERVER_ENV", cellwatchProperties["MSAK_SERVER_ENV"] as String)
            buildConfigField("String", "MSAK_LOCAL_SERVER_HOST", cellwatchProperties["MSAK_LOCAL_SERVER_HOST"] as String)
            buildConfigField("Boolean", "MSAK_LOCAL_SERVER_SECURE", cellwatchProperties["MSAK_LOCAL_SERVER_SECURE"] as String)
            buildConfigField("Integer", "MSAK_LATENCY_PORT", cellwatchProperties["MSAK_LOCAL_LATENCY_PORT"] as String)
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            buildConfigField("String", "TCP_TUPLE_URL", cellwatchProperties["TCP_TUPLE_URL"] as String)
            buildConfigField("String", "SUPABASE_URL", cellwatchProperties["SUPABASE_URL"] as String)
            buildConfigField("String", "SUPABASE_API_KEY", cellwatchProperties["SUPABASE_API_KEY"] as String)
            buildConfigField("String", "MSAK_SERVER_ENV", "\"prod\"")
            buildConfigField("String", "MSAK_LOCAL_SERVER_HOST", "\"\"")
            buildConfigField("Boolean", "MSAK_LOCAL_SERVER_SECURE", "true")
            buildConfigField("Integer", "MSAK_LATENCY_PORT", cellwatchProperties["MSAK_LATENCY_PORT"] as String)
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
