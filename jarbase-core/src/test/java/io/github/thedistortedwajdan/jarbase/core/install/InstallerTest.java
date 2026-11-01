package io.github.thedistortedwajdan.jarbase.core.install;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.thedistortedwajdan.jarbase.core.agent.AgentAdapter;
import io.github.thedistortedwajdan.jarbase.core.agent.claude.ClaudeCodeAdapter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InstallerTest {

    @TempDir
    Path project;

    private final List<AgentAdapter> claude = List.of(new ClaudeCodeAdapter());
    private final InstallAssets assets =
            new InstallAssets("#!/bin/sh\necho hook\n", "#!/bin/sh\necho launcher\n", null, null);

    private String read(String path) throws IOException {
        return Files.readString(project.resolve(path));
    }

    private JsonObject settings() throws IOException {
        return JsonParser.parseString(read(".claude/settings.json")).getAsJsonObject();
    }

    private List<Change> install() {
        return Installer.install(project, "docs", claude, assets);
    }

    @Test
    void freshProjectGetsEverything() throws IOException {
        List<Change> changes = install();

        assertThat(read(".jarbase/jarbase-hook.sh")).isEqualTo("#!/bin/sh\necho hook\n");
        assertThat(read(".jarbase/.gitignore")).isEqualTo("jarbase.jar\n");
        assertThat(read("AGENTS.md")).contains("<!-- jarbase:start -->", "docs/", "sh .jarbase/jarbase affected");
        assertThat(read("CLAUDE.md")).isEqualTo("@AGENTS.md\n");
        assertThat(read("docs/index.md")).contains("title: Introduction");
        assertThat(changes).extracting(Change::path).contains(
                ".jarbase/jarbase-hook.sh", "AGENTS.md", "CLAUDE.md", ".claude/settings.json", "docs/index.md");
    }

    @Test
    void settingsFileIsCreatedOnceAndNeverOverwritten() throws IOException {
        install();
        assertThat(read(".jarbase/config.properties")).contains("review=auto").contains("onPush=fix");

        Files.writeString(project.resolve(".jarbase/config.properties"), "review=required\nonPush=block\n");
        List<Change> second = install();

        assertThat(read(".jarbase/config.properties")).isEqualTo("review=required\nonPush=block\n");
        assertThat(second).anyMatch(c -> c.path().equals(".jarbase/config.properties") && c.kind() == Change.Kind.UNCHANGED);
    }

    @Test
    void defaultReviewModeTellsTheAgentNotToWaitForApproval() throws IOException {
        install();

        assertThat(read("AGENTS.md"))
                .contains("do not need to ask the user to approve the docs change first")
                .doesNotContain("wait for approval");
    }

    @Test
    void requiredReviewModeTellsTheAgentToShowTheDiffFirst() throws IOException {
        Files.createDirectories(project.resolve(".jarbase"));
        Files.writeString(project.resolve(".jarbase/config.properties"), "review=required\n");

        install();

        assertThat(read("AGENTS.md"))
                .contains("Show the user the docs diff and wait for approval")
                .doesNotContain("do not need to ask the user");
    }

    @Test
    void changingTheReviewModeAndReinstallingRewritesTheInstructions() throws IOException {
        install();
        Files.writeString(project.resolve(".jarbase/config.properties"), "review=required\n");

        install();

        assertThat(read("AGENTS.md")).contains("wait for approval");
        assertThat(read("AGENTS.md").split("<!-- jarbase:start -->", -1)).hasSize(2); // still one block
    }

    @Test
    void theGitHookIsInstalledInAGitRepository() throws Exception {
        Process init = new ProcessBuilder("git", "init", "-q").directory(project.toFile()).start();
        assertThat(init.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)).isTrue();

        List<Change> changes = install();

        assertThat(changes).anyMatch(c -> c.path().equals(".git/hooks/pre-push") && c.kind() == Change.Kind.CREATED);
        assertThat(read(".git/hooks/pre-push")).contains("# jarbase:git-hook");
    }

    @Test
    void launcherAndLineEndingRulesAreInstalled() throws IOException {
        install();

        assertThat(read(".jarbase/jarbase")).isEqualTo("#!/bin/sh\necho launcher\n");
        assertThat(read(".jarbase/.gitattributes")).isEqualTo("* text eol=lf\n");
    }

    @Test
    void mavenModeWritesTheVersionAndNeverTouchesAJar() throws IOException {
        InstallAssets maven = new InstallAssets("#!/bin/sh\necho hook\n", "#!/bin/sh\necho launcher\n", null, "1.2.3");

        List<Change> changes = Installer.install(project, "docs", claude, maven);

        assertThat(read(".jarbase/version")).isEqualTo("1.2.3\n");
        assertThat(changes).extracting(Change::path).doesNotContain(".jarbase/jarbase.jar");
        assertThat(project.resolve(".jarbase/jarbase.jar")).doesNotExist();
    }

    @Test
    void changingTheMavenVersionUpdatesTheVersionFile() throws IOException {
        InstallAssets v1 = new InstallAssets("h\n", "l\n", null, "1.0.0");
        InstallAssets v2 = new InstallAssets("h\n", "l\n", null, "1.1.0");
        Installer.install(project, "docs", claude, v1);

        List<Change> changes = Installer.install(project, "docs", claude, v2);

        assertThat(read(".jarbase/version")).isEqualTo("1.1.0\n");
        assertThat(changes).anyMatch(c -> c.path().equals(".jarbase/version") && c.kind() == Change.Kind.UPDATED);
    }

    @Test
    void bundledScriptsHaveUnixLineEndingsAndTheRightShebang() {
        InstallAssets bundled = InstallAssets.bundled(null, "0.1.0");

        assertThat(bundled.hookScript()).startsWith("#!/bin/sh").doesNotContain("\r");
        assertThat(bundled.launcherScript()).startsWith("#!/bin/sh").doesNotContain("\r");
        assertThat(bundled.launcherScript()).contains("io/github/thedistortedwajdan/jarbase/jarbase-cli");
    }

    @Test
    void registersTheHookForBashCalls() throws IOException {
        install();

        var preToolUse = settings().getAsJsonObject("hooks").getAsJsonArray("PreToolUse");
        assertThat(preToolUse).hasSize(1);
        JsonObject entry = preToolUse.get(0).getAsJsonObject();
        assertThat(entry.get("matcher").getAsString()).isEqualTo("Bash");
        assertThat(entry.getAsJsonArray("hooks").get(0).getAsJsonObject().get("command").getAsString())
                .isEqualTo("sh \"$CLAUDE_PROJECT_DIR/.jarbase/jarbase-hook.sh\" claude-code");
    }

    @Test
    void runningTwiceChangesNothingTheSecondTime() throws IOException {
        install();
        String settingsBefore = read(".claude/settings.json");

        List<Change> second = install();

        assertThat(second).extracting(Change::kind)
                .doesNotContain(Change.Kind.CREATED, Change.Kind.UPDATED);
        assertThat(read(".claude/settings.json")).isEqualTo(settingsBefore);
        assertThat(settings().getAsJsonObject("hooks").getAsJsonArray("PreToolUse")).hasSize(1);
    }

    @Test
    void existingClaudeSettingsAreKept() throws IOException {
        Files.createDirectories(project.resolve(".claude"));
        Files.writeString(project.resolve(".claude/settings.json"), """
                {
                  "permissions": { "allow": ["Bash(ls:*)"] },
                  "hooks": {
                    "PreToolUse": [
                      { "matcher": "Edit", "hooks": [ { "type": "command", "command": "echo edit" } ] }
                    ],
                    "Stop": [ { "hooks": [ { "type": "command", "command": "echo done" } ] } ]
                  }
                }
                """);

        install();

        JsonObject result = settings();
        assertThat(result.getAsJsonObject("permissions").getAsJsonArray("allow")).hasSize(1);
        assertThat(result.getAsJsonObject("hooks").getAsJsonArray("Stop")).hasSize(1);
        var preToolUse = result.getAsJsonObject("hooks").getAsJsonArray("PreToolUse");
        assertThat(preToolUse).hasSize(2);
        assertThat(preToolUse.get(0).toString()).contains("echo edit");
        assertThat(preToolUse.get(1).toString()).contains("jarbase-hook.sh");
    }

    @Test
    void anOutdatedJarbaseEntryIsReplacedNotDuplicated() throws IOException {
        Files.createDirectories(project.resolve(".claude"));
        Files.writeString(project.resolve(".claude/settings.json"), """
                {"hooks":{"PreToolUse":[
                  {"matcher":"Bash","hooks":[{"type":"command","command":"old/path/jarbase-hook.sh x"}]},
                  {"matcher":"Edit","hooks":[{"type":"command","command":"echo edit"}]}
                ]}}
                """);

        install();

        var preToolUse = settings().getAsJsonObject("hooks").getAsJsonArray("PreToolUse");
        assertThat(preToolUse).hasSize(2);
        assertThat(preToolUse.get(0).toString()).contains("$CLAUDE_PROJECT_DIR/.jarbase/jarbase-hook.sh")
                .doesNotContain("old/path");
    }

    @Test
    void brokenSettingsFileIsNeverOverwritten() throws IOException {
        Files.createDirectories(project.resolve(".claude"));
        Files.writeString(project.resolve(".claude/settings.json"), "{ this is not json");

        assertThatThrownBy(this::install)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(".claude/settings.json");
        assertThat(read(".claude/settings.json")).isEqualTo("{ this is not json");
    }

    @Test
    void existingAgentsMdKeepsItsContent() throws IOException {
        Files.writeString(project.resolve("AGENTS.md"), "# Rules\n\nBe kind.\n");

        install();

        assertThat(read("AGENTS.md")).startsWith("# Rules\n\nBe kind.\n\n<!-- jarbase:start -->");
    }

    @Test
    void existingClaudeMdGetsTheImportAppended() throws IOException {
        Files.writeString(project.resolve("CLAUDE.md"), "# Claude notes\n");

        install();

        assertThat(read("CLAUDE.md")).isEqualTo("# Claude notes\n\n@AGENTS.md\n");
    }

    @Test
    void claudeMdThatAlreadyImportsIsLeftAlone() throws IOException {
        Files.writeString(project.resolve("CLAUDE.md"), "@AGENTS.md\n\nMore.\n");

        install();

        assertThat(read("CLAUDE.md")).isEqualTo("@AGENTS.md\n\nMore.\n");
    }

    @Test
    void existingDocsAreNotTouched() throws IOException {
        Files.createDirectories(project.resolve("docs"));
        Files.writeString(project.resolve("docs/billing.md"), "# Billing\n");

        List<Change> changes = install();

        assertThat(project.resolve("docs/index.md")).doesNotExist();
        assertThat(changes).anyMatch(c -> c.path().equals("docs/index.md") && c.kind() == Change.Kind.SKIPPED);
    }

    @Test
    void jarIsCopiedWhenOneIsGivenAndSkippedWhenNot(@TempDir Path elsewhere) throws IOException {
        assertThat(install()).anyMatch(c -> c.path().equals(".jarbase/jarbase.jar") && c.kind() == Change.Kind.SKIPPED);

        Path fakeJar = elsewhere.resolve("jarbase-all.jar");
        Files.writeString(fakeJar, "pretend this is a jar");
        InstallAssets withJar = new InstallAssets("#!/bin/sh\necho hook\n", "#!/bin/sh\necho launcher\n", fakeJar, null);

        List<Change> first = Installer.install(project, "docs", claude, withJar);
        List<Change> second = Installer.install(project, "docs", claude, withJar);

        assertThat(read(".jarbase/jarbase.jar")).isEqualTo("pretend this is a jar");
        assertThat(first).anyMatch(c -> c.path().equals(".jarbase/jarbase.jar") && c.kind() == Change.Kind.CREATED);
        assertThat(second).anyMatch(c -> c.path().equals(".jarbase/jarbase.jar") && c.kind() == Change.Kind.UNCHANGED);
    }
}
