import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The Google Play Games leaderboard, and the only place in the project that knows Google's libraries. The "google" flavor of :app depends
// on this module; the "plain" flavor (the APK on GitHub) does not, so it has no Google code at all.
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "app.roadtoorbit.playgames"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
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
    implementation("com.google.android.gms:play-services-games-v2:22.1.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
}
