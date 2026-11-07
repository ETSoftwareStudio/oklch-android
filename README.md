# oklch-android

[![Maven Central](https://img.shields.io/maven-central/v/studio.etsoftware/oklch)](https://central.sonatype.com/artifact/studio.etsoftware/oklch)

Write Jetpack Compose and Android View colors in OKLCH, the same space as CSS `oklch(L C H)`.
The Android twin of [oklch-swift](https://github.com/ETSoftwareStudio/oklch-swift): same math,
same results.

```kotlin
Color.oklch(0.55, 0.15, 262)                 // Compose, Display P3
Color.oklch(0.55, 0.15, 262, alpha = 0.5f)
oklchColorInt(0.55, 0.15, 262)              // Views, sRGB @ColorInt, any API level
oklchColorLong(0.55, 0.15, 262)             // Views, Display P3 @ColorLong, API 26+
```

- L: lightness 0..1, C: chroma 0..~0.37, H: hue in degrees.
- Compose colors are Display P3 by default (`gamut = OklchGamut.SRGB` is optional).
- Colors outside the target gamut are mapped with the CSS Color 4 algorithm, matching
  color.js `toGamut({ method: "css" })`: chroma drops at fixed L and H until clipping the rest
  is no longer noticeable. Browsers currently clip instead, so very saturated colors can differ
  from what Chrome shows.
- Gives the same RGB values as oklch-swift: the tests use the same reference colors.

## Install

The library is on Maven Central:

<!-- x-release-please-start-version -->
| Group | Artifact | Version |
|---|---|---|
| `studio.etsoftware` | `oklch` | `1.1.2` |
<!-- x-release-please-end -->

Requires minSdk 21 and Compose 1.0 or newer. It depends on Compose `ui-graphics` 1.0.0 as an
`api` dependency: Gradle uses the newer Compose your app already has.

`mavenCentral()` is already in the repositories of new Android projects. Add the dependency to
your module's `build.gradle.kts`:

<!-- x-release-please-start-version -->
```kotlin
dependencies {
    implementation("studio.etsoftware:oklch:1.1.2")
}
```
<!-- x-release-please-end -->

Or with a version catalog (`gradle/libs.versions.toml`):

<!-- x-release-please-start-version -->
```toml
[versions]
oklch = "1.1.2"

[libraries]
oklch = { module = "studio.etsoftware:oklch", version.ref = "oklch" }
```
<!-- x-release-please-end -->

```kotlin
dependencies {
    implementation(libs.oklch)
}
```

## Usage

### Jetpack Compose

```kotlin
import androidx.compose.ui.graphics.Color
import studio.etsoftware.oklch.oklch

val Brand = Color.oklch(0.55, 0.15, 262)

Text("Hello", color = Brand)
Box(Modifier.background(Color.oklch(0.97, 0.008, 80)))
```

For light and dark themes, build each color scheme from OKLCH. Lowering L at the same hue keeps
the two themes related:

```kotlin
private val LightColors = lightColorScheme(
    primary = Color.oklch(0.55, 0.15, 262),
    background = Color.oklch(0.97, 0.008, 80),
)
private val DarkColors = darkColorScheme(
    primary = Color.oklch(0.8, 0.12, 262),
    background = Color.oklch(0.15, 0.006, 60),
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
```

### Value type

`Oklch` holds one color: `l`, `c`, `h` and `alpha`. Use it to keep colors in OKLCH, mix them
and convert them back to Compose.

```kotlin
import studio.etsoftware.oklch.Oklch
import studio.etsoftware.oklch.toOklch

val brand = Oklch(0.55, 0.15, 262.0)            // the hue wraps: -30.0 becomes 330.0
val accent = Color.Red.toOklch()               // any color space, P3 too
val between = brand.mix(accent, 0.5)           // like CSS color-mix(in oklch)
Box(Modifier.background(between.toColor()))    // Display P3; toColor(OklchGamut.SRGB) for sRGB
```

- `mix` takes the shorter way round the hue circle. `t` is not clamped, so springs can overshoot.
- A grey (chroma below 0.000004) has no hue. It takes the hue of the other color in a mix.
- Alpha premultiplies L and C in a mix, so a transparent end does not change them.

### Views

```kotlin
import studio.etsoftware.oklch.oklchColorInt
import studio.etsoftware.oklch.oklchColorLong

view.setBackgroundColor(oklchColorInt(0.55, 0.15, 262))   // sRGB, any API level
paint.setColor(oklchColorLong(0.55, 0.15, 262))           // Display P3, API 26+
```

### Showing Display P3

Android draws P3 colors as P3 only in activities that opt in. Otherwise it converts them to
sRGB, so they still look right, just less vivid at high chroma:

```xml
<activity
    android:name=".MainActivity"
    android:colorMode="wideColorGamut" />
```

## Releasing

Releases are automated with [release-please](https://github.com/googleapis/release-please).
Commit messages follow [Conventional Commits](https://www.conventionalcommits.org/):
`fix:` and `perf:` make a patch release, `feat:` a minor one, and `feat!:` a major one.
PR titles must follow it too (CI checks them): a squash merge keeps only the title.

The public API is tracked in `oklch/api/oklch.api`. If a PR changes it, run `./gradlew apiDump`
and commit the file. Removing or changing anything there breaks apps, so CI then requires `!`
in the PR title.

1. Merge changes into `main`. release-please keeps a "chore(main): release X.Y.Z" PR open
   with the version bump and the changelog.
2. Merge that PR to release. The Release workflow tags the version, creates the GitHub
   release and publishes to Maven Central.

The Central Portal user token and the GPG signing key are repository secrets
(`MAVEN_CENTRAL_USERNAME`, `MAVEN_CENTRAL_PASSWORD`, `SIGNING_IN_MEMORY_KEY`,
`SIGNING_IN_MEMORY_KEY_PASSWORD`), never in the repo.

## License

MIT. See [LICENSE](LICENSE).

Math: Björn Ottosson, "A perceptual color space for image processing".
