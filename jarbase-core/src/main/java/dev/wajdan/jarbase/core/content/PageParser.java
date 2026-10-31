package dev.wajdan.jarbase.core.content;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

/** Turns the text of a Markdown file into a {@link Page}. */
public final class PageParser {

    private static final String FENCE = "---";

    private PageParser() {
    }

    public static Page parse(String path, String markdown) {
        String text = normalize(markdown);

        Map<?, ?> raw = Map.of();
        String body = text;

        if (text.startsWith(FENCE + "\n")) {
            int closing = findClosingFence(text);
            if (closing >= 0) {
                int yamlStart = FENCE.length() + 1;
                // An empty block ("---\n---") has its closing fence right at the opening line's newline.
                String yaml = closing > yamlStart ? text.substring(yamlStart, closing) : "";
                raw = loadYaml(path, yaml);
                body = bodyAfter(text, closing);
            }
        }

        return new Page(path, toFrontmatter(path, raw, body), body);
    }

    /** Drops a leading byte-order mark and makes line endings uniform. */
    private static String normalize(String markdown) {
        String text = markdown.startsWith("﻿") ? markdown.substring(1) : markdown;
        return text.replace("\r\n", "\n");
    }

    /** Index of the newline that begins the closing fence line, or -1 if the block is never closed. */
    private static int findClosingFence(String text) {
        int from = FENCE.length();
        while (true) {
            int at = text.indexOf("\n" + FENCE, from);
            if (at < 0) {
                return -1;
            }
            int after = at + 1 + FENCE.length();
            if (after == text.length() || text.charAt(after) == '\n') {
                return at;
            }
            from = at + 1;
        }
    }

    private static String bodyAfter(String text, int closingNewline) {
        int afterFence = closingNewline + 1 + FENCE.length();
        return afterFence >= text.length() ? "" : text.substring(afterFence + 1);
    }

    private static Map<?, ?> loadYaml(String path, String yaml) {
        Object loaded;
        try {
            loaded = new Yaml(new SafeConstructor(new LoaderOptions())).load(yaml);
        } catch (YAMLException e) {
            throw new InvalidFrontmatterException("Invalid frontmatter in " + path + ": " + e.getMessage(), e);
        }
        if (loaded == null) {
            return Map.of();
        }
        if (loaded instanceof Map<?, ?> map) {
            return map;
        }
        throw new InvalidFrontmatterException("Frontmatter in " + path + " must be a set of key: value pairs");
    }

    private static Frontmatter toFrontmatter(String path, Map<?, ?> raw, String body) {
        String title = asString(raw.get("title"));
        if (title == null) {
            title = firstHeading(body);
        }
        return new Frontmatter(
                title,
                asString(raw.get("description")),
                asInteger(path, "order", raw.get("order")),
                asStringList(raw.get("sources")),
                asStringList(raw.get("tags")),
                Boolean.TRUE.equals(raw.get("hidden")));
    }

    private static String firstHeading(String body) {
        for (String line : body.split("\n")) {
            if (line.startsWith("# ")) {
                return line.substring(2).trim();
            }
        }
        return null;
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private static Integer asInteger(String path, String key, Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        throw new InvalidFrontmatterException("'" + key + "' in " + path + " must be a number");
    }

    /** Accepts a single value ({@code sources: a/**}) or a list, and always returns a list. */
    private static List<String> asStringList(Object value) {
        List<String> result = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (item != null) {
                    result.add(item.toString());
                }
            }
        } else if (value != null) {
            result.add(value.toString());
        }
        return result;
    }
}
