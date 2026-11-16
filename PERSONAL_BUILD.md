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
- The standard single-vowel keyboard fills an eight-column grid. All 22 Korean letters
  have equal width; third-row Shift and Delete fit that grid. Number-row and shift states
  retain the same letter widths.
- Reopening an existing word preserves literal jamo. Replayed text is explicitly marked
  and cannot form a new double tap, even when key coordinates are supplied.
- Supplementary Unicode characters remain intact when typing and deleting.
- Double-tap checkpoints retain the completed-prefix length and only a short undo suffix,
  avoiding full-word copies on each eligible tap.

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
- Version: `4.3-beta2-danmoeum.2`, code `4303`.
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

The stability checks include all 11,172 modern Hangul syllables using the available
single-vowel keys, Unicode normalization with direct jamo, every final-consonant split,
and one-step backspace across the complete syllable set. They also cover 5,000-syllable
composition, all 11 timed mappings, restored literal text, cursor editing, selection,
paste, emoji and supplementary text, language switching, and six common editor types.
Geometry checks cover five screen widths, four alphabet shift states, and number row
enabled/disabled. The test input connection returns the suffix before the caret, as
Android requires; the touch fixture uses each key's width for x and height for y.

The application regression command excludes the separate external documentation-link
tests (`XLinkTest`); existing upstream translation lint errors and asynchronous test-runner
diagnostics are recorded in the release report rather than treated as fixed defects.
