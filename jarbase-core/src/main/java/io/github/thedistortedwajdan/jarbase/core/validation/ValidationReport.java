package io.github.thedistortedwajdan.jarbase.core.validation;

import io.github.thedistortedwajdan.jarbase.core.json.JsonText;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The outcome of checking the docs.
 *
 * @param pageCount how many pages were checked
 * @param issues    everything found, ordered by page then line
 */
public record ValidationReport(int pageCount, List<Issue> issues) {

    public ValidationReport {
        issues = List.copyOf(issues);
    }

    public long errorCount() {
        return issues.stream().filter(i -> i.severity() == Issue.Severity.ERROR).count();
    }

    public long warningCount() {
        return issues.stream().filter(i -> i.severity() == Issue.Severity.WARNING).count();
    }

    public boolean hasErrors() {
        return errorCount() > 0;
    }

    public String text() {
        if (issues.isEmpty()) {
            return "Docs look good: " + pageCount + (pageCount == 1 ? " page" : " pages") + " checked, no problems.\n";
        }
        Map<String, List<Issue>> byPage = new LinkedHashMap<>();
        for (Issue issue : issues) {
            byPage.computeIfAbsent(issue.page(), k -> new ArrayList<>()).add(issue);
        }
        StringBuilder out = new StringBuilder();
        byPage.forEach((page, pageIssues) -> {
            out.append(page).append('\n');
            for (Issue issue : pageIssues) {
                out.append(String.format("  %-8s %-14s %s%s%n",
                        issue.severity().name().toLowerCase(), issue.rule(), issue.message(),
                        issue.line() > 0 ? " (line " + issue.line() + ")" : ""));
            }
            out.append('\n');
        });
        out.append(errorCount()).append(errorCount() == 1 ? " error, " : " errors, ")
                .append(warningCount()).append(warningCount() == 1 ? " warning" : " warnings")
                .append(" in ").append(pageCount).append(pageCount == 1 ? " page" : " pages").append(".\n");
        return out.toString();
    }

    public String json() {
        StringBuilder out = new StringBuilder();
        out.append("{\"pages\":").append(pageCount)
                .append(",\"errors\":").append(errorCount())
                .append(",\"warnings\":").append(warningCount())
                .append(",\"issues\":[");
        for (int i = 0; i < issues.size(); i++) {
            Issue issue = issues.get(i);
            if (i > 0) {
                out.append(',');
            }
            out.append("{\"severity\":").append(JsonText.quote(issue.severity().name().toLowerCase()))
                    .append(",\"page\":").append(JsonText.quote(issue.page()))
                    .append(",\"line\":").append(issue.line())
                    .append(",\"rule\":").append(JsonText.quote(issue.rule()))
                    .append(",\"message\":").append(JsonText.quote(issue.message()))
                    .append('}');
        }
        return out.append("]}\n").toString();
    }
}
