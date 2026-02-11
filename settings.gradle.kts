import java.util.Properties

include(":shared")
include(":androidTestApp")


pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

val cellwatchProperties = Properties()
file("cellwatch.properties").inputStream().use { cellwatchProperties.load(it) }
val mapboxToken = cellwatchProperties.getProperty("MAPBOX_DOWNLOADS_TOKEN")

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenLocal()
        mavenCentral()
        maven(url = "https://jitpack.io")
        maven(url = "https://api.mapbox.com/downloads/v2/releases/maven") {
            authentication {
                create<BasicAuthentication>("basic")
            }
            credentials {
                username = "mapbox"
                password = mapboxToken
            }
        }
    }
}

rootProject.name = "cellwatch"
include(":app")

// Optional local-source override for msak-client-kmp.
// Keep this opt-in only so default builds stay on Maven artifacts (mavenLocal/remote).
// Enable with: -Pcellwatch.useLocalMsak=true
val useLocalMsak = providers.gradleProperty("cellwatch.useLocalMsak")
    .orNull
    ?.toBooleanStrictOrNull()
    ?: false

if (useLocalMsak) {
    val localMsakDir = providers.gradleProperty("cellwatch.local.msak.dir")
        .orNull
        ?.takeIf { it.isNotBlank() }
        ?: "../msak-android"

    val localMsakFile = file(localMsakDir)
    check(localMsakFile.exists()) {
        "cellwatch.useLocalMsak=true but local msak directory does not exist: ${localMsakFile.absolutePath}"
    }

    includeBuild(localMsakFile) {
        dependencySubstitution {
            // Substitute only the published msak-client-kmp coordinate with the local project.
            substitute(module("edu.gatech.cc.cellwatch:msak-client-kmp"))
                .using(project(":msak-shared"))
        }
    }
}
