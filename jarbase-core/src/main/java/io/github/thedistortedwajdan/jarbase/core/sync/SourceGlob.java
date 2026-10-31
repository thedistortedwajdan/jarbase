package io.github.thedistortedwajdan.jarbase.core.sync;

import java.util.regex.Pattern;

/**
 * One entry from a page's {@code sources:} list, for example {@code src/main/java/com/acme/billing/**}.
 *
 * <p>Rules (the same on every operating system, because paths are compared with forward slashes):
 * <ul>
 *   <li>{@code *} matches any characters within one folder level</li>
 *   <li>{@code **} matches across folders; {@code **}{@code /} also matches zero folders</li>
 *   <li>{@code ?} matches one character</li>
 *   <li>{@code {a,b}} matches either alternative</li>
 *   <li>a trailing {@code /} means everything beneath that folder</li>
 *   <li>{@code path#anchor}: the part from {@code #} on is ignored for matching</li>
 * </ul>
 */
public final class SourceGlob {

    private final String spec;
    private final Pattern pattern;

    private SourceGlob(String spec, Pattern pattern) {
        this.spec = spec;
        this.pattern = pattern;
    }

    public static SourceGlob of(String spec) {
        String glob = spec;
        int anchor = glob.indexOf('#');
        if (anchor >= 0) {
            glob = glob.substring(0, anchor);
        }
        glob = normalizePath(glob);
        if (glob.isEmpty()) {
            throw new IllegalArgumentException("Empty source pattern: '" + spec + "'");
        }
        if (glob.endsWith("/")) {
            glob += "**";
        }
        return new SourceGlob(spec, Pattern.compile(toRegex(glob)));
    }

    public boolean matches(String filePath) {
        return pattern.matcher(normalizePath(filePath)).matches();
    }

    /** The pattern exactly as the author wrote it. */
    public String spec() {
        return spec;
    }

    /** Forward slashes only, no leading "./" or "/", no surrounding spaces. */
    public static String normalizePath(String path) {
        String p = path.trim().replace('\\', '/');
        while (p.startsWith("./")) {
            p = p.substring(2);
        }
        while (p.startsWith("/")) {
            p = p.substring(1);
        }
        return p;
    }

    private static String toRegex(String glob) {
        StringBuilder re = new StringBuilder();
        int braceDepth = 0;
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            switch (c) {
                case '*' -> {
                    if (i + 1 < glob.length() && glob.charAt(i + 1) == '*') {
                        i++;
                        if (i + 1 < glob.length() && glob.charAt(i + 1) == '/') {
                            i++;
                            re.append("(?:.*/)?");
                        } else {
                            re.append(".*");
                        }
                    } else {
                        re.append("[^/]*");
                    }
                }
                case '?' -> re.append("[^/]");
                case '{' -> {
                    braceDepth++;
                    re.append("(?:");
                }
                case '}' -> {
                    if (braceDepth == 0) {
                        re.append("\\}");
                    } else {
                        braceDepth--;
                        re.append(')');
                    }
                }
                case ',' -> re.append(braceDepth > 0 ? "|" : ",");
                default -> {
                    if ("\\.[]()+^$|".indexOf(c) >= 0) {
                        re.append('\\');
                    }
                    re.append(c);
                }
            }
        }
        if (braceDepth != 0) {
            throw new IllegalArgumentException("Unclosed '{' in source pattern: '" + glob + "'");
        }
        return re.toString();
    }
}
