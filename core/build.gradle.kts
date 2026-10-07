import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation(kotlin("test"))
}

// Knobs for the dev tooling (tools/render-check): forwarded to the test JVM when given as -Dkey=value.
val toolingProps = listOf("simVerbose", "traceDir", "traceLevel", "balance", "balanceSeeds", "traceW", "traceH", "hudStatesDir", "audioDir", "chaos", "chaosRuns", "chaosFrames", "chaosSeed")

tasks.test {
    for (key in toolingProps) System.getProperty(key)?.let { systemProperty(key, it) }
    // Rendering / simulation tests can be a little heavy; give them room.
    maxHeapSize = "1g"
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

// Lets the bot play the whole game and records the renderer's GL commands plus HUD snapshots (build/traces)
// at screenshot size. Its value is the files it writes, so it is never up to date and never cached.
tasks.register<Test>("journeyTrace") {
    group = "verification"
    description = "Records a full bot playthrough for tools/render-check/screenshots.sh"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter { includeTestsMatching("*JourneyTraceTest") }
    for (key in toolingProps) System.getProperty(key)?.let { systemProperty(key, it) }
    systemProperty("traceW", System.getProperty("traceW") ?: "1600")
    systemProperty("traceH", System.getProperty("traceH") ?: "740")
    maxHeapSize = "1g"
    outputs.upToDateWhen { false }
    outputs.cacheIf { false }
}
