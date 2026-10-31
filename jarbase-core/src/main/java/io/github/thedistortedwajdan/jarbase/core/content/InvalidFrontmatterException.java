package io.github.thedistortedwajdan.jarbase.core.content;

/** Thrown when a page's settings block exists but cannot be understood. */
public class InvalidFrontmatterException extends RuntimeException {

    public InvalidFrontmatterException(String message) {
        super(message);
    }

    public InvalidFrontmatterException(String message, Throwable cause) {
        super(message, cause);
    }
}
