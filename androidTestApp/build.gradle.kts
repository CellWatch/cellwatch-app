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
    defaultValue = readCellwatchProperty(
        name = "SUPABASE_LOCAL_API_KEY",
        defaultValue = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZS1kZW1vIiwicm9sZSI6ImFub24iLCJleHAiOjE5ODM4MTI5OTZ9.CRXP1A7WOeoJeXxjNni43kdQwgnWNReilDMblYTn_I0",
    ),
)
val buildConfigSyncDiagnosticsLevel = readCellwatchProperty(
    name = "CELLWATCH_SYNC_DIAGNOSTICS_LEVEL",
    defaultValue = "BASIC",
)
val buildConfigSyncDiagnosticsMaxSamples = readCellwatchProperty(
    name = "CELLWATCH_SYNC_DIAGNOSTICS_MAX_SAMPLES",
    defaultValue = "6",
).toIntOrNull()?.coerceIn(0, 50) ?: 6
val buildConfigSyncDiagnosticsIncludeCauseChain = readCellwatchProperty(
    name = "CELLWATCH_SYNC_DIAGNOSTICS_INCLUDE_CAUSE_CHAIN",
    defaultValue = "false",
).equals("true", ignoreCase = true)
val buildConfigDisableSupabaseSync = readCellwatchProperty(
    name = "CELLWATCH_DISABLE_SUPABASE_SYNC",
    defaultValue = "false",
).equals("true", ignoreCase = true)
val buildConfigMapboxAccessToken = readCellwatchProperty(
    name = "MAPBOX_ACCESS_TOKEN",
    defaultValue = readCellwatchProperty(
        name = "MAPBOX_DOWNLOADS_TOKEN",
        defaultValue = "",
    ),
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
        buildConfigField("String", "CELLWATCH_SYNC_DIAGNOSTICS_LEVEL", toBuildConfigString(buildConfigSyncDiagnosticsLevel))
        buildConfigField("int", "CELLWATCH_SYNC_DIAGNOSTICS_MAX_SAMPLES", buildConfigSyncDiagnosticsMaxSamples.toString())
        buildConfigField("boolean", "CELLWATCH_SYNC_DIAGNOSTICS_INCLUDE_CAUSE_CHAIN", buildConfigSyncDiagnosticsIncludeCauseChain.toString())
        buildConfigField("boolean", "CELLWATCH_DISABLE_SUPABASE_SYNC", buildConfigDisableSupabaseSync.toString())
        resValue("string", "mapbox_access_token", buildConfigMapboxAccessToken)
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
    implementation(libs.mapbox.maps)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.datetime)

    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.espresso.core)
}
