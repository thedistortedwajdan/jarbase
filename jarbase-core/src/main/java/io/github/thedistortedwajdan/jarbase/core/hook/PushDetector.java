package io.github.thedistortedwajdan.jarbase.core.hook;

import java.util.Set;

/**
 * Decides whether a shell command sends work out of the machine: {@code git push},
 * {@code gh pr create}, {@code glab mr create} or {@code hub pull-request}, including inside
 * chained commands such as {@code mvn test && git push}.
 */
public final class PushDetector {

    /** Things people put in front of a command that do not change what it is. */
    private static final Set<String> PREFIXES = Set.of("sudo", "time", "command", "exec", "nohup");

    /** git options that take their value as a separate word, for example {@code git -C dir push}. */
    private static final Set<String> GIT_OPTIONS_WITH_VALUE = Set.of("-C", "-c", "--git-dir", "--work-tree", "--namespace");

    private PushDetector() {
    }

    public static boolean isPush(String command) {
        if (command == null || command.isBlank()) {
            return false;
        }
        for (String segment : command.split("&&|\\|\\||[;|&\\n()`]")) {
            if (segmentPushes(segment.trim().split("\\s+"))) {
                return true;
            }
        }
        return false;
    }

    private static boolean segmentPushes(String[] words) {
        int i = 0;
        while (i < words.length && (isAssignment(words[i]) || PREFIXES.contains(words[i]))) {
            i++;
        }
        if (i >= words.length) {
            return false;
        }
        String program = programName(words[i]);
        return switch (program) {
            case "git" -> gitSubcommand(words, i + 1).equals("push");
            case "gh" -> at(words, i + 1).equals("pr") && at(words, i + 2).equals("create");
            case "glab" -> at(words, i + 1).equals("mr") && at(words, i + 2).equals("create");
            case "hub" -> at(words, i + 1).equals("pull-request");
            default -> false;
        };
    }

    /** The first word after git's own options, for example {@code push} in {@code git -C app push origin}. */
    private static String gitSubcommand(String[] words, int from) {
        int i = from;
        while (i < words.length && words[i].startsWith("-")) {
            i += GIT_OPTIONS_WITH_VALUE.contains(words[i]) ? 2 : 1;
        }
        return at(words, i);
    }

    private static String at(String[] words, int index) {
        return index < words.length ? words[index] : "";
    }

    private static boolean isAssignment(String word) {
        int eq = word.indexOf('=');
        return eq > 0 && word.substring(0, eq).matches("[A-Za-z_][A-Za-z0-9_]*");
    }

    /** {@code /usr/bin/git} and {@code C:\\Git\\git.exe} both become {@code git}. */
    private static String programName(String word) {
        String name = word.substring(Math.max(word.lastIndexOf('/'), word.lastIndexOf('\\')) + 1);
        return name.endsWith(".exe") ? name.substring(0, name.length() - 4) : name;
    }
}
