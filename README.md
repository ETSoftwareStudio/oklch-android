# oklch-android

Write Jetpack Compose and Android View colors in OKLCH, the same space as CSS `oklch(L C H)`.
The Android twin of [oklch-swift](https://github.com/ETSoftwareStudio/oklch-swift): same math,
same results.

```kotlin
import studio.etsoftware.oklch.oklch
import studio.etsoftware.oklch.oklchColorInt
import studio.etsoftware.oklch.oklchColorLong

Color.oklch(0.55, 0.15, 262)                 // Compose, Display P3
Color.oklch(0.55, 0.15, 262, alpha = 0.5f)
oklchColorInt(0.55, 0.15, 262)              // Views, sRGB @ColorInt, any API level
oklchColorLong(0.55, 0.15, 262)             // Views, Display P3 @ColorLong, API 26+
```

- L: lightness 0..1, C: chroma 0..~0.37, H: hue in degrees.
- Compose colors are Display P3 by default (`gamut = OklchGamut.SRGB` is optional). They only
  show as P3 when the Activity sets `android:colorMode="wideColorGamut"`.
- Colors outside the target gamut are mapped with the CSS Color 4 algorithm, matching
  color.js `toGamut({ method: "css" })`: chroma drops at fixed L and H until clipping the rest
  is no longer noticeable. Browsers currently clip instead, so very saturated colors can differ
  from what Chrome shows.
- Gives the same RGB values as oklch-swift: the tests use the same reference colors.

## Requirements

minSdk 21, Compose `ui-graphics` 1.8.1 (exposed as an `api` dependency).

## Install

Not published yet.

Math: Björn Ottosson, "A perceptual color space for image processing".
