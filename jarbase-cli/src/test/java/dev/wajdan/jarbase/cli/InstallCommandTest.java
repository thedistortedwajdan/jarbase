package dev.wajdan.jarbase.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class InstallCommandTest {

    @TempDir
    Path repo;

    private final StringWriter out = new StringWriter();
    private final StringWriter err = new StringWriter();

    @BeforeEach
    void gitRepo() throws Exception {
        Process p = new ProcessBuilder("git", "init", "-q", "-b", "main").directory(repo.toFile()).start();
        assertThat(p.waitFor(30, TimeUnit.SECONDS)).isTrue();
    }

    private int run(String... args) {
        CommandLine cli = Main.commandLine();
        cli.setOut(new PrintWriter(out));
        cli.setErr(new PrintWriter(err));
        String[] all = new String[args.length + 3];
        all[0] = "install";
        all[1] = "--repo";
        all[2] = repo.toString();
        System.arraycopy(args, 0, all, 3, args.length);
        return cli.execute(all);
    }

    @Test
    void installsAndReportsEachFile() throws Exception {
        int exit = run();

        assertThat(exit).isZero();
        assertThat(out.toString()).contains("created   AGENTS.md").contains("created   .claude/settings.json");
        assertThat(Files.readString(repo.resolve(".jarbase/jarbase-hook.sh"))).startsWith("#!/bin/sh");
        assertThat(Files.readString(repo.resolve(".jarbase/jarbase-hook.sh"))).doesNotContain("\r");
    }

    @Test
    void runningTwiceReportsNothingNew() {
        run();
        out.getBuffer().setLength(0);

        run();

        assertThat(out.toString()).doesNotContain("created ").doesNotContain("updated ");
    }

    @Test
    void unknownAgentExitsTwoAndListsTheValidOnes() {
        int exit = run("--agent", "no-such-agent");

        assertThat(exit).isEqualTo(2);
        assertThat(err.toString()).contains("unknown agent 'no-such-agent'").contains("claude-code");
    }

    @Test
    void outsideAGitRepositoryItFailsClearly(@TempDir Path notARepo) {
        CommandLine cli = Main.commandLine();
        cli.setErr(new PrintWriter(err));

        int exit = cli.execute("install", "--repo", notARepo.toString());

        assertThat(exit).isEqualTo(2);
        assertThat(err.toString()).startsWith("jarbase: ");
    }
}
