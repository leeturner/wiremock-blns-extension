package io.github.leeturner.wiremock.blns;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.net.ConnectException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;

/** Checks the shadow JAR's contents, then runs it inside wiremock-standalone. */
class StandaloneSmokeTest {

  private static final String EXTENSION_JAR = System.getProperty("extensionJar");
  private static final String STANDALONE_JAR = System.getProperty("standaloneJar");
  private static final HttpClient HTTP = HttpClient.newHttpClient();

  @Test
  void jarHoldsOnlyTheExtensionWithRelocatedHandlebarsReferences() throws Exception {
    try (JarFile jar = new JarFile(EXTENSION_JAR)) {
      List<String> names = Collections.list(jar.entries()).stream().map(JarEntry::getName).toList();
      assertThat(names)
          .contains(
              "io/github/leeturner/wiremock/blns/BlnsExtension.class",
              "io/github/leeturner/wiremock/blns/NaughtyStringHelper.class",
              "blns/blns.txt",
              "META-INF/services/com.github.tomakehurst.wiremock.extension.Extension")
          .noneMatch(n -> n.startsWith("com/github/tomakehurst/"))
          .noneMatch(n -> n.startsWith("wiremock/com/github/jknack/"));
      String classes =
          read(jar, "io/github/leeturner/wiremock/blns/BlnsExtension.class")
              + read(jar, "io/github/leeturner/wiremock/blns/NaughtyStringHelper.class");
      assertThat(classes).contains("wiremock/com/github/jknack/handlebars/Helper");
      assertThat(classes.replace("wiremock/com/github/jknack/", ""))
          .doesNotContain("com/github/jknack/");
    }
  }

  @Test
  void servesNaughtyStringsFromWireMockStandalone() throws Exception {
    List<String> blns;
    try (JarFile jar = new JarFile(EXTENSION_JAR)) {
      blns = List.of(read(jar, "blns/blns.txt").split("\n"));
    }
    int port;
    try (ServerSocket socket = new ServerSocket(0)) {
      port = socket.getLocalPort();
    }
    String java = ProcessHandle.current().info().command().orElse("java");
    Process wiremock =
        new ProcessBuilder(
                java,
                "-cp",
                STANDALONE_JAR + File.pathSeparator + EXTENSION_JAR,
                "wiremock.Run",
                "--port",
                String.valueOf(port),
                "--root-dir",
                "src/smokeTest/resources")
            .inheritIO()
            .start();
    try {
      String base = "http://localhost:" + port;
      // Same value BlnsExtensionTest asserts for the embedded JAR.
      assertThat(fetchWhenUp(base + "/index", wiremock)).isEqualTo("undefined");
      assertThat(fetchWhenUp(base + "/category", wiremock)).isIn(blns);
    } finally {
      wiremock.destroy();
      wiremock.waitFor();
    }
  }

  private static String read(JarFile jar, String name) throws IOException {
    try (var in = jar.getInputStream(jar.getEntry(name))) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  private static String fetchWhenUp(String url, Process wiremock) throws Exception {
    long deadline = System.nanoTime() + 30_000_000_000L;
    while (true) {
      try {
        return HTTP.send(
                HttpRequest.newBuilder(URI.create(url)).build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
            .body();
      } catch (ConnectException e) {
        if (!wiremock.isAlive() || System.nanoTime() > deadline) throw e;
        Thread.sleep(200);
      }
    }
  }
}
