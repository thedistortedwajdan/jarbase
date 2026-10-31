package dev.wajdan.jarbase.cli;

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

class ValidateCommandTest {

    @TempDir
    Path repo;

    private final StringWriter out = new StringWriter();
    private final StringWriter err = new StringWriter();

    @BeforeEach
    void gitRepoWithOneCodeFile() throws Exception {
        git("init", "-q", "-b", "main");
        write("src/billing/Invoice.java", "class Invoice {}");
    }

    private int run(String... args) {
        CommandLine cli = Main.commandLine();
        cli.setOut(new PrintWriter(out));
        cli.setErr(new PrintWriter(err));
        List<String> all = new ArrayList<>(List.of("validate", "--repo", repo.toString()));
        all.addAll(List.of(args));
        return cli.execute(all.toArray(String[]::new));
    }

    @Test
    void healthyDocsExitZero() throws Exception {
        write("docs/billing.md", "---\nsources: [\"src/billing/**\"]\n---\n# Billing\nText.\n");

        assertThat(run()).isZero();
        assertThat(out.toString()).contains("no problems");
    }

    @Test
    void untrackedNewFilesCountAsExisting() throws Exception {
        // nothing is committed or even added: validate must still see the code and the docs
        write("docs/a.md", "# A\n[b](b.md)\n");
        write("docs/b.md", "# B\n");

        assertThat(run()).isZero();
    }

    @Test
    void errorsExitOne() throws Exception {
        write("docs/a.md", "# A\n[x](missing.md)\n");

        assertThat(run()).isEqualTo(1);
        assertThat(out.toString()).contains("broken-link").contains("(line 2)");
    }

    @Test
    void warningsAloneExitZeroUnlessStrict() throws Exception {
        write("docs/a.md", "---\ntitle: A\nsources: [\"src/gone/**\"]\n---\nText\n");

        assertThat(run()).isZero();
        assertThat(run("--strict")).isEqualTo(1);
    }

    @Test
    void jsonFormat() throws Exception {
        write("docs/a.md", "# A\n[x](missing.md)\n");

        run("--format", "json");

        assertThat(out.toString()).startsWith("{\"pages\":1,\"errors\":1,\"warnings\":0,\"issues\":[");
    }

    @Test
    void notAGitRepositoryExitsTwo(@TempDir Path notARepo) {
        CommandLine cli = Main.commandLine();
        cli.setErr(new PrintWriter(err));

        assertThat(cli.execute("validate", "--repo", notARepo.toString())).isEqualTo(2);
        assertThat(err.toString()).startsWith("jarbase: ");
    }

    private void write(String relativePath, String content) throws IOException {
        Path file = repo.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
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
