package io.github.thedistortedwajdan.jarbase.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

/** Runs the real command against a throwaway git repository and checks what it prints and returns. */
class AffectedCommandTest {

    @TempDir
    Path repo;

    private final StringWriter out = new StringWriter();
    private final StringWriter err = new StringWriter();

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

    private int run(String... args) {
        CommandLine cli = Main.commandLine();
        cli.setOut(new PrintWriter(out));
        cli.setErr(new PrintWriter(err));
        List<String> all = new ArrayList<>(List.of("affected", "--repo", repo.toString()));
        all.addAll(List.of(args));
        return cli.execute(all.toArray(String[]::new));
    }

    @Test
    void printsAffectedPagesAndExitsZeroByDefault() {
        int exit = run("--base", "main");

        assertThat(exit).isZero();
        assertThat(out.toString()).contains("docs/billing.md  [NOT UPDATED]");
        assertThat(out.toString()).contains("changed: src/billing/Invoice.java");
    }

    @Test
    void failOnStaleExitsOneWhenDocsWereNotUpdated() {
        assertThat(run("--base", "main", "--fail-on-stale")).isEqualTo(1);
    }

    @Test
    void failOnStaleExitsZeroOnceDocsAreUpdated() throws Exception {
        write("docs/billing.md", "---\nsources: [src/billing/**]\n---\n# Billing\nTotals added.\n");
        commitAll("update docs");

        assertThat(run("--base", "main", "--fail-on-stale")).isZero();
        assertThat(out.toString()).contains("[updated]");
    }

    @Test
    void jsonFormatIsMachineReadable() {
        int exit = run("--base", "main", "--format", "json");

        assertThat(exit).isZero();
        assertThat(out.toString()).startsWith("{\"base\":\"main\",\"stale\":1,\"affected\":[{\"page\":\"docs/billing.md\"");
    }

    @Test
    void uncommittedEditsAreOnlyCountedWhenAsked() throws Exception {
        write("docs/other.md", "---\nsources: [src/other/**]\n---\n# Other\n");
        commitAll("add other page");
        write("src/other/Thing.java", "class Thing {}");

        run("--base", "main");
        assertThat(out.toString()).doesNotContain("docs/other.md");

        out.getBuffer().setLength(0);
        run("--base", "main", "--uncommitted");
        assertThat(out.toString()).contains("docs/other.md");
    }

    @Test
    void unknownBaseExitsTwoWithAReadableMessage() {
        int exit = run("--base", "no-such-branch");

        assertThat(exit).isEqualTo(2);
        assertThat(err.toString()).startsWith("jarbase: ");
    }

    @Test
    void jarbaseAloneShowsHelp() {
        CommandLine cli = Main.commandLine();
        cli.setOut(new PrintWriter(out));

        assertThat(cli.execute()).isZero();
        assertThat(out.toString()).contains("affected");
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
