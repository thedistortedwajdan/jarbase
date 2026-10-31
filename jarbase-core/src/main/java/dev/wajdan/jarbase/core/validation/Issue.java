package dev.wajdan.jarbase.core.validation;

/**
 * One problem found in the docs.
 *
 * @param severity ERROR fails the check; WARNING is advice
 * @param page     the page the problem is in, relative to the repository root
 * @param line     1-based line in the page, or 0 when it applies to the page as a whole
 * @param rule     short name of the rule, for example {@code broken-link}
 * @param message  what is wrong, in plain words
 */
public record Issue(Severity severity, String page, int line, String rule, String message) {

    public enum Severity { ERROR, WARNING }

    public static Issue error(String page, int line, String rule, String message) {
        return new Issue(Severity.ERROR, page, line, rule, message);
    }

    public static Issue warning(String page, int line, String rule, String message) {
        return new Issue(Severity.WARNING, page, line, rule, message);
    }
}
