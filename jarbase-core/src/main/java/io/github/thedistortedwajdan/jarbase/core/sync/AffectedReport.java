package io.github.thedistortedwajdan.jarbase.core.sync;

import io.github.thedistortedwajdan.jarbase.core.json.JsonText;
import java.util.List;

/**
 * The answer to "which docs pages describe the code I changed?", ready to print.
 *
 * @param base  what the changes were measured against, for example {@code main}
 * @param pages the affected pages
 */
public record AffectedReport(String base, List<AffectedPage> pages) {

    public AffectedReport {
        pages = List.copyOf(pages);
    }

    /** Pages whose code changed but whose own file did not. */
    public long staleCount() {
        return pages.stream().filter(p -> !p.updated()).count();
    }

    public boolean hasStalePages() {
        return staleCount() > 0;
    }

    public String text() {
        if (pages.isEmpty()) {
            return "No docs pages are affected by these changes.\n";
        }
        StringBuilder out = new StringBuilder();
        out.append(pages.size()).append(pages.size() == 1 ? " docs page describes" : " docs pages describe")
                .append(" code you changed:\n");
        for (AffectedPage affected : pages) {
            out.append('\n')
                    .append("  ").append(affected.page().path())
                    .append(affected.updated() ? "  [updated]" : "  [NOT UPDATED]").append('\n');
            out.append("    sources: ").append(String.join(", ", affected.matchedSources())).append('\n');
            for (String file : affected.changedFiles()) {
                out.append("    changed: ").append(file).append('\n');
            }
        }
        long stale = staleCount();
        out.append('\n');
        if (stale == 0) {
            out.append("All affected pages were updated.\n");
        } else {
            out.append(stale).append(stale == 1 ? " page needs" : " pages need").append(" an update.\n");
        }
        return out.toString();
    }

    public String json() {
        StringBuilder out = new StringBuilder();
        out.append("{\"base\":").append(quote(base)).append(",\"stale\":").append(staleCount()).append(",\"affected\":[");
        for (int i = 0; i < pages.size(); i++) {
            AffectedPage affected = pages.get(i);
            if (i > 0) {
                out.append(',');
            }
            out.append("{\"page\":").append(quote(affected.page().path()))
                    .append(",\"title\":").append(affected.page().frontmatter().title() == null
                            ? "null" : quote(affected.page().frontmatter().title()))
                    .append(",\"updated\":").append(affected.updated())
                    .append(",\"matchedSources\":").append(array(affected.matchedSources()))
                    .append(",\"changedFiles\":").append(array(affected.changedFiles()))
                    .append('}');
        }
        return out.append("]}\n").toString();
    }

    private static String array(List<String> values) {
        StringBuilder out = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                out.append(',');
            }
            out.append(quote(values.get(i)));
        }
        return out.append(']').toString();
    }

    private static String quote(String value) {
        return JsonText.quote(value);
    }
}
