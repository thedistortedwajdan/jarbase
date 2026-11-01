package io.github.thedistortedwajdan.jarbase.core.hook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.github.thedistortedwajdan.jarbase.core.agent.AgentAdapter;
import io.github.thedistortedwajdan.jarbase.core.config.JarbaseConfig;
import io.github.thedistortedwajdan.jarbase.core.config.JarbaseConfig.OnPush;
import io.github.thedistortedwajdan.jarbase.core.config.JarbaseConfig.Review;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The auto-fix: git push finds stale docs, a (pretend) coding agent updates them, and jarbase commits and pushes.
 * Uses a real repository, a real bare "remote", and shell scripts standing in for the agent.
 */
class GitPrePushFixTest {

    private static final String ZEROS = "0000000000000000000000000000000000000000";

    @TempDir
    Path repo;

    @TempDir
    Path remoteDir;

    private final List<String> progress = new ArrayList<>();

    @BeforeEach
    void repoWithAStaleBillingChange() throws Exception {
        assumeTrue(shellAvailable(), "no sh on this machine");
        Path remote = remoteDir.resolve("remote.git");
        run(remoteDir, "git", "init", "-q", "--bare", "-b", "main", remote.toString());

        git("init", "-q", "-b", "main");
        git("config", "user.name", "Dev Person");
        git("config", "user.email", "dev@example.com");
        git("config", "commit.gpgsign", "false");
        write("docs/billing.md", "---\nsources: [src/billing/**]\n---\n# Billing\nInvoices.\n");
        write("docs/other.md", "---\nsources: [src/other/**]\n---\n# Other\n");
        write("src/billing/Invoice.java", "class Invoice {}");
        commitAll("initial");
        git("remote", "add", "origin", remote.toString());
        git("push", "-q", "-u", "origin", "main");

        git("checkout", "-q", "-b", "feature");
        write("src/billing/Invoice.java", "class Invoice { int total; }");
        commitAll("change billing");
    }

    // ---- the happy paths ----

    @Test
    void autoModeUpdatesTheDocsCommitsAndPushes() throws Exception {
        GitPrePush.Result result = push(config(Review.AUTO), agent("echo 'Totals are documented.' >> docs/billing.md"));

        assertThat(result.block()).isTrue(); // the original push is stopped: this hook already did it
        assertThat(result.message()).contains("fake-agent updated docs/billing.md").contains("feature was pushed")
                .contains("expected");
        assertThat(progress).anyMatch(m -> m.contains("Asking fake-agent"));

        assertThat(remoteLog("feature")).startsWith("docs: update for code changes on feature (jarbase)");
        assertThat(remoteFile("feature", "docs/billing.md")).contains("Totals are documented.");
        assertThat(git("status", "--porcelain")).isEmpty();
    }

    @Test
    void theInstructionTheAgentReceivesNamesTheStalePageAndTheChange() throws Exception {
        // the pretend agent reads its standard input and only edits the docs if it saw the right instruction
        GitPrePush.Result result = push(config(Review.AUTO), agent(
                "cat > /tmp/jarbase-instruction.$$ ; "
                        + "grep -q 'docs/billing.md' /tmp/jarbase-instruction.$$ && grep -q 'src/billing/Invoice.java' /tmp/jarbase-instruction.$$ "
                        + "&& grep -q 'git diff main...HEAD' /tmp/jarbase-instruction.$$ && echo 'Saw the instruction.' >> docs/billing.md; "
                        + "rm -f /tmp/jarbase-instruction.$$"));

        assertThat(result.message()).contains("fake-agent updated docs/billing.md");
        assertThat(remoteFile("feature", "docs/billing.md")).contains("Saw the instruction.");
    }

    @Test
    void theDocsCommitBelongsToTheDeveloperAndHasNoCoAuthor() throws Exception {
        push(config(Review.AUTO), agent("echo 'More.' >> docs/billing.md"));

        String commit = git("log", "-1", "--format=%an <%ae>%n%B");
        assertThat(commit).contains("Dev Person <dev@example.com>").doesNotContainIgnoringCase("co-authored-by");
    }

    @Test
    void requiredModeLeavesTheEditsForReviewAndPushesNothing() throws Exception {
        GitPrePush.Result result = push(config(Review.REQUIRED), agent("echo 'Totals.' >> docs/billing.md"));

        assertThat(result.block()).isTrue();
        assertThat(result.message()).contains("Nothing was committed or pushed").contains("git diff -- docs");
        assertThat(git("status", "--porcelain")).contains("docs/billing.md");
        assertThat(git("log", "-1", "--format=%s")).isEqualTo("change billing");
        assertThat(remoteBranches()).doesNotContain("feature");
    }

    @Test
    void anAgentThatCreatesANewDocsFileIsAccepted() throws Exception {
        GitPrePush.Result result = push(config(Review.AUTO),
                agent("echo 'More.' >> docs/billing.md; printf -- '---\\ntitle: New\\n---\\n# New\\nHi.\\n' > docs/new.md"));

        assertThat(result.block()).isTrue();
        assertThat(remoteFile("feature", "docs/new.md")).contains("# New");
    }

    // ---- when it cannot fix: warn and let the push through, leaving nothing behind ----

    @Test
    void anAgentThatFailsLeavesNothingAndThePushGoesThroughWithAWarning() throws Exception {
        GitPrePush.Result result = push(config(Review.AUTO), agent("echo 'half done' >> docs/billing.md; exit 3"));

        assertThat(result.block()).isFalse();
        assertThat(result.message()).contains("could not update the docs automatically").contains("stopped with an error")
                .contains("pushing anyway").contains("docs/billing.md");
        assertThat(git("status", "--porcelain")).isEmpty();
    }

    @Test
    void anAgentThatTakesTooLongIsStoppedAndUndone() throws Exception {
        JarbaseConfig config = new JarbaseConfig(Review.AUTO, OnPush.FIX, null, 1);

        GitPrePush.Result result = push(config, agent("echo 'slow' >> docs/billing.md; sleep 20"));

        assertThat(result.block()).isFalse();
        assertThat(result.message()).contains("took longer than 1s").contains("pushing anyway");
        assertThat(git("status", "--porcelain")).isEmpty();
    }

    @Test
    void editsOutsideTheDocsFolderAreUndoneEvenIfTheDocsWereFine() throws Exception {
        GitPrePush.Result result = push(config(Review.AUTO),
                agent("echo 'Totals.' >> docs/billing.md; echo '// sneaky' >> src/billing/Invoice.java; echo x > stray.txt"));

        assertThat(result.block()).isFalse();
        assertThat(result.message()).contains("outside docs/").contains("all of its changes were undone");
        assertThat(git("status", "--porcelain")).isEmpty();
        assertThat(repo.resolve("stray.txt")).doesNotExist();
    }

    @Test
    void anAgentThatChangesNothingIsReportedAsSuch() throws Exception {
        GitPrePush.Result result = push(config(Review.AUTO), agent("true"));

        assertThat(result.block()).isFalse();
        assertThat(result.message()).contains("made no changes").contains("pushing anyway");
    }

    @Test
    void editingOnlyUnrelatedPagesDoesNotCount() throws Exception {
        GitPrePush.Result result = push(config(Review.AUTO), agent("echo 'Unrelated.' >> docs/other.md"));

        assertThat(result.block()).isFalse();
        assertThat(result.message()).contains("did not update the pages that describe the pushed code");
        assertThat(git("status", "--porcelain")).isEmpty();
    }

    @Test
    void editsThatBreakTheDocsAreUndone() throws Exception {
        GitPrePush.Result result = push(config(Review.AUTO),
                agent("echo 'See [gone](missing.md).' >> docs/billing.md"));

        assertThat(result.block()).isFalse();
        assertThat(result.message()).contains("introduced docs problems").contains("missing.md");
        assertThat(git("status", "--porcelain")).isEmpty();
    }

    @Test
    void noAgentOnThisMachineJustWarns() throws Exception {
        AgentAdapter missing = new FakeAgent("ghost", "definitely-not-installed-anywhere", "true");

        GitPrePush.Result result = push(config(Review.AUTO), missing);

        assertThat(result.block()).isFalse();
        assertThat(result.message()).contains("no coding agent found").contains("definitely-not-installed-anywhere")
                .contains("pushing anyway");
        assertThat(progress).isEmpty();
    }

    @Test
    void aConfiguredAgentThatDoesNotExistIsNamedInTheWarning() throws Exception {
        JarbaseConfig config = new JarbaseConfig(Review.AUTO, OnPush.FIX, "codex", 60);

        GitPrePush.Result result = push(config, agent("true"));

        assertThat(result.block()).isFalse();
        assertThat(result.message()).contains("unknown agent 'codex'").contains("fake-agent");
    }

    @Test
    void theConfiguredAgentIsTheOneThatRuns() throws Exception {
        JarbaseConfig config = new JarbaseConfig(Review.AUTO, OnPush.FIX, "second", 60);
        AgentAdapter first = new FakeAgent("first", "sh", "echo 'from first' >> docs/billing.md");
        AgentAdapter second = new FakeAgent("second", "sh", "echo 'from second' >> docs/billing.md");

        GitPrePush.Result result = push(config, first, second);

        assertThat(result.message()).contains("second updated");
        assertThat(remoteFile("feature", "docs/billing.md")).contains("from second").doesNotContain("from first");
    }

    @Test
    void uncommittedDocsChangesStopAnAutomaticFix() throws Exception {
        write("docs/other.md", "---\nsources: [src/other/**]\n---\n# Other\nMy unfinished edit.\n");

        GitPrePush.Result result = push(config(Review.AUTO), agent("echo 'Totals.' >> docs/billing.md"));

        assertThat(result.block()).isFalse();
        assertThat(result.message()).contains("uncommitted changes under docs/");
        assertThat(read("docs/other.md")).contains("My unfinished edit.");
        assertThat(read("docs/billing.md")).doesNotContain("Totals.");
    }

    @Test
    void aFailedFixIsOnlyTriedOncePerSituation() throws Exception {
        push(config(Review.AUTO), agent("exit 1"));
        progress.clear();

        GitPrePush.Result second = push(config(Review.AUTO), agent("exit 1"));

        assertThat(progress).isEmpty();
        assertThat(second.block()).isFalse();
        assertThat(second.message()).startsWith("jarbase: pushing anyway");
    }

    @Test
    void insideAnAgentSessionNoSecondAgentIsStarted() throws Exception {
        GitPrePush.Context context = context(config(Review.AUTO), true, "origin", agent("echo x >> docs/billing.md"));

        GitPrePush.Result result = GitPrePush.run(context, List.of(pushOfFeature()));

        assertThat(result.block()).isFalse();
        assertThat(progress).isEmpty();
        assertThat(git("status", "--porcelain")).isEmpty();
    }

    @Test
    void anotherBranchThanTheCheckedOutOneIsNotFixedAutomatically() throws Exception {
        String featureSha = git("rev-parse", "HEAD");
        git("checkout", "-q", "main");

        GitPrePush.RefUpdate other = new GitPrePush.RefUpdate("refs/heads/feature", featureSha, "refs/heads/feature", ZEROS);
        GitPrePush.Result result = GitPrePush.run(
                context(config(Review.AUTO), false, "origin", agent("echo x >> docs/billing.md")), List.of(other));

        assertThat(result.block()).isFalse();
        assertThat(result.message()).contains("only the checked-out branch");
    }

    @Test
    void ifPushingTheDocsFailsTheyStayCommittedLocallyAndYouAreTold() throws Exception {
        GitPrePush.Context context = context(config(Review.AUTO), false, "no-such-remote", agent("echo 'Totals.' >> docs/billing.md"));

        GitPrePush.Result result = GitPrePush.run(context, List.of(pushOfFeature()));

        assertThat(result.block()).isFalse();
        assertThat(result.message()).contains("pushing them failed").contains("committed");
        assertThat(git("log", "-1", "--format=%s")).startsWith("docs: update for code changes on feature");
        assertThat(git("status", "--porcelain")).isEmpty();
    }

    // ---- helpers ----

    private JarbaseConfig config(Review review) {
        return new JarbaseConfig(review, OnPush.FIX, null, 60);
    }

    private AgentAdapter agent(String script) {
        return new FakeAgent(script);
    }

    private GitPrePush.RefUpdate pushOfFeature() throws Exception {
        return new GitPrePush.RefUpdate("refs/heads/feature", git("rev-parse", "HEAD"), "refs/heads/feature", ZEROS);
    }

    private GitPrePush.Context context(JarbaseConfig config, boolean insideAgent, String remote, AgentAdapter... agents) {
        return new GitPrePush.Context(repo, "docs", config, insideAgent, remote, System.getenv(), List.of(agents), progress::add);
    }

    private GitPrePush.Result push(JarbaseConfig config, AgentAdapter... agents) throws Exception {
        return GitPrePush.run(context(config, false, "origin", agents), List.of(pushOfFeature()));
    }

    private String remoteLog(String branch) throws Exception {
        return run(remoteDir.resolve("remote.git"), "git", "log", "-1", "--format=%s", branch);
    }

    private String remoteFile(String branch, String path) throws Exception {
        return run(remoteDir.resolve("remote.git"), "git", "show", branch + ":" + path);
    }

    private String remoteBranches() throws Exception {
        return run(remoteDir.resolve("remote.git"), "git", "branch", "--list");
    }

    private String read(String path) throws IOException {
        return Files.readString(repo.resolve(path));
    }

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

    private static boolean shellAvailable() {
        try {
            Process process = new ProcessBuilder("sh", "-c", "exit 0").start();
            return process.waitFor(10, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }
}
