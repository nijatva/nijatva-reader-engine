plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(libs.junit)
}

// The boundary is a build failure, not a habit. engine-core is the part that must run
// on a plain JVM with no Android artifact anywhere near it.
val androidImports = Regex("""^\s*import\s+(android|androidx)\.""", RegexOption.MULTILINE)

val checkCorePurity by tasks.registering {
    description = "Fails if engine-core imports android.* or androidx.*"
    group = "verification"
    val sources = fileTree("src") { include("**/*.kt", "**/*.java") }
    val root = projectDir
    doLast {
        val offenders = sources.filter { f ->
            androidImports.containsMatchIn(f.readText())
        }.map { it.relativeTo(root).path }
        if (offenders.isNotEmpty()) {
            throw GradleException(
                "engine-core must not depend on Android. Offending files:\n" +
                    offenders.joinToString("\n") { "  $it" }
            )
        }
    }
}

tasks.named("check") { dependsOn(checkCorePurity) }
