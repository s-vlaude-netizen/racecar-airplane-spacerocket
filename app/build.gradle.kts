import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "app.roadtoorbit"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.roadtoorbit"
        minSdk = 26
        targetSdk = 35
        versionCode = 14
        versionName = "1.5.0"
    }

    // Two builds of the same game. "plain" is the APK on GitHub: no Google libraries, no permissions, no network, it works on any
    // device (LineageOS without Google Play services included). "google" is the Google Play build: the same game plus an optional
    // Play Games leaderboard of finish times, and every line of Google code lives in the :playgames module that only this flavor
    // depends on. Both have the same application id, so either installs over the other if they are signed with the same key.
    flavorDimensions += "store"
    productFlavors {
        create("plain") { dimension = "store" }
        create("google") { dimension = "store" }
    }

    // A fixed, openly committed debug-style key so every build (local or CI) is signed identically
    // and can be installed over the previous one. This is NOT a secret and not for the Play Store.
    signingConfigs {
        create("shared") {
            storeFile = rootProject.file("keystore/road-to-orbit.keystore")
            storePassword = "roadtoorbit"
            keyAlias = "roadtoorbit"
            keyPassword = "roadtoorbit"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("shared")
        }
        release {
            // No R8: the game is ~130 KB of code, shrinking buys nothing, and what ships should be exactly the
            // bytecode the tests ran (R8's class merging and inlining cannot be exercised on the JVM). It also
            // keeps real class names and line numbers in crash reports.
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("shared")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = false
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core"))
    "googleImplementation"(project(":playgames"))

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
}

// The plain flavor is the GitHub APK and must stay free of Google: not the :playgames module and none of Google's libraries (checked on every
// classpath the flavor is built and tested with; PlainFlavorTest looks at the classes themselves, and CI looks into the finished APK).
val checkPlainHasNoGoogle by tasks.registering {
    group = "verification"
    description = "Fails when the plain flavor depends on the :playgames module or on a Google library"
    val classpaths = listOf("plainDebugRuntimeClasspath", "plainReleaseRuntimeClasspath", "plainDebugUnitTestRuntimeClasspath")
    doLast {
        for (name in classpaths) {
            val components = configurations.getByName(name).incoming.resolutionResult.allComponents.map { it.id.displayName }
            val google = components.filter {
                it.startsWith("com.google.android.gms") || it.startsWith("com.google.android.play") ||
                    it.startsWith("com.google.firebase") || it.contains("playgames")
            }
            check(google.isEmpty()) { "the plain flavor ($name) must not contain Google code, but it has: $google" }
        }
    }
}
tasks.matching { it.name == "check" }.configureEach { dependsOn(checkPlainHasNoGoogle) }
