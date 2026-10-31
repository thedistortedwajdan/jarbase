package io.github.thedistortedwajdan.jarbase.core.install;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MarkedBlockTest {

    private final String block = MarkedBlock.wrap("hello");

    @Test
    void wrapAddsTheMarkers() {
        assertThat(block).isEqualTo("<!-- jarbase:start -->\nhello\n<!-- jarbase:end -->");
    }

    @Test
    void newFileGetsJustTheBlock() {
        assertThat(MarkedBlock.apply(null, block)).isEqualTo(block + "\n");
        assertThat(MarkedBlock.apply("  \n", block)).isEqualTo(block + "\n");
    }

    @Test
    void existingTextIsKeptAndTheBlockAppended() {
        String result = MarkedBlock.apply("# My project\n\nSome notes.\n", block);

        assertThat(result).isEqualTo("# My project\n\nSome notes.\n\n" + block + "\n");
    }

    @Test
    void anExistingBlockIsReplacedInPlace() {
        String old = "# Top\n\n" + MarkedBlock.wrap("old text") + "\n\n## Below\nkeep me\n";

        String result = MarkedBlock.apply(old, block);

        assertThat(result).isEqualTo("# Top\n\n" + block + "\n\n## Below\nkeep me\n");
    }

    @Test
    void applyingTwiceChangesNothing() {
        String once = MarkedBlock.apply("# Notes\n", block);

        assertThat(MarkedBlock.apply(once, block)).isEqualTo(once);
    }

    @Test
    void aStartMarkerWithoutAnEndMarkerIsNotTreatedAsABlock() {
        String result = MarkedBlock.apply("<!-- jarbase:start -->\nbroken\n", block);

        assertThat(result).endsWith(block + "\n");
        assertThat(result).startsWith("<!-- jarbase:start -->\nbroken");
    }
}
