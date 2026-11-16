# HeliBoard 단모음 personal fork

This fork keeps HeliBoard's existing features and adds built-in Korean single-vowel input.
It is based on upstream commit `106386356f0ff5b13ab6617bca4d1cea4d9513e6` (4.3-beta2).
These changes were produced with AI assistance for this personal fork and have not been submitted upstream.

## Defaults and input

- A fresh install enables Korean single vowel and English, initially selecting Korean.
- The comma position becomes the language switch key. The former globe position becomes `@`.
- The expandable toolbar opens by default. Suggestions remain available by collapsing it.
- The original functional layout and Korean layouts remain selectable.
- New preferences appear under Advanced: enable double taps and set the interval (200–800 ms; default 450 ms).
- Long press provides the alternative vowels and tense consonants even when double taps are off.
- Timed conversion only applies to the built-in `korean_single_vowel` layout and actual touch input.
- Pauses, cursor changes, layout changes, hardware events, and key repeats do not continue a tap pair.
- An immediate backspace after a conversion restores its first tap.
- Editing inside an existing Korean word starts composition at the editor caret instead of appending at its end.
- Physical keyboards retain standard Korean two-set mapping; touch double-tap timing does not apply to them.

| Base | Double tap |
|---|---|
| ㅏ ㅓ ㅗ ㅜ ㅐ ㅔ | ㅑ ㅕ ㅛ ㅠ ㅒ ㅖ |
| ㄱ ㄷ ㅂ ㅅ ㅈ | ㄲ ㄸ ㅃ ㅆ ㅉ |

The main rows are:

| Row | Keys |
|---|---|
| 1 | ㅂ ㅈ ㄷ ㄱ ㅅ ㅗ ㅐ ㅔ |
| 2 | ㅁ ㄴ ㅇ ㄹ ㅎ ㅓ ㅏ ㅣ |
| 3 | ㅋ ㅌ ㅊ ㅍ ㅜ ㅡ |

## Android build

- Package: `io.github.minaric0.heliboard` (can coexist with upstream).
- Version: `4.3-beta2-danmoeum.1`, code `4302`.
- Compile and target SDK: 37 (Android 17). Minimum API remains 21.
- The personal release uses `-PpersonalAbis=arm64-v8a` for Samsung SM-A175N.
- R8 release optimization remains enabled; the native dictionary library uses NDK r28.
- No Knox enrollment, device owner, root, Internet permission, or new runtime permissions are added.
- Optional gesture typing still uses upstream's separately imported compatible library.

Build unsigned release with `./gradlew :app:assembleRelease -PpersonalAbis=arm64-v8a`.
Run JVM/Robolectric checks with `./gradlew :app:testRunTestsUnitTest -PpersonalAbis=arm64-v8a`.
The existing `runTests` variant is explicitly enabled for unit tests on AGP 9.

Signing happens after the build. The private signing key is distributed separately to the owner;
it must never be committed to this public repository. Keep the same package and key for updates,
and increase the version code for each release.

## Verification

The accompanying build report records the exact checks and their results. Physical Samsung
One UI behavior must be assessed on the actual handset; a compiled APK or JVM test does not
establish device compatibility by itself.
