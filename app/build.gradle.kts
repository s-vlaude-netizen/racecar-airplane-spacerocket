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
        versionCode = 6
        versionName = "1.1.1"
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

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
}
