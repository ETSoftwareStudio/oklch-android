import java.util.jar.JarFile
import kotlinx.validation.api.dump
import kotlinx.validation.api.filterOutNonPublic
import kotlinx.validation.api.loadApiFromJvmClasses
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    id("com.android.library")
    id("com.vanniktech.maven.publish")
}

android {
    namespace = "studio.etsoftware.oklch"
    compileSdk = 36

    defaultConfig {
        minSdk = 21
        // AGP 9 defaults this to compileSdk, which would force it on every app.
        aarMetadata {
            minCompileSdk = 21
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
    // Apps only need Kotlin 2.0, whatever compiler builds the library.
    compilerOptions {
        apiVersion = KotlinVersion.KOTLIN_2_0
        languageVersion = KotlinVersion.KOTLIN_2_0
    }
    coreLibrariesVersion = "2.0.21"
}

tasks.withType<Test>().configureEach {
    // ART does little escape analysis, so turn HotSpot's off: OklchAllocationTest then
    // counts the allocations a phone would make.
    jvmArgs("-XX:-DoEscapeAnalysis")
}

// CI only: test against the newest stable Compose. Never set when publishing.
if (providers.gradleProperty("latestCompose").isPresent) {
    configurations.configureEach {
        resolutionStrategy {
            eachDependency {
                if (requested.group.startsWith("androidx.compose.")) useVersion("+")
            }
            componentSelection.all {
                if (Regex("(?i)(alpha|beta|rc|dev)").containsMatchIn(candidate.version)) {
                    reject("pre-release")
                }
            }
        }
    }
}

dependencies {
    // Minimum versions, not the latest: apps keep their newer ones. Raise only when needed.
    api("androidx.compose.ui:ui-graphics:1.0.0")
    implementation("androidx.annotation:annotation:1.2.0")
    // For @Immutable. ui-graphics already brings this artifact in at runtime.
    implementation("androidx.compose.runtime:runtime:1.0.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.17")
}

mavenPublishing {
    coordinates("studio.etsoftware", "oklch", "1.1.2") // x-release-please-version
    publishToMavenCentral()
    // Maven Central requires signed artifacts. Sign whenever a key is configured
    // (~/.gradle/gradle.properties or CI), so local builds work without one.
    if (providers.gradleProperty("signingInMemoryKey").isPresent ||
        providers.gradleProperty("signing.keyId").isPresent
    ) {
        signAllPublications()
    }

    pom {
        name = "oklch-android"
        description = "Write Jetpack Compose and Android View colors in OKLCH."
        inceptionYear = "2026"
        url = "https://github.com/ETSoftwareStudio/oklch-android"
        licenses {
            license {
                name = "MIT License"
                url = "https://opensource.org/license/mit"
                distribution = "repo"
            }
        }
        developers {
            developer {
                id = "etsoftwarestudio"
                name = "ET Software Studio"
                url = "https://github.com/ETSoftwareStudio"
            }
        }
        scm {
            url = "https://github.com/ETSoftwareStudio/oklch-android"
            connection = "scm:git:git://github.com/ETSoftwareStudio/oklch-android.git"
            developerConnection = "scm:git:ssh://git@github.com/ETSoftwareStudio/oklch-android.git"
        }
    }
}

// Public API check. The binary-compatibility-validator plugin and Kotlin's abiValidation
// don't see AGP 9's built-in Kotlin yet, so call the validator library on the release classes.
val apiFile = layout.projectDirectory.file("api/oklch.api")
val releaseClasses = layout.buildDirectory.file(
    "intermediates/compile_library_classes_jar/release/bundleLibCompileToJarRelease/classes.jar",
)

fun currentApi(): String = JarFile(releaseClasses.get().asFile).use { jar ->
    jar.loadApiFromJvmClasses()
        .filterOutNonPublic(nonPublicPackages = emptyList(), nonPublicClasses = listOf("studio.etsoftware.oklch.R"))
        .dump(StringBuilder())
        .toString()
}

val apiDump by tasks.registering {
    group = "verification"
    description = "Writes the public API to api/oklch.api."
    dependsOn("bundleLibCompileToJarRelease")
    doLast {
        apiFile.asFile.parentFile.mkdirs()
        apiFile.asFile.writeText(currentApi())
    }
}

val apiCheck by tasks.registering {
    group = "verification"
    description = "Fails if the public API differs from api/oklch.api."
    dependsOn("bundleLibCompileToJarRelease")
    inputs.file(apiFile)
    doLast {
        if (currentApi() != apiFile.asFile.readText()) {
            throw GradleException(
                "The public API changed. Run ./gradlew apiDump and commit oklch/api/oklch.api. " +
                    "If anything was removed or changed, mark the PR title as breaking with '!'.",
            )
        }
    }
}

tasks.named("check") { dependsOn(apiCheck) }
