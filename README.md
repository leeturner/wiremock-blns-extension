# wiremock-blns-extension
A WireMock extension that allows you to use the Big List of Naughty Strings in your WireMock templates

<!-- src/main/resources/blns/blns.txt is vendored from minimaxir/big-list-of-naughty-strings
     at commit db33ec7b1d5d9616a88c76394b7d0897bd0b97eb (MIT licence alongside it). -->

## Running with standalone WireMock

`wiremock-standalone` relocates Handlebars, so it needs the
`wiremock-blns-extension-standalone` artifact (built locally by `./gradlew shadowJar`
as `build/libs/wiremock-blns-extension-<version>-standalone.jar`). The plain JAR only
works with embedded WireMock.

```shell
java -cp wiremock-standalone-4.0.0-beta.40.jar:wiremock-blns-extension-standalone-<version>.jar \
  wiremock.Run --root-dir path/to/stubs
```

The extension is found by extension scanning, so no `--extensions` flag is needed.

Docker: mount the JAR into `/var/wiremock/extensions/` of `wiremock/wiremock:4.0.0-beta.40`.
This is the 3.x convention and has not yet been verified on 4.x.
