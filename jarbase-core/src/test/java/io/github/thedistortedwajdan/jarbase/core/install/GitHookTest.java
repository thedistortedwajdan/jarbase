package io.github.thedistortedwajdan.jarbase.core.install;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GitHookTest {

    @TempDir
    Path repo;

    @BeforeEach
    void gitRepo() throws Exception {
        git("init", "-q", "-b", "main");
    }

    @Test
    void installsAnExecutableHookThatCallsJarbase() throws Exception {
        Change change = GitHook.install(repo);

        Path hook = repo.resolve(".git/hooks/pre-push");
        assertThat(change.kind()).isEqualTo(Change.Kind.CREATED);
        assertThat(change.path()).isEqualTo(".git/hooks/pre-push");
        assertThat(Files.readString(hook))
                .startsWith("#!/bin/sh")
                .contains("# jarbase:git-hook")
                .contains("hook git-pre-push")
                .doesNotContain("\r");
        assertThat(Files.isExecutable(hook)).isTrue();
    }

    @Test
    void installingTwiceChangesNothing() {
        GitHook.install(repo);

        assertThat(GitHook.install(repo).kind()).isEqualTo(Change.Kind.UNCHANGED);
    }

    @Test
    void anOutdatedJarbaseHookIsReplaced() throws Exception {
        Path hook = repo.resolve(".git/hooks/pre-push");
        Files.createDirectories(hook.getParent());
        Files.writeString(hook, "#!/bin/sh\n# jarbase:git-hook\necho old version\n");

        Change change = GitHook.install(repo);

        assertThat(change.kind()).isEqualTo(Change.Kind.UPDATED);
        assertThat(Files.readString(hook)).doesNotContain("old version");
    }

    @Test
    void aHookSomeoneElseWroteIsNeverOverwritten() throws Exception {
        Path hook = repo.resolve(".git/hooks/pre-push");
        Files.createDirectories(hook.getParent());
        Files.writeString(hook, "#!/bin/sh\necho my own hook\n");

        Change change = GitHook.install(repo);

        assertThat(change.kind()).isEqualTo(Change.Kind.SKIPPED);
        assertThat(change.note()).contains("sh .jarbase/jarbase hook git-pre-push");
        assertThat(Files.readString(hook)).isEqualTo("#!/bin/sh\necho my own hook\n");
    }

    @Test
    void aConfiguredHooksPathIsLeftAlone() throws Exception {
        git("config", "core.hooksPath", ".husky");

        Change change = GitHook.install(repo);

        assertThat(change.kind()).isEqualTo(Change.Kind.SKIPPED);
        assertThat(change.note()).contains("core.hooksPath");
        assertThat(repo.resolve(".git/hooks/pre-push")).doesNotExist();
    }

    @Test
    void outsideAGitRepositoryItIsSkipped(@TempDir Path notARepo) {
        Change change = GitHook.install(notARepo);

        assertThat(change.kind()).isEqualTo(Change.Kind.SKIPPED);
        assertThat(change.note()).contains("not a git repository");
    }

    private void git(String... args) throws Exception {
        String[] command = new String[args.length + 1];
        command[0] = "git";
        System.arraycopy(args, 0, command, 1, args.length);
        Process process = new ProcessBuilder(command).directory(repo.toFile()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes());
        if (!process.waitFor(30, TimeUnit.SECONDS) || process.exitValue() != 0) {
            throw new IllegalStateException("git " + String.join(" ", args) + " failed: " + output);
        }
    }
}
