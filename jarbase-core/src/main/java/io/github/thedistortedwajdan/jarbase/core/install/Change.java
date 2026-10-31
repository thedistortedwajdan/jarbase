package io.github.thedistortedwajdan.jarbase.core.install;

/**
 * One thing the installer did (or chose not to do), so it can tell the user.
 *
 * @param kind what happened
 * @param path the file, relative to the project root with forward slashes
 * @param note extra detail, or empty
 */
public record Change(Kind kind, String path, String note) {

    public enum Kind { CREATED, UPDATED, UNCHANGED, SKIPPED }

    public static Change of(Kind kind, String path) {
        return new Change(kind, path, "");
    }
}
