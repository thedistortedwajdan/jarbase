package io.github.thedistortedwajdan.jarbase.maven;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.maven.plugin.MojoFailureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The goals, run directly against a throwaway git repository. */
class MojosTest {

    @TempDir
    Path repo;

    @BeforeEach
    void gitRepo() throws Exception {
        git("init", "-q", "-b", "main");
        git("config", "user.name", "Test");
        git("config", "user.email", "test@example.com");
        git("config", "commit.gpgsign", "false");
        write("src/billing/Invoice.java", "class Invoice {}");
        write("docs/billing.md", "---\nsources: [\"src/billing/**\"]\n---\n# Billing\nText.\n");
        git("add", "-A");
        git("commit", "-q", "-m", "initial");
    }

    private InstallMojo installMojo() {
        InstallMojo mojo = new InstallMojo();
        mojo.basedir = repo.toFile();
        mojo.executionRoot = repo.toString();
        mojo.docs = "docs";
        mojo.agents = List.of("claude-code");
        mojo.pluginVersion = "0.1.0";
        return mojo;
    }

    @Test
    void installWritesTheVersionAndTheHookFilesButNoJar() throws Exception {
        installMojo().execute();

        assertThat(Files.readString(repo.resolve(".jarbase/version"))).isEqualTo("0.1.0\n");
        assertThat(repo.resolve(".jarbase/jarbase-hook.sh")).exists();
        assertThat(repo.resolve(".jarbase/jarbase")).exists();
        assertThat(repo.resolve(".claude/settings.json")).exists();
        assertThat(repo.resolve("AGENTS.md")).exists();
        assertThat(repo.resolve(".jarbase/jarbase.jar")).doesNotExist();
    }

    @Test
    void installTwiceChangesNothingTheSecondTime() throws Exception {
        installMojo().execute();
        String settings = Files.readString(repo.resolve(".claude/settings.json"));
        String agents = Files.readString(repo.resolve("AGENTS.md"));

        installMojo().execute();

        assertThat(Files.readString(repo.resolve(".claude/settings.json"))).isEqualTo(settings);
        assertThat(Files.readString(repo.resolve("AGENTS.md"))).isEqualTo(agents);
    }

    @Test
    void installAfterAPluginUpgradeMovesTheVersion() throws Exception {
        installMojo().execute();
        InstallMojo upgraded = installMojo();
        upgraded.pluginVersion = "0.2.0";

        upgraded.execute();

        assertThat(Files.readString(repo.resolve(".jarbase/version"))).isEqualTo("0.2.0\n");
    }

    @Test
    void installDoesNothingInAModuleThatIsNotWhereMavenStarted() throws Exception {
        InstallMojo mojo = installMojo();
        mojo.executionRoot = repo.getParent().toString();

        mojo.execute();

        assertThat(repo.resolve(".jarbase")).doesNotExist();
    }

    @Test
    void installCanBeSkipped() throws Exception {
        InstallMojo mojo = installMojo();
        mojo.skip = true;

        mojo.execute();

        assertThat(repo.resolve(".jarbase")).doesNotExist();
    }

    @Test
    void installOutsideAGitRepositoryWarnsAndMovesOn(@TempDir Path notARepo) throws Exception {
        InstallMojo mojo = installMojo();
        mojo.basedir = notARepo.toFile();
        mojo.executionRoot = notARepo.toString();

        mojo.execute();

        assertThat(notARepo.resolve(".jarbase")).doesNotExist();
    }

    @Test
    void installRejectsAnUnknownAgent() {
        InstallMojo mojo = installMojo();
        mojo.agents = List.of("no-such-agent");

        assertThatThrownBy(mojo::execute)
                .isInstanceOf(MojoFailureException.class)
                .hasMessageContaining("no-such-agent")
                .hasMessageContaining("claude-code");
    }

    private ValidateMojo validateMojo() {
        ValidateMojo mojo = new ValidateMojo();
        mojo.basedir = repo.toFile();
        mojo.docs = "docs";
        return mojo;
    }

    @Test
    void validatePassesOnHealthyDocs() throws Exception {
        validateMojo().execute();
    }

    @Test
    void validateFailsTheBuildOnABrokenLink() throws Exception {
        write("docs/bad.md", "# Bad\n[x](missing.md)\n");

        assertThatThrownBy(() -> validateMojo().execute()).isInstanceOf(MojoFailureException.class);
    }

    @Test
    void validateTreatsWarningsAsErrorsOnlyWhenStrict() throws Exception {
        write("docs/warn.md", "---\ntitle: W\nsources: [\"src/gone/**\"]\n---\nText\n");

        validateMojo().execute();

        ValidateMojo strict = validateMojo();
        strict.strict = true;
        assertThatThrownBy(strict::execute).isInstanceOf(MojoFailureException.class);
    }

    private AffectedMojo affectedMojo() {
        AffectedMojo mojo = new AffectedMojo();
        mojo.basedir = repo.toFile();
        mojo.docs = "docs";
        mojo.base = "main";
        return mojo;
    }

    @Test
    void affectedFailsOnlyWhenAskedAndOnlyWhenStale() throws Exception {
        git("checkout", "-q", "-b", "feature");
        write("src/billing/Invoice.java", "class Invoice { int total; }");
        git("commit", "-qam", "change billing");

        affectedMojo().execute(); // informational by default

        AffectedMojo failing = affectedMojo();
        failing.failOnStale = true;
        assertThatThrownBy(failing::execute).isInstanceOf(MojoFailureException.class);

        write("docs/billing.md", "---\nsources: [\"src/billing/**\"]\n---\n# Billing\nTotals.\n");
        git("commit", "-qam", "update docs");
        failing.execute(); // docs were updated too, so it passes
    }

    // ---- helpers ----

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
