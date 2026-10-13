package io.github.leeturner.wiremock.blns;

import static org.assertj.core.api.Assertions.assertThat;

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
}
