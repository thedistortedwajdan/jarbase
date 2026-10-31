package dev.wajdan.jarbase.core.validation;

import dev.wajdan.jarbase.core.content.DocsLoader;
import dev.wajdan.jarbase.core.content.InvalidFrontmatterException;
import dev.wajdan.jarbase.core.content.Page;
import dev.wajdan.jarbase.core.content.PageParser;
import dev.wajdan.jarbase.core.git.GitChanges;
import dev.wajdan.jarbase.core.sync.SourceGlob;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks that the docs are healthy, so an agent (or a person) can trust what they just edited.
 *
 * <p>Rules: {@code frontmatter} (unreadable settings block), {@code title} (page has no title),
 * {@code sources} (a bad pattern, or a pattern matching no files), {@code broken-link} (a link or image
 * pointing at a file that does not exist) and {@code empty-page}.
 */
public final class DocsValidator {

    /** {@code [text](target)} and {@code ![alt](target)}, with an optional {@code "title"} after the target. */
    private static final Pattern LINK = Pattern.compile("!?\\[[^\\]]*]\\(\\s*<?([^)\\s>]+)>?(?:\\s+\"[^\"]*\")?\\s*\\)");
    private static final Pattern URL_SCHEME = Pattern.compile("^[a-zA-Z][a-zA-Z0-9+.-]*:.*");
    private static final Pattern INLINE_CODE = Pattern.compile("`[^`\\n]*`");

    private DocsValidator() {
    }

    /** Checks the docs of the repository that contains {@code anyFolder}. */
    public static ValidationReport validate(Path anyFolder, String docsFolder) {
        Path root = GitChanges.repoRoot(anyFolder);
        return validate(root, docsFolder, GitChanges.repoFiles(root));
    }

    /**
     * @param root      the repository's top folder
     * @param docsFolder docs folder relative to {@code root}
     * @param repoFiles every file in the repository (repo-relative, forward slashes), used to check
     *                  {@code sources:} patterns and links
     */
    public static ValidationReport validate(Path root, String docsFolder, Collection<String> repoFiles) {
        Set<String> files = new HashSet<>(repoFiles);
        List<String> pagePaths = DocsLoader.markdownFiles(root, docsFolder);
        List<Issue> issues = new ArrayList<>();

        for (String pagePath : pagePaths) {
            checkPage(root, pagePath, files, issues);
        }

        issues.sort(Comparator.comparing(Issue::page).thenComparingInt(Issue::line).thenComparing(Issue::rule));
        return new ValidationReport(pagePaths.size(), issues);
    }

    private static void checkPage(Path root, String pagePath, Set<String> files, List<Issue> issues) {
        String raw = DocsLoader.readText(root, pagePath);

        Page page;
        try {
            page = PageParser.parse(pagePath, raw);
        } catch (InvalidFrontmatterException e) {
            issues.add(Issue.error(pagePath, 0, "frontmatter", e.getMessage()));
            return;
        }

        if (page.frontmatter().title() == null || page.frontmatter().title().isBlank()) {
            issues.add(Issue.error(pagePath, 0, "title", "page has no title: add `title:` or start with a `# Heading`"));
        }
        if (page.body().isBlank()) {
            issues.add(Issue.warning(pagePath, 0, "empty-page", "page has no content"));
        }

        checkSources(page, files, issues);

        int lineOffset = lineCount(raw.replace("\r\n", "\n")) - lineCount(page.body());
        checkLinks(page, lineOffset, files, issues);
    }

    private static void checkSources(Page page, Set<String> files, List<Issue> issues) {
        for (String spec : page.frontmatter().sources()) {
            SourceGlob glob;
            try {
                glob = SourceGlob.of(spec);
            } catch (IllegalArgumentException e) {
                issues.add(Issue.error(page.path(), 0, "sources", "bad pattern '" + spec + "': " + e.getMessage()));
                continue;
            }
            if (files.stream().noneMatch(glob::matches)) {
                issues.add(Issue.warning(page.path(), 0, "sources",
                        "'" + spec + "' matches no files, so this page can never be flagged as stale"));
            }
        }
    }

    private static void checkLinks(Page page, int lineOffset, Set<String> files, List<Issue> issues) {
        String pageDir = page.path().contains("/") ? page.path().substring(0, page.path().lastIndexOf('/')) : "";
        String[] lines = page.body().split("\n", -1);
        boolean inFence = false;
        String fenceMarker = "";

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.stripLeading();
            if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
                String marker = trimmed.substring(0, 3);
                if (!inFence) {
                    inFence = true;
                    fenceMarker = marker;
                } else if (marker.equals(fenceMarker)) {
                    inFence = false;
                }
                continue;
            }
            if (inFence) {
                continue;
            }

            Matcher links = LINK.matcher(INLINE_CODE.matcher(line).replaceAll(""));
            while (links.find()) {
                String target = links.group(1);
                if (!resolves(pageDir, target, files)) {
                    issues.add(Issue.error(page.path(), lineOffset + i + 1, "broken-link",
                            "'" + target + "' does not exist"));
                }
            }
        }
    }

    /** True for links we do not check (web links, anchors, site routes) and for links to files that exist. */
    private static boolean resolves(String pageDir, String rawTarget, Set<String> files) {
        String target = rawTarget;
        int cut = firstIndexOf(target, '#', '?');
        if (cut >= 0) {
            target = target.substring(0, cut);
        }
        if (target.isEmpty() || URL_SCHEME.matcher(target).matches() || target.startsWith("//") || target.startsWith("/")) {
            return true;
        }
        try {
            target = URLDecoder.decode(target.replace("+", "%2B"), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return false;
        }

        String resolved = resolve(pageDir, target);
        if (resolved == null) {
            return false; // climbs out of the repository
        }
        String bare = resolved.endsWith("/") ? resolved.substring(0, resolved.length() - 1) : resolved;
        return files.contains(bare)
                || files.contains(bare + ".md")
                || files.contains(bare + "/index.md")
                || files.contains(bare + "/README.md");
    }

    /** Joins a link onto the page's folder and removes {@code .} and {@code ..}. Null if it leaves the repository. */
    private static String resolve(String pageDir, String target) {
        List<String> parts = new ArrayList<>();
        if (!pageDir.isEmpty()) {
            parts.addAll(List.of(pageDir.split("/")));
        }
        boolean trailingSlash = target.endsWith("/");
        for (String part : target.replace('\\', '/').split("/")) {
            if (part.isEmpty() || part.equals(".")) {
                continue;
            }
            if (part.equals("..")) {
                if (parts.isEmpty()) {
                    return null;
                }
                parts.remove(parts.size() - 1);
            } else {
                parts.add(part);
            }
        }
        return String.join("/", parts) + (trailingSlash ? "/" : "");
    }

    private static int firstIndexOf(String text, char a, char b) {
        int ia = text.indexOf(a);
        int ib = text.indexOf(b);
        if (ia < 0) {
            return ib;
        }
        return ib < 0 ? ia : Math.min(ia, ib);
    }

    private static int lineCount(String text) {
        return text.split("\n", -1).length;
    }
}
