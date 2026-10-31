package dev.wajdan.jarbase.core.sync;

import static org.assertj.core.api.Assertions.assertThat;

import dev.wajdan.jarbase.core.content.Page;
import dev.wajdan.jarbase.core.content.PageParser;
import java.util.List;
import org.junit.jupiter.api.Test;

class AffectedReportTest {

    private static Page page(String path, String title) {
        return PageParser.parse(path, "---\ntitle: " + title + "\n---\n");
    }

    private final AffectedPage stale = new AffectedPage(
            page("docs/billing.md", "Billing"), List.of("src/billing/**"), List.of("src/billing/Invoice.java"), false);
    private final AffectedPage fresh = new AffectedPage(
            page("docs/users.md", "Users"), List.of("src/users/**"), List.of("src/users/User.java"), true);

    @Test
    void emptyReportSaysNothingIsAffected() {
        AffectedReport report = new AffectedReport("main", List.of());

        assertThat(report.text()).isEqualTo("No docs pages are affected by these changes.\n");
        assertThat(report.hasStalePages()).isFalse();
        assertThat(report.json()).isEqualTo("{\"base\":\"main\",\"stale\":0,\"affected\":[]}\n");
    }

    @Test
    void staleCountIgnoresUpdatedPages() {
        AffectedReport report = new AffectedReport("main", List.of(stale, fresh));

        assertThat(report.staleCount()).isEqualTo(1);
        assertThat(report.hasStalePages()).isTrue();
    }

    @Test
    void textListsEachPageWithItsStatus() {
        String text = new AffectedReport("main", List.of(stale, fresh)).text();

        assertThat(text).contains("2 docs pages describe code you changed:");
        assertThat(text).contains("docs/billing.md  [NOT UPDATED]");
        assertThat(text).contains("docs/users.md  [updated]");
        assertThat(text).contains("sources: src/billing/**");
        assertThat(text).contains("changed: src/billing/Invoice.java");
        assertThat(text).contains("1 page needs an update.");
    }

    @Test
    void textSaysAllGoodWhenEverythingWasUpdated() {
        String text = new AffectedReport("main", List.of(fresh)).text();

        assertThat(text).contains("1 docs page describes code you changed:");
        assertThat(text).contains("All affected pages were updated.");
    }

    @Test
    void jsonHasOneEntryPerPage() {
        String json = new AffectedReport("main", List.of(stale)).json();

        assertThat(json).isEqualTo("{\"base\":\"main\",\"stale\":1,\"affected\":[{"
                + "\"page\":\"docs/billing.md\",\"title\":\"Billing\",\"updated\":false,"
                + "\"matchedSources\":[\"src/billing/**\"],"
                + "\"changedFiles\":[\"src/billing/Invoice.java\"]}]}\n");
    }

    @Test
    void jsonEscapesQuotesAndBackslashes() {
        AffectedPage odd = new AffectedPage(
                page("docs/a.md", "x"), List.of("weird\"name\\"), List.of("f"), false);

        assertThat(new AffectedReport("main", List.of(odd)).json()).contains("\"weird\\\"name\\\\\"");
    }
}
