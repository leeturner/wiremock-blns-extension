# Roadmap: WireMock BLNS Extension

A WireMock extension that adds a Handlebars helper for putting strings from the
[Big List of Naughty Strings](https://github.com/minimaxir/big-list-of-naughty-strings)
(BLNS) into templated WireMock responses. It targets **WireMock 4.0.0-beta.40**.

## Why

Consumers of a mocked API rarely get tested against hostile or awkward data: SQL
injection, RTL text, zero-width characters, emoji, Zalgo text, and so on. If a
stub can return a naughty string, every client test that hits the mock exercises
those cases for free.

```json
{
  "request": { "method": "GET", "url": "/user" },
  "response": {
    "status": 200,
    "headers": { "Content-Type": "application/json" },
    "body": "{ \"name\": {{{toJson (naughtyString category='sql-injection')}}} }",
    "transformers": ["response-template"]
  }
}
```

The helper goes in a `body` string, unquoted, and `toJson` supplies the quotes and
escaping. Inside a `jsonBody` string value (`"name": "{{{toJson …}}}"`) the quotes
would double up and produce invalid JSON.

## Goals

- One Handlebars helper, `naughtyString`, that returns a BLNS string. It can pick
  at random, pick from a category, or pick a fixed entry by index.
- Works when WireMock is embedded (Java/JUnit) and when it runs standalone (JAR or Docker).
- The list is bundled in the extension, so nothing is downloaded at runtime.

## Non-goals

- Matching requests against naughty strings (request-side fuzzing).
- Seeded or reproducible random sequences. Use `index` to get the same string every time.
- A runtime option to fetch a newer list. BLNS has barely changed since 2024
  (last push 2024-04-18), so a vendored copy is enough.

## What we verified about WireMock 4.0.0-beta.40

These come from inspecting the published artifacts on Maven Central, not from memory:

| Fact | Consequence |
|---|---|
| `org.wiremock:wiremock` is now an aggregator; the extension API lives in `org.wiremock:wiremock-core` | Compile against `wiremock-core` as `compileOnly` |
| Class files are major version 61 | Java 17 baseline |
| `TemplateHelperProviderExtension.provideTemplateHelpers()` returns `Map<String, com.github.jknack.handlebars.Helper<?>>`; `Extension` has `getName()` plus default `start()`/`stop()` | This is the only interface we implement |
| `HandlebarsHelper<T>` (in `...responsetemplating.helpers`) is public, with `handleError(...)` producing `[ERROR: ...]` | Extend it so errors look the same as WireMock's built-in helpers |
| Extensions are found with `ServiceLoader` on `Extension` and `ExtensionFactory`. Standalone scans by default (`--disable-extensions-scanning` turns it off) | Ship `META-INF/services/com.github.tomakehurst.wiremock.extension.Extension` |
| **`wiremock-standalone` relocates Handlebars to `wiremock.com.github.jknack.handlebars`** but leaves `com.github.tomakehurst.wiremock.*` alone | A JAR built against plain Handlebars **will not load** in standalone. We need a second, relocated JAR |
| `TemplateEngine` uses `EscapingStrategy.NOOP` | `{{naughtyString}}` is not HTML-escaped. That is what we want, but JSON bodies need `toJson` (see Phase 3) |
| Built-in helpers include `toJson`, `pickRandom`, `randomInt` | Reuse them; don't duplicate |
| `org.wiremock:wiremock-junit5:4.0.0-beta.40` and Docker `wiremock/wiremock:4.0.0-beta.40` exist | Use them for tests and the Docker smoke test |

## Defaults I chose (change any of these)

- **Build: Gradle (Kotlin DSL), modelled on your own
  [`wiremock-micronaut`](https://github.com/leeturner/wiremock-micronaut).** That
  project already publishes `io.github.leeturner:*` to Maven Central with the same
  tools. The extension code is modelled on
  [`wiremock-faker-extension`](https://github.com/wiremock/wiremock-faker-extension).
  The reasons for not using WireMock's shared plugins are in "Build setup" below.
- **Language: Java 17 bytecode** (`options.release = 17`), matching WireMock's
  baseline. The build JDK can be newer; `wiremock-micronaut` uses 25.
- **Two artifacts, the same shape as the official extensions.** Faker, state and
  gRPC each publish `<name>` and `<name>-standalone` as separate artifactIds
  (checked on Maven Central). We do the same under the namespace you already
  publish `wiremock-micronaut` to:

  | Artifact | Contents | For |
  |---|---|---|
  | `io.github.leeturner:wiremock-blns-extension` | Plain JAR, references `com.github.jknack.handlebars.*` | Embedded WireMock (`org.wiremock:wiremock`, JUnit) |
  | `io.github.leeturner:wiremock-blns-extension-standalone` | Shadow JAR, references relocated to `wiremock.com.github.jknack.handlebars.*` | `wiremock-standalone` JAR and the Docker image |

  Each JAR only works in its own setting: the plain JAR won't load in standalone,
  and the relocated JAR won't load embedded. The Phase 3 and Phase 4 tests check
  both.
- **Package:** `io.github.leeturner.wiremock.blns`.
- **Helper name: `naughtyString`.** It reads better in templates than `blns`. The
  extension's `getName()` is `blns`.
- **Data source: `blns.txt`**, not `blns.json`. Only the `.txt` file has category
  headings; `blns.json` is just a flat array of 515 strings. The `.json` is also
  out of date at this commit. Around 230 entries differ: the `.txt` numbers its XSS
  payloads `alert(1)`, `alert(2)`, … where the `.json` uses `alert(123)` throughout,
  and some `.txt` entries such as `<script\x09type=…>` are missing from the `.json`.
  The cost: `.txt` cannot hold strings that contain NUL, LF or CR. The `.json` has
  none of those either, so we lose nothing.
- **Line splitting:** split only on `\n`. Some entries contain U+2028/U+2029/U+0085,
  and splitters that treat Unicode line separators as line breaks (Python's
  `splitlines`, for example) would cut those strings apart.

## Build setup

**Why we don't use WireMock's shared plugins.** Most of the official extensions use
`gradle-wiremock-extension-plugins` (`org.wiremock.tools.gradle.java` /
`.publishing`, latest `0.6.0-beta.3`). Reading their source shows they don't suit
a repo that lives outside the `wiremock` org:

- the java plugin forces `group 'org.wiremock.extensions'`;
- the publishing plugin hardcodes `https://github.com/wiremock/${githubRepo}` into
  the POM's `url`/`scm` and into its GitHub Packages repository;
- it brings in the old `com.github.johnrengelman.shadow` 8.1.1, which ties the build
  to Gradle 8.x. Faker uses 8.14.3;
- if `wiremockVersion` is missing from `gradle.properties`, it silently falls back
  to WireMock `4.0.0-beta.19`.

What those plugins actually do is small: `java-library`, sources and javadoc JARs,
Shadow with relocation, and vanniktech publishing. `wiremock-micronaut` already
does the same things in about 60 lines of `build.gradle.kts`, so we copy that and
add Shadow.

**Files** (copied from `wiremock-micronaut` and trimmed):

- `gradle/libs.versions.toml`:

  ```toml
  [versions]
  wiremock = "4.0.0-beta.40"
  google-java-format = "1.35.0"

  [libraries]
  wiremock-core       = { module = "org.wiremock:wiremock-core", version.ref = "wiremock" }
  wiremock            = { module = "org.wiremock:wiremock", version.ref = "wiremock" }
  wiremock-junit5     = { module = "org.wiremock:wiremock-junit5", version.ref = "wiremock" }
  wiremock-standalone = { module = "org.wiremock:wiremock-standalone", version.ref = "wiremock" }
  junit-bom           = { module = "org.junit:junit-bom", version = "5.14.4" }
  assertj             = { module = "org.assertj:assertj-core", version = "3.27.7" }

  [plugins]
  shadow        = { id = "com.gradleup.shadow", version = "9.6.1" }
  spotless      = { id = "com.diffplug.spotless", version = "8.10.4" }
  maven-publish = { id = "com.vanniktech.maven.publish", version = "0.37.0" }
  ```

- `build.gradle.kts`, the parts that differ from `wiremock-micronaut`:

  ```kotlin
  plugins {
      `java-library`
      alias(libs.plugins.shadow)
      alias(libs.plugins.spotless)
      alias(libs.plugins.maven.publish)
  }

  dependencies {
      compileOnly(libs.wiremock.core)          // brings handlebars at compile time
      testImplementation(libs.wiremock)
      testImplementation(libs.wiremock.junit5)
      testImplementation(platform(libs.junit.bom))
      testImplementation("org.junit.jupiter:junit-jupiter")
      testImplementation(libs.assertj)
      testRuntimeOnly("org.junit.platform:junit-platform-launcher")
  }

  tasks.withType<JavaCompile>().configureEach { options.release = 17 }

  shadow { addShadowVariantIntoJavaComponent = false }   // keep the shadow JAR out of the main artifact

  tasks.shadowJar {
      archiveClassifier = "standalone"
      relocate("com.github.jknack", "wiremock.com.github.jknack")
  }

  mavenPublishing {
      publishToMavenCentral(automaticRelease = true)
      signAllPublications()
      // group from gradle.properties; artifactId from rootProject.name. coordinates() is
      // not called, because it may also overwrite the standalone publication's artifactId.
      pom { /* same as wiremock-micronaut, with the name/description/URLs changed */ }
  }

  publishing {
      publications {
          register<MavenPublication>("standalone") {
              artifactId = "wiremock-blns-extension-standalone"
              artifact(tasks.shadowJar) { classifier = null }
              artifact(tasks.named("sourcesJar"))    // Central requires sources and javadoc
              artifact(tasks.named("javadocJar"))    // for every artifactId
          }
      }
  }
  ```

- `settings.gradle.kts`, `gradle.properties` (`group=io.github.leeturner`,
  `version=0.1.0-SNAPSHOT`, `org.gradle.caching=true`), `.sdkmanrc` (`java=25-tem`),
  and the Gradle wrapper at **9.8.x**, all as in `wiremock-micronaut`.

**`compileOnly`, not `api`, for WireMock.** Faker declares
`api "org.wiremock:wiremock-core"`. That puts WireMock on `runtimeClasspath`, and
Shadow bundles `runtimeClasspath`, so faker's published standalone JAR
(`0.3.0-beta.1`) is 19 MB and contains about 700 `com/github/tomakehurst`
classes. With `compileOnly` our runtime classpath is empty. The standalone JAR is
then just our two classes (with relocated Handlebars references), `blns.txt` and
the services file, and the published POM has no dependencies. Embedded users
already have WireMock on their classpath.

## Helper API

```handlebars
{{naughtyString}}                                  {{!-- random, from the whole list --}}
{{naughtyString category='Emoji'}}                 {{!-- random, from one category --}}
{{naughtyString category='sql-injection'}}         {{!-- category given as a slug --}}
{{naughtyString index=42}}                         {{!-- fixed entry from the whole list --}}
{{naughtyString category='Emoji' index=0}}         {{!-- fixed entry within a category --}}
{{naughtyString index=(randomInt lower=0 upper=10)}}
```

- `category` is matched case-insensitively, either by heading (`SQL Injection`) or
  by slug (`sql-injection`). An unknown category returns
  `[ERROR: Unknown BLNS category 'x'. Known: ...]`.
- An `index` outside the range also returns `[ERROR: ...]`. It does not wrap
  around, so a typo can't quietly return the wrong string.
- Random choice uses `ThreadLocalRandom`.

## Phases

### Phase 0: Scaffold

- Copy the scaffold from `wiremock-micronaut`:
  - the Gradle wrapper (9.8.x);
  - `.sdkmanrc`, `gradle.properties` and `settings.gradle.kts`;
  - the Spotless config.

  Then write `libs.versions.toml` and `build.gradle.kts` as in "Build setup".
- The existing `.gitignore` ignores `*.jar`. Add the exception
  `!gradle/wrapper/gradle-wrapper.jar`, and add `.gradle/` and `build/`.
- **Done when:** `./gradlew build` passes on an empty project.

### Phase 1: Vendor and parse the list

- Copy `blns.txt` from BLNS commit `db33ec7b1d5d9616a88c76394b7d0897bd0b97eb` to
  `src/main/resources/blns/blns.txt`. Put the BLNS MIT licence next to it, and
  record the commit in a comment in the README.
- Parse the file once at class-load time into a `LinkedHashMap<String, List<String>>`
  (category → strings), plus one flat `List<String>` of every string.
  - **Parsed once at startup, reused for every request.** A static initialiser
    loads the file the first time the helper class loads. The result is held in
    immutable collections and shared by every WireMock server in the JVM, so no
    request re-reads or re-parses the file. The cost is one pass over about 30 KB.
  - **No build-time conversion.** Turning `blns.txt` into JSON or Java source
    during the build would only move the parser into the build and add a reader
    at runtime. JSON would also need Jackson, which is relocated in standalone.
    Java source would need escaping for the control, zero-width, BOM and
    lone-surrogate strings. The Phase 1 test already fails the build on a
    parsing mistake. Revisit only if the list grows a lot or has to be fetched
    from upstream.
- Parsing rules, taken from the file as it is today:
  - A **category heading** is a `#\t<title>` line followed directly by a bare `#` line.
  - Any other `#` line is a comment.
  - Blank lines are skipped.
  - Every other line is a string, and belongs to the most recent heading.
  - Watch out: the "Special Unicode Characters Union" section has a second
    `#\t…` + `#` pair (the VMware description), which the heading rule would
    wrongly treat as a category. Handle it with an explicit skip, or require the
    heading to be preceded by a blank or string line. Pin the outcome with a test.
- Read the file as UTF-8 explicitly. Keep trailing whitespace: some entries are
  only whitespace.
- **Test:**
  - Exactly 30 categories, in file order (the list is in the appendix).
  - About 515 strings in total. Assert the exact number once the parser is settled.
  - A few known strings land in the right category, e.g. `1;DROP TABLE users` → SQL Injection.

### Phase 2: Helper and extension

- `NaughtyStringHelper extends HandlebarsHelper<Object>`:
  - reads the `category` and `index` hash parameters;
  - uses `handleError` for bad input.
- `BlnsExtension implements TemplateHelperProviderExtension`:
  - `getName()` returns `"blns"`;
  - `provideTemplateHelpers()` returns `Map.of("naughtyString", new NaughtyStringHelper())`.
- Add `META-INF/services/com.github.tomakehurst.wiremock.extension.Extension`
  containing `BlnsExtension`.
- Two classes, one data file and no extra abstractions.

### Phase 3: Embedded integration tests

Use `WireMockExtension` (junit5) with `.options(wireMockConfig().dynamicPort().extensions(new BlnsExtension()))`:

1. **Random:** `{{naughtyString}}` returns a string that is in the list.
2. **Category:** every string returned for `category='sql-injection'` is in that category.
3. **Index:** `index=0` always gives the same string.
4. **Errors:** an unknown category and an out-of-range index both give `[ERROR: ...]`.
5. **JSON safety (the most important test):** for *every* index, a
   `body` stub using `{ "value": {{{toJson (naughtyString index=i)}}} }` must return
   JSON that parses and round-trips to the original string. This shows the README's
   recommended pattern works for all ~515 entries. If `toJson` fails on some
   (control characters, lone surrogates), document those entries or add a
   `json=true` hash option. Only build it if the test fails. Add one
   `jsonBody` case as well, to confirm WireMock 4 doesn't treat a whole-string
   `"{{{toJson …}}}"` value specially. If it does, document that as an alternative.
6. **ServiceLoader:** a server started with `extensionScanningEnabled(true)` and
   *no* explicit extension still resolves `{{naughtyString}}`.

### Phase 4: Standalone and Docker packaging

- `./gradlew shadowJar` produces `build/libs/wiremock-blns-extension-<v>-standalone.jar`
  (the classifier is only the local file name; it is published without one).
- **Check what gets published.** Run `./gradlew publishToMavenLocal` and look in
  `~/.m2/repository/io/github/leeturner/`. You should see exactly two artifactIds,
  each with `.jar`, `-sources.jar`, `-javadoc.jar` and `.pom`, and POMs with no
  `<dependencies>`. Four things need checking, because they come from how
  vanniktech 0.37 and Shadow 9 behave together:
  - the `pom {}` block reaches the second publication;
  - `signAllPublications()` signs it;
  - the `sourcesJar`/`javadocJar` task names are right;
  - nothing renames the standalone artifactId.

  If the `pom {}` block isn't applied, set it on that publication directly.
- **Check the JAR's contents** (a few lines in `build.gradle.kts`, or a test that
  opens the JAR):
  - it contains `io/github/leeturner/wiremock/blns/*`, `blns/blns.txt` and the services file;
  - it contains **no** `com/github/tomakehurst/**` and no `wiremock/com/github/jknack/**` classes;
  - the bytecode references `wiremock/com/github/jknack/handlebars/Helper`.

  This is how we catch faker's problem of bundling all of WireMock.
- **Smoke test** (a Gradle task that depends on `shadowJar`, run from `check`):
  1. Resolve `libs.wiremock.standalone` through a dedicated configuration, so no download script is needed.
  2. Run `java -cp wiremock-standalone.jar:wiremock-blns-extension-<v>-standalone.jar wiremock.Run --port 0 --root-dir src/smokeTest/resources`.
  3. Hit a fixture stub and check the response is a BLNS string.
- **Docker:** mount the standalone JAR into `wiremock/wiremock:4.0.0-beta.40` at
  `/var/wiremock/extensions/`. That is the 3.x convention, so **check it still
  applies in 4.x** and record the result in the README.
- **Done when:** both the embedded JAR and the standalone JAR give the same output
  for the same stub.

### Phase 5: Docs, CI, release

- README sections:
  - installation: Maven/Gradle for embedded use; the standalone JAR or Docker for standalone;
  - helper reference with examples, including the `toJson` pattern for JSON bodies;
  - the category list;
  - BLNS attribution and licence.
- GitHub Actions: copy `build.yml` and `release.yml` from `wiremock-micronaut`.
  - `build.yml` runs `./gradlew build` (which includes the smoke test) on a JDK
    matrix of `17` and `25`. JDK 17 proves the extension runs on WireMock's baseline.
    Keep the `dependency-submission` job.
  - `release.yml` is unchanged: on a published GitHub Release it runs
    `./gradlew build publishToMavenCentral -Pversion="${GITHUB_REF_NAME#v}"`. Add
    the `MAVEN_CENTRAL_USERNAME`, `MAVEN_CENTRAL_PASSWORD`, `SIGNING_KEY` and
    `SIGNING_PASSWORD` secrets to this repo.
- Optional: copy `dependabot-automerge.yml` / `weekly.yml` too.

## Risks and open questions

- **WireMock 4 is still in beta.** Its APIs, the shaded package prefix and the
  Docker extension directory could all change before GA. The standalone smoke test
  is there to catch that. Pin `4.0.0-beta.40` and bump it on purpose.
- **Payloads in a stub are real payloads.** The XSS, SQL and command-injection
  strings will reach whatever consumes the mock. That is the point of the
  extension, but the README should say so plainly.
- **Should a list helper exist?** For example
  `{{#each (naughtyStrings category='emoji')}}` to build a JSON array. Skipped for
  now; add it if someone asks.

## Appendix: BLNS categories (from `blns.txt` @ `db33ec7`)

Reserved Strings · Numeric Strings · Special Characters · Unicode Symbols ·
Unicode Subscript/Superscript/Accents · Quotation Marks · Two-Byte Characters ·
Special Unicode Characters Union · Changing length when lowercased · Japanese
Emoticons · Emoji · Unicode Numbers · Right-To-Left Strings · Ogham Text · Trick
Unicode · Zalgo Text · Unicode Upsidedown · Unicode font · Script Injection · SQL
Injection · Server Code Injection · Command Injection (Ruby) · Unwanted
Interpolation · File Inclusion · Known CVEs and Vulnerabilities · MSDOS/Windows
Special Filenames · Scunthorpe Problem · Human injection · Terminal escape codes ·
iOS Vulnerabilities
