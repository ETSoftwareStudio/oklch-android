plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("com.vanniktech.maven.publish")
}

android {
    namespace = "studio.etsoftware.oklch"
    compileSdk = 36

    defaultConfig {
        minSdk = 21
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api("androidx.compose.ui:ui-graphics:1.8.1")
    implementation("androidx.annotation:annotation:1.9.1")
    // For @Immutable. ui-graphics already brings this artifact in at runtime.
    implementation("androidx.compose.runtime:runtime:1.8.1")

    testImplementation("junit:junit:4.13.2")
}

mavenPublishing {
    coordinates("studio.etsoftware", "oklch", "1.1.0")
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
