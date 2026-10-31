apply(plugin = "org.jetbrains.kotlin.plugin.serialization")
dependencies {
    "implementation"("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    "api"(project(":core:geo"))
}
