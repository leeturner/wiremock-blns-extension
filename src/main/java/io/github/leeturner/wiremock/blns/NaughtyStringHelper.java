package io.github.leeturner.wiremock.blns;

import com.github.jknack.handlebars.Options;
import com.github.tomakehurst.wiremock.extension.responsetemplating.helpers.HandlebarsHelper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

final class NaughtyStringHelper extends HandlebarsHelper<Object> {

  /** BLNS category heading to its strings, in file order. Parsed once per JVM. */
  static final Map<String, List<String>> CATEGORIES = load();

  /** Every string, in file order. */
  static final List<String> ALL = CATEGORIES.values().stream().flatMap(List::stream).toList();

  /** Lowercased heading and slug ("sql injection", "sql-injection") to the category's strings. */
  private static final Map<String, List<String>> BY_NAME = new HashMap<>();

  static {
    CATEGORIES.forEach(
        (title, strings) -> {
          BY_NAME.put(title.toLowerCase(Locale.ROOT), strings);
          BY_NAME.put(slug(title), strings);
        });
  }

  @Override
  public Object apply(Object context, Options options) {
    List<String> strings = ALL;
    Object category = options.hash("category");
    if (category != null) {
      strings = BY_NAME.get(category.toString().toLowerCase(Locale.ROOT));
      if (strings == null) {
        return handleError(
            "Unknown BLNS category '"
                + category
                + "'. Known: "
                + String.join(
                    ", ", CATEGORIES.keySet().stream().map(NaughtyStringHelper::slug).toList()));
      }
    }
    Object index = options.hash("index");
    if (index == null) {
      return strings.get(ThreadLocalRandom.current().nextInt(strings.size()));
    }
    int i;
    try {
      i = index instanceof Number n ? n.intValue() : Integer.parseInt(index.toString());
    } catch (NumberFormatException e) {
      return handleError("BLNS index '" + index + "' is not an integer");
    }
    if (i < 0 || i >= strings.size()) {
      return handleError("BLNS index " + i + " is out of range 0-" + (strings.size() - 1));
    }
    return strings.get(i);
  }

  /** "Command Injection (Ruby)" becomes "command-injection-ruby". */
  static String slug(String title) {
    return title.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
  }

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
