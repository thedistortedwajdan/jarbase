package io.github.thedistortedwajdan.jarbase.core.hook;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.thedistortedwajdan.jarbase.core.config.JarbaseConfig;
import io.github.thedistortedwajdan.jarbase.core.config.JarbaseConfig.OnPush;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The git pre-push rule, against a real throwaway repository. */
class GitPrePushTest {

    private static final String ZEROS = "0000000000000000000000000000000000000000";

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

    private JarbaseConfig config(OnPush onPush) {
        return new JarbaseConfig(JarbaseConfig.Review.AUTO, onPush, null, 60);
    }

    private GitPrePush.RefUpdate pushOfCurrentBranch() throws Exception {
        String sha = git("rev-parse", "HEAD");
        return new GitPrePush.RefUpdate("refs/heads/feature", sha, "refs/heads/feature", ZEROS);
    }

    private void staleBillingChange() throws Exception {
        write("src/billing/Invoice.java", "class Invoice { int total; }");
        commitAll("change billing");
    }

    @Test
    void parsesWhatGitSendsOnStandardInput() {
        List<GitPrePush.RefUpdate> updates = GitPrePush.parse(
                "refs/heads/feature abc123 refs/heads/feature " + ZEROS + "\n(delete) " + ZEROS + " refs/heads/old def456\n");

        assertThat(updates).hasSize(2);
        assertThat(updates.get(0).isBranchPush()).isTrue();
        assertThat(updates.get(0).branch()).isEqualTo("feature");
        assertThat(updates.get(1).isBranchPush()).isFalse();
    }

    @Test
    void nothingStaleMeansNoMessageAndNoBlock() throws Exception {
        write("pom.xml", "<project/>");
        commitAll("build file");

        GitPrePush.Result result = GitPrePush.run(repo, "docs", List.of(pushOfCurrentBranch()), config(OnPush.BLOCK), false);

        assertThat(result.block()).isFalse();
        assertThat(result.message()).isEmpty();
    }

    @Test
    void warnModePushesAnywayAndNamesThePages() throws Exception {
        staleBillingChange();

        GitPrePush.Result result = GitPrePush.run(repo, "docs", List.of(pushOfCurrentBranch()), config(OnPush.WARN), false);

        assertThat(result.block()).isFalse();
        assertThat(result.message()).contains("pushing anyway").contains("docs/billing.md").contains("src/billing/**");
    }

    @Test
    void blockModeStopsThePushOnceAndThenLetsItThrough() throws Exception {
        staleBillingChange();
        List<GitPrePush.RefUpdate> push = List.of(pushOfCurrentBranch());

        GitPrePush.Result first = GitPrePush.run(repo, "docs", push, config(OnPush.BLOCK), false);
        GitPrePush.Result second = GitPrePush.run(repo, "docs", push, config(OnPush.BLOCK), false);

        assertThat(first.block()).isTrue();
        assertThat(first.message()).contains("push stopped once").contains("docs/billing.md").contains("--no-verify");
        assertThat(second.block()).isFalse();
        assertThat(second.message()).contains("pushing anyway");
    }

    @Test
    void insideAnAgentSessionItOnlyWarns() throws Exception {
        staleBillingChange();

        GitPrePush.Result result = GitPrePush.run(repo, "docs", List.of(pushOfCurrentBranch()), config(OnPush.BLOCK), true);

        assertThat(result.block()).isFalse();
        assertThat(result.message()).contains("pushing anyway");
    }

    @Test
    void updatedDocsAreNotStale() throws Exception {
        write("src/billing/Invoice.java", "class Invoice { int total; }");
        write("docs/billing.md", "---\nsources: [src/billing/**]\n---\n# Billing\nTotals.\n");
        commitAll("billing with docs");

        GitPrePush.Result result = GitPrePush.run(repo, "docs", List.of(pushOfCurrentBranch()), config(OnPush.BLOCK), false);

        assertThat(result.block()).isFalse();
        assertThat(result.message()).isEmpty();
    }

    @Test
    void uncommittedDocsEditsDoNotCountBecauseTheyAreNotPushed() throws Exception {
        staleBillingChange();
        write("docs/billing.md", "---\nsources: [src/billing/**]\n---\n# Billing\nNot committed.\n");

        GitPrePush.Result result = GitPrePush.run(repo, "docs", List.of(pushOfCurrentBranch()), config(OnPush.WARN), false);

        assertThat(result.message()).contains("docs/billing.md");
    }

    @Test
    void deletionsAndTagsAreIgnored() throws Exception {
        staleBillingChange();
        String sha = git("rev-parse", "HEAD");
        List<GitPrePush.RefUpdate> updates = List.of(
                new GitPrePush.RefUpdate("(delete)", ZEROS, "refs/heads/old", sha),
                new GitPrePush.RefUpdate("refs/tags/v1", sha, "refs/tags/v1", ZEROS));

        GitPrePush.Result result = GitPrePush.run(repo, "docs", updates, config(OnPush.BLOCK), false);

        assertThat(result.block()).isFalse();
        assertThat(result.message()).isEmpty();
    }

    @Test
    void pushingADifferentBranchChecksThatBranchNotTheCheckedOutOne() throws Exception {
        staleBillingChange();
        String stale = git("rev-parse", "HEAD");
        git("checkout", "-q", "main");
        git("checkout", "-q", "-b", "clean-branch");
        write("pom.xml", "<project/>");
        commitAll("unrelated");

        GitPrePush.RefUpdate pushStale = new GitPrePush.RefUpdate("refs/heads/feature", stale, "refs/heads/feature", ZEROS);
        GitPrePush.Result result = GitPrePush.run(repo, "docs", List.of(pushStale), config(OnPush.WARN), false);

        assertThat(result.message()).contains("docs/billing.md");
    }

    // ---- helpers ----

    private void write(String relativePath, String content) throws IOException {
        Path file = repo.resolve(relativePath);
        Files.createDirectories(file.getParent());
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
        Process process = new ProcessBuilder(command).directory(repo.toFile()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes());
        if (!process.waitFor(30, TimeUnit.SECONDS) || process.exitValue() != 0) {
            throw new IllegalStateException("git " + String.join(" ", args) + " failed: " + output);
        }
        return output.trim();
    }
}
