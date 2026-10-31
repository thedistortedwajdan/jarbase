package dev.wajdan.jarbase.core.git;

/** Thrown when a git command fails or git cannot be run. */
public class GitException extends RuntimeException {

    public GitException(String message) {
        super(message);
    }

    public GitException(String message, Throwable cause) {
        super(message, cause);
    }
}
