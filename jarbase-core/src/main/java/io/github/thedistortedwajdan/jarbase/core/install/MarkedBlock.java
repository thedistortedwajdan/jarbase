package io.github.thedistortedwajdan.jarbase.core.install;

/**
 * A section of a shared file (like {@code AGENTS.md}) that jarbase owns, fenced by marker comments.
 * Re-running the installer replaces what is between the markers and never touches anything outside them.
 */
public final class MarkedBlock {

    public static final String START = "<!-- jarbase:start -->";
    public static final String END = "<!-- jarbase:end -->";

    private MarkedBlock() {
    }

    /** Wraps content in the markers. */
    public static String wrap(String content) {
        return START + "\n" + content.strip() + "\n" + END;
    }

    /**
     * @param existing the file's current text, or empty/null if the file does not exist
     * @param block    the new block, markers included (see {@link #wrap})
     * @return the file's new text: the block replaced in place if present, otherwise appended
     */
    public static String apply(String existing, String block) {
        if (existing == null || existing.isBlank()) {
            return block + "\n";
        }
        int start = existing.indexOf(START);
        int end = start < 0 ? -1 : existing.indexOf(END, start);
        if (start >= 0 && end >= 0) {
            return existing.substring(0, start) + block + existing.substring(end + END.length());
        }
        return existing.stripTrailing() + "\n\n" + block + "\n";
    }
}
