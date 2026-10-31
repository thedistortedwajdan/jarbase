package dev.wajdan.jarbase.core.hook;

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

/** The blocking rule, against a real throwaway git repository. */
class PrePushCheckTest {

    @TempDir
    Path repo;

    @BeforeEach
    void repoWithBillingDocsOnAFeatureBranch() throws Exception {
        git("init", "-q", "-b", "main");
        git("config", "user.name", "Test");
        git("config", "user.email", "test@example.com");
        git("config", "commit.gpgsign", "false");
        write("docs/billing.md", "---\nsources: [src/billing/**]\n---\n# Billing\n");
        write("src/billing/Invoice.java", "class Invoice {}");
        commitAll("initial");
        git("checkout", "-q", "-b", "feature");
    }

    @Test
    void nothingChangedAllowsThePush() {
        assertThat(PrePushCheck.decide(repo, "docs", null).block()).isFalse();
    }

    @Test
    void unrelatedChangesAllowThePush() throws Exception {
        write("pom.xml", "<project/>");
        commitAll("build file");

        assertThat(PrePushCheck.decide(repo, "docs", null).block()).isFalse();
    }

    @Test
    void codeChangeWithoutDocsBlocksAndNamesThePage() throws Exception {
        write("src/billing/Invoice.java", "class Invoice { int total; }");
        commitAll("change billing");

        HookDecision decision = PrePushCheck.decide(repo, "docs", null);

        assertThat(decision.block()).isTrue();
        assertThat(decision.reason()).contains("docs/billing.md").contains("src/billing/**")
                .contains("the next push is allowed");
    }

    @Test
    void theSameSituationIsBlockedOnlyOnce() throws Exception {
        write("src/billing/Invoice.java", "class Invoice { int total; }");
        commitAll("change billing");

        assertThat(PrePushCheck.decide(repo, "docs", null).block()).isTrue();
        assertThat(PrePushCheck.decide(repo, "docs", null).block()).isFalse();
        assertThat(PrePushCheck.decide(repo, "docs", null).block()).isFalse();
    }

    @Test
    void aNewCommitWithNewStaleCodeIsBlockedAgain() throws Exception {
        write("src/billing/Invoice.java", "class Invoice { int total; }");
        commitAll("change billing");
        assertThat(PrePushCheck.decide(repo, "docs", null).block()).isTrue();

        write("src/billing/Invoice.java", "class Invoice { int total; int tax; }");
        commitAll("more billing");

        assertThat(PrePushCheck.decide(repo, "docs", null).block()).isTrue();
    }

    @Test
    void updatingTheDocsAllowsThePush() throws Exception {
        write("src/billing/Invoice.java", "class Invoice { int total; }");
        write("docs/billing.md", "---\nsources: [src/billing/**]\n---\n# Billing\nTotals.\n");
        commitAll("billing and docs");

        assertThat(PrePushCheck.decide(repo, "docs", null).block()).isFalse();
    }

    @Test
    void uncommittedDocsEditsCountAsUpdated() throws Exception {
        write("src/billing/Invoice.java", "class Invoice { int total; }");
        commitAll("change billing");
        write("docs/billing.md", "---\nsources: [src/billing/**]\n---\n# Billing\nTotals.\n");

        assertThat(PrePushCheck.decide(repo, "docs", null).block()).isFalse();
    }

    @Test
    void hookStateIsKeptInsideDotGitSoItIsNeverCommitted() throws Exception {
        write("src/billing/Invoice.java", "class Invoice { int total; }");
        commitAll("change billing");
        PrePushCheck.decide(repo, "docs", null);

        assertThat(repo.resolve(".git/jarbase-hook-state")).exists();
        assertThat(git("status", "--porcelain")).isEmpty();
    }

    @Test
    void pushingTheDefaultBranchComparesAgainstItsUpstream() throws Exception {
        // a bare "remote", with main pushed to it
        Path remote = Files.createTempDirectory(repo.getParent(), "remote");
        run(remote, "git", "init", "-q", "--bare", "-b", "main");
        git("checkout", "-q", "main");
        git("remote", "add", "origin", remote.toString());
        git("push", "-q", "-u", "origin", "main");

        // work directly on main: one unpushed commit that touches documented code
        write("src/billing/Invoice.java", "class Invoice { int total; }");
        commitAll("change billing on main");

        HookDecision decision = PrePushCheck.decide(repo, "docs", null);

        assertThat(decision.block()).isTrue();
        assertThat(decision.reason()).contains("docs/billing.md");
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

    private String git(String... args) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(List.of(args));
        return run(repo, command.toArray(String[]::new));
    }

    private static String run(Path dir, String... command) throws Exception {
        Process process = new ProcessBuilder(command).directory(dir.toFile()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes());
        if (!process.waitFor(30, TimeUnit.SECONDS) || process.exitValue() != 0) {
            throw new IllegalStateException(String.join(" ", command) + " failed: " + output);
        }
        return output.trim();
    }
}
