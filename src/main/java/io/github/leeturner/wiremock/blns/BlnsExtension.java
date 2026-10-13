package io.github.leeturner.wiremock.blns;

import com.github.jknack.handlebars.Helper;
import com.github.tomakehurst.wiremock.extension.TemplateHelperProviderExtension;
import java.util.Map;

/** Adds the {@code naughtyString} Handlebars helper to WireMock response templates. */
public class BlnsExtension implements TemplateHelperProviderExtension {

  @Override
  public String getName() {
    return "blns";
  }

  @Override
  public Map<String, Helper<?>> provideTemplateHelpers() {
    return Map.of("naughtyString", new NaughtyStringHelper());
  }
}
