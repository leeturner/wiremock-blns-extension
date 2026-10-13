package io.github.leeturner.wiremock.blns;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class NaughtyStringHelper {

  /** BLNS category heading to its strings, in file order. Parsed once per JVM. */
  static final Map<String, List<String>> CATEGORIES = load();

  /** Every string, in file order. */
  static final List<String> ALL = CATEGORIES.values().stream().flatMap(List::stream).toList();

  private static Map<String, List<String>> load() {
    String text;
    try (InputStream in = NaughtyStringHelper.class.getResourceAsStream("/blns/blns.txt")) {
      text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    // Split only on \n: some strings contain U+0085, U+2028 or U+2029.
    String[] lines = text.split("\n");
    Map<String, List<String>> categories = new LinkedHashMap<>();
    List<String> current = null;
    for (int i = 0; i < lines.length; i++) {
      String line = lines[i];
      // A heading is "#\t<title>" then a bare "#". Requiring a blank line before it skips the
      // VMware description inside "Special Unicode Characters Union", which follows a "#" line.
      boolean heading =
          line.startsWith("#\t")
              && (i == 0 || lines[i - 1].isEmpty())
              && i + 1 < lines.length
              && lines[i + 1].equals("#");
      if (heading) {
        current = new ArrayList<>();
        categories.put(line.substring(2), current);
      } else if (!line.isEmpty() && !line.startsWith("#")) {
        current.add(line);
      }
    }
    categories.replaceAll((title, strings) -> List.copyOf(strings));
    return Collections.unmodifiableMap(categories);
  }
}
