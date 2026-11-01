package io.github.thedistortedwajdan.jarbase.core.config;

import io.github.thedistortedwajdan.jarbase.core.Jarbase;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/**
 * jarbase's settings. They live in {@code .jarbase/config.properties}, which is committed so the whole team
 * shares them. A teammate can override any of them for their own machine with an environment variable.
 *
 * @param review         what a coding agent is told to do about docs
 * @param onPush         what {@code git push} does when docs are stale
 * @param agent          which coding agent to use for {@link OnPush#FIX}, or null for the first one found
 * @param timeoutSeconds how long the agent may take before jarbase gives up and just warns
 */
public record JarbaseConfig(Review review, OnPush onPush, String agent, int timeoutSeconds) {

    public static final String FILE = Jarbase.HOME_FOLDER + "/config.properties";

    /** What a coding agent is told to do about docs. */
    public enum Review {
        /** Update the docs, commit them with the code and push, without asking. */
        AUTO,
        /** Update the docs, show the diff and wait for approval. */
        REQUIRED
    }

    /** What {@code git push} does when docs are stale. */
    public enum OnPush {
        /** Run the coding agent to update the docs, commit them and push. Falls back to a warning. */
        FIX,
        /** Stop the push once, then let the next push through. */
        BLOCK,
        /** Print a warning and push anyway. */
        WARN
    }

    public JarbaseConfig {
        if (timeoutSeconds < 1) {
            throw new IllegalArgumentException("timeoutSeconds must be at least 1");
        }
    }

    public static JarbaseConfig defaults() {
        return new JarbaseConfig(Review.AUTO, OnPush.FIX, null, 180);
    }

    /** The settings for the project at {@code root}: the file (or the defaults), then environment overrides. */
    public static JarbaseConfig load(Path root, Map<String, String> env) {
        Properties properties = new Properties();
        Path file = root.resolve(FILE);
        if (Files.isRegularFile(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                properties.load(reader);
            } catch (IOException e) {
                throw new UncheckedIOException("Could not read " + FILE, e);
            }
        }

        JarbaseConfig defaults = defaults();
        Review review = choose(Review.class, pick(env, "JARBASE_REVIEW", properties, "review"), defaults.review, "review");
        OnPush onPush = choose(OnPush.class, pick(env, "JARBASE_ON_PUSH", properties, "onPush"), defaults.onPush, "onPush");
        String agent = pick(env, "JARBASE_AGENT", properties, "agent");
        String timeout = pick(env, "JARBASE_TIMEOUT_SECONDS", properties, "timeoutSeconds");
        return new JarbaseConfig(review, onPush, agent, timeout == null ? defaults.timeoutSeconds : seconds(timeout));
    }

    /** The text written to a new project's {@code .jarbase/config.properties}. */
    public static String defaultFileText() {
        return """
                # jarbase settings. Committed, so the whole team shares them.
                # A teammate can override any of these on their own machine with an environment variable:
                # JARBASE_REVIEW, JARBASE_ON_PUSH, JARBASE_AGENT, JARBASE_TIMEOUT_SECONDS.
                # JARBASE_HOOKS=off switches the hooks off for one session.

                # What a coding agent is told to do about docs.
                #   auto      update the docs, commit them with the code and push, without asking
                #   required  update the docs, show the diff and wait for approval
                review=auto

                # What `git push` does when docs describing the pushed code were not updated.
                #   fix    run your coding agent to update the docs, commit them and push (a warning if no agent works)
                #   block  stop the push once; the next push goes through
                #   warn   print a warning and push anyway
                onPush=fix

                # Which coding agent to use for `fix`. Empty means the first one found on this machine.
                agent=

                # How long the agent may take, in seconds, before jarbase gives up and only warns.
                timeoutSeconds=180
                """;
    }

    private static String pick(Map<String, String> env, String envName, Properties properties, String key) {
        String value = env.get(envName);
        if (value == null || value.isBlank()) {
            value = properties.getProperty(key);
        }
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static <E extends Enum<E>> E choose(Class<E> type, String value, E fallback, String key) {
        if (value == null) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("'" + value + "' is not a valid value for " + key + " in " + FILE
                    + "; use one of: " + String.join(", ", names(type)));
        }
    }

    private static <E extends Enum<E>> java.util.List<String> names(Class<E> type) {
        return java.util.Arrays.stream(type.getEnumConstants()).map(e -> e.name().toLowerCase(Locale.ROOT)).toList();
    }

    private static int seconds(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("'" + value + "' is not a number for timeoutSeconds in " + FILE);
        }
    }
}
