import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    // AGP 9 compiles Kotlin itself. The root build only sets the Kotlin version.
    id("com.android.library")
    id("com.vanniktech.maven.publish")
}

android {
    namespace = "studio.etsoftware.oklch"
    compileSdk = 36

    defaultConfig {
        minSdk = 21
        // AGP 9 otherwise writes compileSdk here, which would force every app to compile
        // with SDK 36 or newer. Compose's own AARs already set the real floor.
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
    // The compiler can be newer, but apps only need Kotlin 2.0 or newer (1.9 reads 2.0
    // metadata). Raise these on purpose, like the Compose versions.
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

dependencies {
    // These versions are the minimum the library needs, not the newest available: Gradle picks
    // the highest version in the app, so an app on newer Compose gets its own version. Raising
    // them would force every app up too (newer compileSdk, AGP and minSdk). Dependabot ignores
    // them, so raise them on purpose, only when the library needs a newer API.
    api("androidx.compose.ui:ui-graphics:1.0.0")
    // ui-graphics 1.0.0 already brings in annotation 1.2.0.
    implementation("androidx.annotation:annotation:1.2.0")
    // For @Immutable. ui-graphics already brings this artifact in at runtime.
    implementation("androidx.compose.runtime:runtime:1.0.0")

    testImplementation("junit:junit:4.13.2")
}

mavenPublishing {
    // release-please updates this version in its release PR.
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
