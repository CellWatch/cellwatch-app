import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

val cellwatchProperties = Properties().apply {
    val file = rootProject.file("cellwatch.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

fun readCellwatchProperty(name: String, defaultValue: String = ""): String {
    return (cellwatchProperties.getProperty(name) ?: System.getenv(name) ?: defaultValue)
        .trim()
        .removeSurrounding("\"")
}

fun toBuildConfigString(value: String): String {
    return "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""
}

val buildConfigLocalSupabaseUrl = readCellwatchProperty("SUPABASE_LOCAL_URL", "http://10.0.2.2:54321")
    .replace("127.0.0.1", "10.0.2.2")
    .replace("localhost", "10.0.2.2")
val buildConfigLocalSupabaseApiKey = readCellwatchProperty(
    name = "SUPABASE_LOCAL_SERVICE_KEY",
    defaultValue = readCellwatchProperty("SUPABASE_LOCAL_API_KEY"),
)

android {
    namespace = "edu.gatech.cc.cellwatch.androidtestapp"
    compileSdk = 34

    defaultConfig {
        applicationId = "edu.gatech.cc.cellwatch.androidtestapp"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "CELLWATCH_LOCAL_SUPABASE_URL", toBuildConfigString(buildConfigLocalSupabaseUrl))
        buildConfigField("String", "CELLWATCH_LOCAL_SUPABASE_API_KEY", toBuildConfigString(buildConfigLocalSupabaseApiKey))
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.core.ktx)
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.datetime)
    implementation(libs.sqldelight.android.driver)
    implementation(libs.ktor.client.cio)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.datetime)

    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.espresso.core)
}
