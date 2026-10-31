package dev.wajdan.jarbase.core.sync;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The whole pipeline against a real throwaway git repository. */
class AffectedServiceTest {

    @TempDir
    Path repo;

    @BeforeEach
    void repoWithADocsPageOnMain() throws Exception {
        git("init", "-q", "-b", "main");
        git("config", "user.name", "Test");
        git("config", "user.email", "test@example.com");
        git("config", "commit.gpgsign", "false");
        write("docs/billing.md", "---\nsources: [src/billing/**]\n---\n# Billing\n");
        write("docs/users.md", "---\nsources: [src/users/**]\n---\n# Users\n");
        write("src/billing/Invoice.java", "class Invoice {}");
        write("src/users/User.java", "class User {}");
        commitAll("initial");
        git("checkout", "-q", "-b", "feature");
    }

    @Test
    void codeChangeWithoutDocsChangeIsStale() throws Exception {
        write("src/billing/Invoice.java", "class Invoice { int total; }");
        commitAll("change billing");

        AffectedReport report = AffectedService.run(repo, "docs", "main", false);

        assertThat(report.pages()).hasSize(1);
        assertThat(report.pages().get(0).page().path()).isEqualTo("docs/billing.md");
        assertThat(report.hasStalePages()).isTrue();
    }

    @Test
    void codeAndDocsChangedTogetherIsNotStale() throws Exception {
        write("src/billing/Invoice.java", "class Invoice { int total; }");
        write("docs/billing.md", "---\nsources: [src/billing/**]\n---\n# Billing\nNow with totals.\n");
        commitAll("change billing and its docs");

        AffectedReport report = AffectedService.run(repo, "docs", "main", false);

        assertThat(report.pages()).hasSize(1);
        assertThat(report.pages().get(0).updated()).isTrue();
        assertThat(report.hasStalePages()).isFalse();
    }

    @Test
    void worksFromASubfolderOfTheRepository() throws Exception {
        write("src/users/User.java", "class User { String name; }");
        commitAll("change users");

        AffectedReport report = AffectedService.run(repo.resolve("src/users"), "docs", "main", false);

        assertThat(report.pages()).extracting(p -> p.page().path()).containsExactly("docs/users.md");
    }

    @Test
    void unrelatedChangesAffectNothing() throws Exception {
        write("build.gradle", "// nothing documented here");
        commitAll("tooling");

        assertThat(AffectedService.run(repo, "docs", "main", false).pages()).isEmpty();
    }

    // ---- helpers ----

    private void write(String relativePath, String content) throws IOException {
        Path file = repo.resolve(relativePath);
        Files.createDirectories(file.getParent() == null ? repo : file.getParent());
        Files.writeString(file, content);
    }

    private void commitAll(String message) throws Exception {
        git("add", "-A");
        git("commit", "-q", "-m", message);
    }

    private void git(String... args) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command).directory(repo.toFile()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes());
        if (!process.waitFor(30, TimeUnit.SECONDS) || process.exitValue() != 0) {
            throw new IllegalStateException("git " + String.join(" ", args) + " failed: " + output);
        }
    }
}
