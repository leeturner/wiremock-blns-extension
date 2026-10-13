package io.github.leeturner.wiremock.blns;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.jknack.handlebars.EscapingStrategy;
import com.github.jknack.handlebars.Handlebars;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class NaughtyStringHelperTest {

  @Test
  void parsesEveryCategoryInFileOrder() {
    assertThat(NaughtyStringHelper.CATEGORIES.keySet())
        .containsExactly(
            "Reserved Strings",
            "Numeric Strings",
            "Special Characters",
            "Unicode Symbols",
            "Unicode Subscript/Superscript/Accents",
            "Quotation Marks",
            "Two-Byte Characters",
            "Special Unicode Characters Union",
            "Changing length when lowercased",
            "Japanese Emoticons",
            "Emoji",
            "Unicode Numbers",
            "Right-To-Left Strings",
            "Ogham Text",
            "Trick Unicode",
            "Zalgo Text",
            "Unicode Upsidedown",
            "Unicode font",
            "Script Injection",
            "SQL Injection",
            "Server Code Injection",
            "Command Injection (Ruby)",
            "Unwanted Interpolation",
            "File Inclusion",
            "Known CVEs and Vulnerabilities",
            "MSDOS/Windows Special Filenames",
            "Scunthorpe Problem",
            "Human injection",
            "Terminal escape codes",
            "iOS Vulnerabilities");
  }

  @Test
  void parsesEveryString() {
    assertThat(NaughtyStringHelper.ALL).hasSize(516);
    assertThat(
            NaughtyStringHelper.CATEGORIES.values().stream().mapToInt(java.util.List::size).sum())
        .isEqualTo(516);
  }

  @Test
  void putsStringsInTheirCategory() {
    assertThat(NaughtyStringHelper.CATEGORIES.get("Reserved Strings"))
        .startsWith("undefined", "undef", "null");
    assertThat(NaughtyStringHelper.CATEGORIES.get("SQL Injection")).contains("1;DROP TABLE users");
  }

  @Test
  void keepsWhitespaceOnlyStrings() {
    assertThat(NaughtyStringHelper.CATEGORIES.get("SQL Injection")).contains(" ");
  }

  @Test
  void treatsVmwareDescriptionAsACommentNotACategory() {
    assertThat(NaughtyStringHelper.CATEGORIES.get("Special Unicode Characters Union"))
        .containsExactly("表ポあA鷗ŒéＢ逍Üßªąñ丂㐀𠀀");
  }

  @Test
  void returnsARandomStringFromTheWholeList() throws IOException {
    assertThat(NaughtyStringHelper.ALL).contains(render("{{naughtyString}}"));
  }

  @Test
  void matchesCategoryByHeadingOrSlugIgnoringCase() throws IOException {
    var sql = NaughtyStringHelper.CATEGORIES.get("SQL Injection");
    assertThat(sql).contains(render("{{naughtyString category='SQL Injection'}}"));
    assertThat(sql).contains(render("{{naughtyString category='sql-injection'}}"));
    assertThat(sql).contains(render("{{naughtyString category='SQL-INJECTION'}}"));
    assertThat(render("{{naughtyString category='command-injection-ruby' index=0}}"))
        .isEqualTo(NaughtyStringHelper.CATEGORIES.get("Command Injection (Ruby)").get(0));
  }

  @Test
  void returnsAFixedEntryByIndex() throws IOException {
    assertThat(render("{{naughtyString index=2}}")).isEqualTo("null");
    assertThat(render("{{naughtyString index='2'}}")).isEqualTo("null");
    assertThat(render("{{naughtyString category='Emoji' index=0}}"))
        .isEqualTo(NaughtyStringHelper.CATEGORIES.get("Emoji").get(0));
  }

  @Test
  void returnsAnErrorForBadInput() throws IOException {
    assertThat(render("{{naughtyString category='nope'}}"))
        .startsWith("[ERROR: Unknown BLNS category 'nope'. Known: reserved-strings, ");
    assertThat(render("{{naughtyString index=516}}")).startsWith("[ERROR: ");
    assertThat(render("{{naughtyString index=-1}}")).startsWith("[ERROR: ");
    assertThat(render("{{naughtyString category='emoji' index=1000}}")).startsWith("[ERROR: ");
    assertThat(render("{{naughtyString index='x'}}")).startsWith("[ERROR: ");
  }

  private static String render(String template) throws IOException {
    // NOOP escaping, as WireMock's TemplateEngine uses.
    return new Handlebars()
        .with(EscapingStrategy.NOOP)
        .registerHelper("naughtyString", new NaughtyStringHelper())
        .compileInline(template)
        .apply(null);
  }
}
