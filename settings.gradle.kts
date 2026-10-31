rootProject.name = "PhotoCapsules"

// -PcoreOnly=true builds only the pure-JVM modules (no Android SDK / Google Maven needed).
val coreOnly = (providers.gradleProperty("coreOnly").orNull ?: "false").toBoolean()

dependencyResolutionManagement {
    repositories {
        if (!coreOnly) google()
        mavenCentral()
        gradlePluginPortal()
    }
}

include(":core:astro", ":core:photo", ":core:geo", ":core:data")
if (!coreOnly) include(":app")
