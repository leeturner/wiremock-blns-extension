package io.github.leeturner.wiremock.blns;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.common.Json;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class BlnsExtensionTest {

  @RegisterExtension
  static WireMockExtension wm =
      WireMockExtension.newInstance()
          .options(wireMockConfig().dynamicPort().extensions(new BlnsExtension()))
          .build();

  @RegisterExtension
  static WireMockExtension scanned =
      WireMockExtension.newInstance()
          .options(wireMockConfig().dynamicPort().extensionScanningEnabled(true))
          .build();

  private static final HttpClient HTTP = HttpClient.newHttpClient();
  private static final ObjectMapper JSON = new ObjectMapper();

  @Test
  void returnsARandomStringFromTheList() throws Exception {
    assertThat(NaughtyStringHelper.ALL).contains(render(wm, "{{naughtyString}}"));
  }

  @Test
  void returnsOnlyStringsFromTheRequestedCategory() throws Exception {
    stub(wm, "{{naughtyString category='sql-injection'}}");
    for (int i = 0; i < 20; i++) {
      assertThat(NaughtyStringHelper.CATEGORIES.get("SQL Injection")).contains(fetch(wm, "/t"));
    }
  }

  @Test
  void returnsTheSameStringForAFixedIndex() throws Exception {
    stub(wm, "{{naughtyString index=0}}");
    assertThat(fetch(wm, "/t")).isEqualTo("undefined").isEqualTo(fetch(wm, "/t"));
  }

  @Test
  void returnsAnErrorForBadInput() throws Exception {
    assertThat(render(wm, "{{naughtyString category='nope'}}")).startsWith("[ERROR: ");
    assertThat(render(wm, "{{naughtyString index=516}}")).startsWith("[ERROR: ");
  }

  @Test
  void toJsonMakesEveryStringSafeInAJsonBody() throws Exception {
    wm.stubFor(
        get(urlPathMatching("/json/\\d+"))
            .willReturn(
                aResponse()
                    .withBody("{ \"value\": {{{toJson (naughtyString index=request.path.[1])}}} }")
                    .withTransformers("response-template")));
    List<String> failures = new ArrayList<>();
    for (int i = 0; i < NaughtyStringHelper.ALL.size(); i++) {
      String expected = NaughtyStringHelper.ALL.get(i);
      String body = fetch(wm, "/json/" + i);
      try {
        String actual = JSON.readTree(body).get("value").textValue();
        if (!expected.equals(actual)) failures.add(i + ": " + body);
      } catch (IOException e) {
        failures.add(i + ": " + body + " (" + e.getMessage() + ")");
      }
    }
    assertThat(failures).isEmpty();
  }

  @Test
  void jsonBodyStringValueIsNotSpecialCased() throws Exception {
    // A whole-string "{{{toJson ...}}}" value in jsonBody keeps its surrounding quotes, so the
    // quotes double up. Use a plain `body` instead, as the README says.
    wm.stubFor(
        get(urlPathMatching("/t"))
            .willReturn(
                aResponse()
                    .withJsonBody(
                        Json.node("{\"value\": \"{{{toJson (naughtyString index=0)}}}\"}"))
                    .withTransformers("response-template")));
    assertThat(fetch(wm, "/t")).isEqualTo("{\"value\":\"\"undefined\"\"}");
  }

  @Test
  void isFoundByExtensionScanning() throws Exception {
    assertThat(render(scanned, "{{naughtyString index=0}}")).isEqualTo("undefined");
  }

  private static String render(WireMockExtension server, String template) throws Exception {
    stub(server, template);
    return fetch(server, "/t");
  }

  private static void stub(WireMockExtension server, String template) {
    server.stubFor(
        get(urlPathMatching("/t"))
            .willReturn(aResponse().withBody(template).withTransformers("response-template")));
  }

  private static String fetch(WireMockExtension server, String path) throws Exception {
    HttpResponse<String> response =
        HTTP.send(
            HttpRequest.newBuilder(URI.create(server.baseUrl() + path)).build(),
            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    return response.body();
  }
}
