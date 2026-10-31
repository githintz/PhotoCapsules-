// Plugins are put on the root buildscript classpath so that Kotlin and AGP share a classloader,
// and so that AGP (only on Google Maven) is skipped entirely in -PcoreOnly builds.
buildscript {
    val coreOnly = (findProperty("coreOnly")?.toString() ?: "false").toBoolean()
    val kotlinVersion = "2.1.0"
    repositories {
        if (!coreOnly) google()
        mavenCentral()
        gradlePluginPortal()
    }
    dependencies {
        if (!coreOnly) classpath("com.android.tools.build:gradle:8.7.3")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
        classpath("org.jetbrains.kotlin:kotlin-serialization:$kotlinVersion")
        classpath("org.jetbrains.kotlin:compose-compiler-gradle-plugin:$kotlinVersion")
    }
}

subprojects {
    if (path.startsWith(":core:")) {
        apply(plugin = "org.jetbrains.kotlin.jvm")
        extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
            compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
        extensions.configure<JavaPluginExtension> {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
        dependencies {
            add("testImplementation", "org.jetbrains.kotlin:kotlin-test-junit:2.1.0")
            add("testImplementation", "junit:junit:4.13.2")
            add("testImplementation", "org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
        }
        tasks.withType<Test>().configureEach {
            testLogging {
                events("failed")
                exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
                showStandardStreams = false
            }
        }
    }
}
