import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

val cellwatchProperties = Properties().apply {
    listOf(
        rootProject.file("cellwatch.properties"),
        rootProject.file("cellwatch.local.properties"),
    ).forEach { file ->
        if (file.exists()) {
            file.inputStream().use { load(it) }
        }
    }
}

fun readCellwatchProperty(name: String, defaultValue: String = ""): String {
    return (System.getenv(name) ?: cellwatchProperties.getProperty(name) ?: defaultValue)
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
val releaseSupabaseMode = readCellwatchProperty("CELLWATCH_RELEASE_SUPABASE_MODE", "TESTING")
    .uppercase()
    .also { mode ->
        require(mode == "TESTING" || mode == "LIVE") {
            "CELLWATCH_RELEASE_SUPABASE_MODE must be TESTING or LIVE (was $mode)"
        }
    }
val releaseSupabaseUrlKey = if (releaseSupabaseMode == "LIVE") "SUPABASE_URL" else "SUPABASE_TESTING_URL"
val releaseSupabaseApiKeyKey = if (releaseSupabaseMode == "LIVE") "SUPABASE_API_KEY" else "SUPABASE_TESTING_API_KEY"
val releaseSupabaseUrl = readCellwatchProperty(releaseSupabaseUrlKey)
val releaseSupabaseApiKey = readCellwatchProperty(releaseSupabaseApiKeyKey)

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
        buildConfigField("String", "CELLWATCH_SYNC_DIAGNOSTICS_LEVEL", toBuildConfigString(buildConfigSyncDiagnosticsLevel))
        buildConfigField("int", "CELLWATCH_SYNC_DIAGNOSTICS_MAX_SAMPLES", buildConfigSyncDiagnosticsMaxSamples.toString())
        buildConfigField("boolean", "CELLWATCH_SYNC_DIAGNOSTICS_INCLUDE_CAUSE_CHAIN", buildConfigSyncDiagnosticsIncludeCauseChain.toString())
        buildConfigField("boolean", "CELLWATCH_DISABLE_SUPABASE_SYNC", buildConfigDisableSupabaseSync.toString())
        resValue("string", "mapbox_access_token", buildConfigMapboxAccessToken)
    }

    buildTypes {
        getByName("debug") {
            buildConfigField("String", "CELLWATCH_DEFAULT_MSAK_MODE", "\"LOCAL\"")
            buildConfigField("String", "CELLWATCH_DEFAULT_SUPABASE_MODE", "\"LOCAL\"")
            buildConfigField("boolean", "CELLWATCH_ALLOW_REMOTE_SUPABASE", "false")
            buildConfigField("String", "CELLWATCH_LOCAL_SUPABASE_URL", toBuildConfigString(buildConfigLocalSupabaseUrl))
            buildConfigField("String", "CELLWATCH_LOCAL_SUPABASE_API_KEY", toBuildConfigString(buildConfigLocalSupabaseApiKey))
            buildConfigField("boolean", "CELLWATCH_ALLOW_LIVE_SUPABASE", "false")
            buildConfigField("String", "CELLWATCH_TCP_TUPLE_URL", toBuildConfigString(readCellwatchProperty("TCP_TUPLE_URL")))
            buildConfigField("String", "CELLWATCH_PACKAGED_SUPABASE_MODE", "\"LOCAL\"")
            buildConfigField("String", "CELLWATCH_PACKAGED_SUPABASE_URL", toBuildConfigString(buildConfigLocalSupabaseUrl))
            buildConfigField("String", "CELLWATCH_PACKAGED_SUPABASE_API_KEY", toBuildConfigString(buildConfigLocalSupabaseApiKey))
        }
        getByName("release") {
            buildConfigField("String", "CELLWATCH_DEFAULT_MSAK_MODE", "\"PUBLIC\"")
            buildConfigField("String", "CELLWATCH_DEFAULT_SUPABASE_MODE", toBuildConfigString(releaseSupabaseMode))
            buildConfigField("boolean", "CELLWATCH_ALLOW_REMOTE_SUPABASE", "true")
            buildConfigField("String", "CELLWATCH_LOCAL_SUPABASE_URL", "\"\"")
            buildConfigField("String", "CELLWATCH_LOCAL_SUPABASE_API_KEY", "\"\"")
            buildConfigField("boolean", "CELLWATCH_ALLOW_LIVE_SUPABASE", releaseAllowLiveSupabase.toString())
            buildConfigField("String", "CELLWATCH_TCP_TUPLE_URL", toBuildConfigString(readCellwatchProperty("TCP_TUPLE_URL")))
            buildConfigField("String", "CELLWATCH_PACKAGED_SUPABASE_MODE", toBuildConfigString(releaseSupabaseMode))
            buildConfigField("String", "CELLWATCH_PACKAGED_SUPABASE_URL", toBuildConfigString(releaseSupabaseUrl))
            buildConfigField("String", "CELLWATCH_PACKAGED_SUPABASE_API_KEY", toBuildConfigString(releaseSupabaseApiKey))
        }
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

// LIVE writes to real, deployed data collection, so it needs its own opt-in
// rather than riding on CELLWATCH_ALLOW_REMOTE_SUPABASE, which a hosted-testing
// release also sets. Mirrors RuntimeProfileContract.KEY_ALLOW_LIVE_SUPABASE and
// the iOS AppStore configuration.
val releaseAllowLiveSupabase =
    readCellwatchProperty("CELLWATCH_ALLOW_LIVE_SUPABASE", "false")
        .lowercase() in setOf("true", "1", "yes", "y")

// Checked at CONFIGURATION time, not in a task.
//
// readCellwatchProperty defaults to "", so a missing value used to be baked into
// BuildConfig as an empty string and was only caught if preReleaseBuild ran.
// Failing here means a Release variant cannot be configured with an incomplete
// or improperly gated runtime profile at all.
if (releaseSupabaseMode == "LIVE") {
    require(releaseAllowLiveSupabase) {
        "Android release mode LIVE requires CELLWATCH_ALLOW_LIVE_SUPABASE=true. " +
            "LIVE targets deployed production data; set it deliberately."
    }
}
require(releaseSupabaseUrl.isNotBlank()) {
    "Missing $releaseSupabaseUrlKey for Android $releaseSupabaseMode release packaging."
}
require(releaseSupabaseApiKey.isNotBlank()) {
    "Missing $releaseSupabaseApiKeyKey for Android $releaseSupabaseMode release packaging."
}

val validateReleaseRuntimeProfile by tasks.registering {
    group = "verification"
    description = "Fails when the selected Android release Supabase profile is incomplete or ungated."
    doLast {
        // The require() calls above already failed the build at configuration
        // time; this task keeps a discoverable verification entry point and
        // re-checks in case the values are ever resolved lazily.
        check(releaseSupabaseUrl.isNotBlank()) {
            "Missing $releaseSupabaseUrlKey for Android $releaseSupabaseMode release packaging."
        }
        check(releaseSupabaseApiKey.isNotBlank()) {
            "Missing $releaseSupabaseApiKeyKey for Android $releaseSupabaseMode release packaging."
        }
        check(releaseSupabaseMode != "LIVE" || releaseAllowLiveSupabase) {
            "Android release mode LIVE requires CELLWATCH_ALLOW_LIVE_SUPABASE=true."
        }
    }
}

afterEvaluate {
    tasks.named("preReleaseBuild").configure {
        dependsOn(validateReleaseRuntimeProfile)
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
