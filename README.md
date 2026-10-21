# wiremock-blns-extension

[![Maven Central](https://img.shields.io/maven-central/v/io.github.leeturner/wiremock-blns-extension)](https://central.sonatype.com/artifact/io.github.leeturner/wiremock-blns-extension)

A [WireMock](https://wiremock.org) extension that adds a `naughtyString` Handlebars
helper, so templated responses can return strings from the
[Big List of Naughty Strings](https://github.com/minimaxir/big-list-of-naughty-strings)
(BLNS): SQL injection, RTL text, zero-width characters, emoji, Zalgo text and more.
Every client test that hits the stub then exercises those cases for free.

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

- Targets **WireMock 4.0.0-beta.41** and runs on Java 17 or later.
- The list is bundled in the JAR. Nothing is downloaded at runtime.

> [!WARNING]
> These are real payloads. The XSS, SQL and command-injection strings reach
> whatever consumes the mock, including logs, databases and browsers. That is the
> point, so only point clients at these stubs in environments where that is safe.

## Install

There are two artifacts. Each only works in its own setting, so pick the one that
matches how you run WireMock. The latest `<version>` is on the Maven Central badge above.

| Artifact | For |
|---|---|
| `io.github.leeturner:wiremock-blns-extension` | Embedded WireMock (`org.wiremock:wiremock`, JUnit) |
| `io.github.leeturner:wiremock-blns-extension-standalone` | The `wiremock-standalone` JAR and the Docker image |

`wiremock-standalone` relocates Handlebars to `wiremock.com.github.jknack.handlebars`,
so the standalone artifact is built against the relocated packages. The wrong one
fails at runtime (the plain JAR in standalone WireMock throws `AbstractMethodError`).

### Embedded (Java / JUnit)

Gradle:

```kotlin
testImplementation("io.github.leeturner:wiremock-blns-extension:<version>")
```

Maven:

```xml
<dependency>
  <groupId>io.github.leeturner</groupId>
  <artifactId>wiremock-blns-extension</artifactId>
  <version><!-- version --></version>
  <scope>test</scope>
</dependency>
```

The artifact has no dependencies of its own: it uses the WireMock already on your
classpath. Register the extension:

```java
@RegisterExtension
static WireMockExtension wm =
    WireMockExtension.newInstance()
        .options(wireMockConfig().dynamicPort().extensions(new BlnsExtension()))
        .build();
```

Or let WireMock find it with `extensionScanningEnabled(true)`.

### Standalone JAR

Download `wiremock-blns-extension-standalone-<version>.jar` from Maven Central and
put it on the classpath next to `wiremock-standalone`:

```shell
java -cp wiremock-standalone-4.0.0-beta.41.jar:wiremock-blns-extension-standalone-<version>.jar \
  wiremock.Run --root-dir path/to/stubs
```

Standalone WireMock scans for extensions by default, so no `--extensions` flag is
needed (unless you pass `--disable-extensions-scanning`).

### Docker

Mount the standalone JAR into `/var/wiremock/extensions/`:

```shell
docker run -p 8080:8080 \
  -v "$PWD/wiremock-blns-extension-standalone-<version>.jar:/var/wiremock/extensions/blns.jar" \
  -v "$PWD/mappings:/home/wiremock/mappings" \
  wiremock/wiremock:4.0.0-beta.41
```

This is the same directory as in WireMock 3.x, and has been checked against
`wiremock/wiremock:4.0.0-beta.41`.

## Helper reference

Stubs need the `response-template` transformer (or global templating) as usual.

```handlebars
{{naughtyString}}                                  {{!-- random, from the whole list --}}
{{naughtyString category='Emoji'}}                 {{!-- random, from one category --}}
{{naughtyString category='sql-injection'}}         {{!-- category given as a slug --}}
{{naughtyString index=42}}                         {{!-- fixed entry from the whole list --}}
{{naughtyString category='Emoji' index=0}}         {{!-- fixed entry within a category --}}
{{naughtyString index=(randomInt lower=0 upper=10)}}
```

| Parameter | Meaning |
|---|---|
| `category` | Limit the choice to one category. Matched case-insensitively, by heading (`SQL Injection`) or slug (`sql-injection`). See [Categories](#categories). |
| `index` | Return a fixed entry instead of a random one, counted from 0 within the whole list or within `category`. Use it to get the same string every time. |

With neither, the helper picks at random from all 516 strings.

Bad input returns an error string, the same way WireMock's built-in helpers do:

- an unknown category gives `[ERROR: Unknown BLNS category 'x'. Known: reserved-strings, …]`;
- an `index` outside the range gives `[ERROR: BLNS index 600 is out of range 0-515]`.
  It does not wrap around, so a typo can't quietly return the wrong string.

### JSON bodies

WireMock doesn't escape helper output, so a naughty string dropped straight into
JSON will often break it (`"`, `\`, control characters). Wrap the helper in WireMock's
`toJson` helper, which adds the quotes and escaping, and leave the value unquoted:

```json
"body": "{ \"name\": {{{toJson (naughtyString)}}} }"
```

This is tested against every entry in the list: all 516 come back as valid JSON that
parses to the original string.

Use `body`, not `jsonBody`. Inside a `jsonBody` string value
(`"name": "{{{toJson …}}}"`) the quotes double up and the result is invalid JSON.

## Categories

From `blns.txt` at commit [`db33ec7`](https://github.com/minimaxir/big-list-of-naughty-strings/blob/db33ec7b1d5d9616a88c76394b7d0897bd0b97eb/blns.txt).

| Category | Slug | Strings |
|---|---|---:|
| Reserved Strings | `reserved-strings` | 19 |
| Numeric Strings | `numeric-strings` | 71 |
| Special Characters | `special-characters` | 9 |
| Unicode Symbols | `unicode-symbols` | 11 |
| Unicode Subscript/Superscript/Accents | `unicode-subscript-superscript-accents` | 4 |
| Quotation Marks | `quotation-marks` | 11 |
| Two-Byte Characters | `two-byte-characters` | 10 |
| Special Unicode Characters Union | `special-unicode-characters-union` | 1 |
| Changing length when lowercased | `changing-length-when-lowercased` | 2 |
| Japanese Emoticons | `japanese-emoticons` | 12 |
| Emoji | `emoji` | 13 |
| Unicode Numbers | `unicode-numbers` | 2 |
| Right-To-Left Strings | `right-to-left-strings` | 7 |
| Ogham Text | `ogham-text` | 2 |
| Trick Unicode | `trick-unicode` | 5 |
| Zalgo Text | `zalgo-text` | 5 |
| Unicode Upsidedown | `unicode-upsidedown` | 2 |
| Unicode font | `unicode-font` | 8 |
| Script Injection | `script-injection` | 236 |
| SQL Injection | `sql-injection` | 8 |
| Server Code Injection | `server-code-injection` | 9 |
| Command Injection (Ruby) | `command-injection-ruby` | 7 |
| Unwanted Interpolation | `unwanted-interpolation` | 9 |
| File Inclusion | `file-inclusion` | 2 |
| Known CVEs and Vulnerabilities | `known-cves-and-vulnerabilities` | 4 |
| MSDOS/Windows Special Filenames | `msdos-windows-special-filenames` | 15 |
| Scunthorpe Problem | `scunthorpe-problem` | 22 |
| Human injection | `human-injection` | 1 |
| Terminal escape codes | `terminal-escape-codes` | 3 |
| iOS Vulnerabilities | `ios-vulnerabilities` | 6 |

## Building

Building needs JDK 25 (see `.sdkmanrc`); the JARs target Java 17.

```shell
./gradlew build                          # tests plus the standalone smoke test
./gradlew test smokeTest -PtestJava=17   # run the tests on a Java 17 JVM
./gradlew shadowJar                      # build/libs/wiremock-blns-extension-<version>-standalone.jar
```

Publishing a GitHub Release publishes both artifacts to Maven Central, with the
version taken from the tag (`v1.2.3` → `1.2.3`).

## Licence

This extension is licensed under the [Apache License 2.0](LICENSE).

The Big List of Naughty Strings is © Max Woolf and licensed under the MIT licence.
A copy is bundled at
[`src/main/resources/blns/LICENSE`](src/main/resources/blns/LICENSE) and ships
inside both JARs.

<!-- src/main/resources/blns/blns.txt is vendored from minimaxir/big-list-of-naughty-strings
     at commit db33ec7b1d5d9616a88c76394b7d0897bd0b97eb (MIT licence alongside it). -->
