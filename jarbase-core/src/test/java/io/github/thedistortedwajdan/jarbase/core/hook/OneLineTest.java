package io.github.thedistortedwajdan.jarbase.core.hook;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OneLineTest {

    @Test
    void anEmDashFromTheAgentBecomesAHyphen() {
        // the text Claude Code printed in the live test, which a Windows console showed as "ΓÇö"
        assertThat(DocsFixer.oneLine("only Edit(path) rules are — use Edit(docs/**) instead"))
                .isEqualTo("only Edit(path) rules are - use Edit(docs/**) instead");
    }

    @Test
    void curlyQuotesBecomeStraightOnes() {
        assertThat(DocsFixer.oneLine("“quoted” and ‘single’")).isEqualTo("\"quoted\" and 'single'");
    }

    @Test
    void otherNonAsciiCharactersAndLineBreaksBecomeSpaces() {
        assertThat(DocsFixer.oneLine("café\nline two\t中")).isEqualTo("caf line two");
    }

    @Test
    void longTextIsCut() {
        assertThat(DocsFixer.oneLine("x".repeat(500))).hasSize(303).endsWith("...");
    }
}
