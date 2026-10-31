package dev.wajdan.jarbase.cli;

import static org.assertj.core.api.Assertions.assertThat;

import dev.wajdan.jarbase.core.json.JsonText;
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
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

/** Plays the part of Claude Code: feeds the hook its JSON on standard input and reads the answer. */
class PrePushHookCommandTest {

    @TempDir
    Path repo;

    private final StringWriter out = new StringWriter();
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

    private int callHook(String claudeJson, String... extraArgs) {
        System.setIn(new ByteArrayInputStream(claudeJson.getBytes(StandardCharsets.UTF_8)));
        CommandLine cli = Main.commandLine();
        cli.setOut(new PrintWriter(out));
        cli.setErr(new PrintWriter(err));
        List<String> args = new ArrayList<>(List.of("hook", "pre-push"));
        args.addAll(List.of(extraArgs));
        return cli.execute(args.toArray(String[]::new));
    }

    private String bashCall(String command) {
        return "{\"cwd\":" + JsonText.quote(repo.toString()) + ",\"tool_name\":\"Bash\","
                + "\"tool_input\":{\"command\":" + JsonText.quote(command) + "}}";
    }

    @Test
    void pushWithStaleDocsIsDeniedOnce() {
        int first = callHook(bashCall("git push origin feature"));

        assertThat(first).isZero();
        assertThat(out.toString()).contains("\"permissionDecision\":\"deny\"").contains("docs/billing.md");

        out.getBuffer().setLength(0);
        int second = callHook(bashCall("git push origin feature"));

        assertThat(second).isZero();
        assertThat(out.toString()).isEmpty();
    }

    @Test
    void commandsThatAreNotPushesPassSilently() {
        assertThat(callHook(bashCall("ls -la"))).isZero();
        assertThat(callHook(bashCall("git status"))).isZero();

        assertThat(out.toString()).isEmpty();
        assertThat(err.toString()).isEmpty();
    }

    @Test
    void otherToolsPassSilently() {
        String edit = "{\"cwd\":" + JsonText.quote(repo.toString())
                + ",\"tool_name\":\"Edit\",\"tool_input\":{\"file_path\":\"a.txt\"}}";

        assertThat(callHook(edit)).isZero();
        assertThat(out.toString()).isEmpty();
    }

    @Test
    void garbageInputPassesSilently() {
        assertThat(callHook("this is not json")).isZero();
        assertThat(out.toString()).isEmpty();
    }

    @Test
    void aHookProblemNeverBlocksAndNeverExitsWithTwo() {
        // a folder that is not a git repository: the check cannot run
        String json = "{\"cwd\":" + JsonText.quote(repo.getParent().resolveSibling("definitely-not-a-repo").toString())
                + ",\"tool_name\":\"Bash\",\"tool_input\":{\"command\":\"git push\"}}";

        int exit = callHook(json);

        assertThat(exit).isEqualTo(1);
        assertThat(out.toString()).isEmpty();
        assertThat(err.toString()).startsWith("jarbase hook: skipped");
    }

    @Test
    void unknownAgentIsReportedWithoutBlocking() {
        int exit = callHook(bashCall("git push"), "--agent", "no-such-agent");

        assertThat(exit).isEqualTo(1);
        assertThat(err.toString()).contains("unknown agent 'no-such-agent'").contains("claude-code");
    }

    @Test
    void offSwitchValues() {
        assertThat(PrePushHookCommand.isDisabled("off")).isTrue();
        assertThat(PrePushHookCommand.isDisabled("OFF")).isTrue();
        assertThat(PrePushHookCommand.isDisabled("0")).isTrue();
        assertThat(PrePushHookCommand.isDisabled("false")).isTrue();
        assertThat(PrePushHookCommand.isDisabled("on")).isFalse();
        assertThat(PrePushHookCommand.isDisabled("")).isFalse();
        assertThat(PrePushHookCommand.isDisabled(null)).isFalse();
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
