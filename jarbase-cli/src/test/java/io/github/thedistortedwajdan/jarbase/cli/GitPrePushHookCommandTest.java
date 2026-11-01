package io.github.thedistortedwajdan.jarbase.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

/** Plays the part of git: feeds the hook the lines git sends and reads what it says and returns. */
class GitPrePushHookCommandTest {

    private static final String ZEROS = "0000000000000000000000000000000000000000";

    @TempDir
    Path repo;

    private final StringWriter err = new StringWriter();
    private final InputStream realStdin = System.in;

    @BeforeEach
    void repoWithStaleBillingDocs() throws Exception {
        git("init", "-q", "-b", "main");
        git("config", "user.name", "Test");
        git("config", "user.email", "test@example.com");
        git("config", "commit.gpgsign", "false");
        write("docs/billing.md", "---\nsources: [src/billing/**]\n---\n# Billing\n");
        write("src/billing/Invoice.java", "class Invoice {}");
        commitAll("initial");
        git("checkout", "-q", "-b", "feature");
        write("src/billing/Invoice.java", "class Invoice { int total; }");
        commitAll("change billing");
    }

    @AfterEach
    void restoreStdin() {
        System.setIn(realStdin);
    }

    private int pushAs(Map<String, String> environment, String... extraArgs) throws Exception {
        String sha = git("rev-parse", "HEAD");
        System.setIn(new ByteArrayInputStream(
                ("refs/heads/feature " + sha + " refs/heads/feature " + ZEROS + "\n").getBytes(StandardCharsets.UTF_8)));
        GitPrePushHookCommand command = new GitPrePushHookCommand();
        command.environment = environment;
        CommandLine cli = new CommandLine(command);
        cli.setErr(new PrintWriter(err));
        List<String> args = new ArrayList<>(List.of("--repo", repo.toString()));
        args.addAll(List.of(extraArgs));
        args.addAll(List.of("origin", "file:///somewhere"));
        return cli.execute(args.toArray(String[]::new));
    }

    private void settings(String text) throws IOException {
        write(".jarbase/config.properties", text);
    }

    @Test
    void staleDocsOnlyWarnWhenConfiguredToWarn() throws Exception {
        settings("onPush=warn\n");

        assertThat(pushAs(Map.of())).isZero();
        assertThat(err.toString()).contains("pushing anyway").contains("docs/billing.md");
    }

    @Test
    void blockModeExitsOneOnceAndThenZero() throws Exception {
        settings("onPush=block\n");

        assertThat(pushAs(Map.of())).isEqualTo(1);
        assertThat(err.toString()).contains("push stopped once");

        err.getBuffer().setLength(0);
        assertThat(pushAs(Map.of())).isZero();
        assertThat(err.toString()).contains("pushing anyway");
    }

    @Test
    void anAgentSessionIsNeverBlockedByTheGitHook() throws Exception {
        settings("onPush=block\n");

        assertThat(pushAs(Map.of("CLAUDECODE", "1"))).isZero();
        assertThat(err.toString()).contains("pushing anyway");
    }

    @Test
    void theOffSwitchSilencesEverything() throws Exception {
        settings("onPush=block\n");

        assertThat(pushAs(Map.of("JARBASE_HOOKS", "off"))).isZero();
        assertThat(err.toString()).isEmpty();
    }

    @Test
    void anEnvironmentVariableOverridesTheProjectSetting() throws Exception {
        settings("onPush=block\n");

        assertThat(pushAs(Map.of("JARBASE_ON_PUSH", "warn"))).isZero();
    }

    @Test
    void aBrokenSettingsFileNeverStopsAPush() throws Exception {
        settings("onPush=sometimes\n");

        assertThat(pushAs(Map.of())).isZero();
        assertThat(err.toString()).startsWith("jarbase hook: skipped").contains("onPush");
    }

    @Test
    void anUpToDatePushIsSilent() throws Exception {
        write("docs/billing.md", "---\nsources: [src/billing/**]\n---\n# Billing\nTotals.\n");
        commitAll("docs");
        settings("onPush=block\n");

        assertThat(pushAs(Map.of())).isZero();
        assertThat(err.toString()).isEmpty();
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
