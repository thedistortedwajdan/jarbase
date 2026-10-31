package dev.wajdan.jarbase.core.agent.claude;

import static org.assertj.core.api.Assertions.assertThat;

import dev.wajdan.jarbase.core.agent.AgentAdapters;
import dev.wajdan.jarbase.core.agent.PushIntent;
import dev.wajdan.jarbase.core.hook.HookDecision;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ClaudeCodeAdapterTest {

    private final ClaudeCodeAdapter adapter = new ClaudeCodeAdapter();

    @Test
    void readsABashToolCall() {
        String stdin = "{\"session_id\":\"abc\",\"cwd\":\"/work/app\",\"hook_event_name\":\"PreToolUse\","
                + "\"tool_name\":\"Bash\",\"tool_input\":{\"command\":\"git push origin main\",\"description\":\"push\"}}";

        Optional<PushIntent> intent = adapter.parseBeforeShellCommand(stdin);

        assertThat(intent).isPresent();
        assertThat(intent.get().command()).isEqualTo("git push origin main");
        assertThat(intent.get().workingDirectory()).isEqualTo(Path.of("/work/app"));
    }

    @Test
    void handlesEscapesInTheCommand() {
        String stdin = "{\"tool_name\":\"Bash\",\"tool_input\":{\"command\":\"echo \\\"hi\\\" && git push\\n\"}}";

        assertThat(adapter.parseBeforeShellCommand(stdin).orElseThrow().command()).isEqualTo("echo \"hi\" && git push\n");
    }

    @Test
    void windowsWorkingDirectoryWithEscapedBackslashes() {
        String stdin = "{\"cwd\":\"C:\\\\Users\\\\me\\\\app\",\"tool_name\":\"Bash\",\"tool_input\":{\"command\":\"git push\"}}";

        assertThat(adapter.parseBeforeShellCommand(stdin).orElseThrow().workingDirectory().toString())
                .isEqualTo(Path.of("C:\\Users\\me\\app").toString());
    }

    @Test
    void missingCwdMeansCurrentDirectory() {
        String stdin = "{\"tool_name\":\"Bash\",\"tool_input\":{\"command\":\"git push\"}}";

        assertThat(adapter.parseBeforeShellCommand(stdin).orElseThrow().workingDirectory()).isEqualTo(Path.of("."));
    }

    @Test
    void ignoresOtherTools() {
        String stdin = "{\"tool_name\":\"Edit\",\"tool_input\":{\"file_path\":\"a.txt\"}}";

        assertThat(adapter.parseBeforeShellCommand(stdin)).isEmpty();
    }

    @Test
    void ignoresGarbageAndEmptyInput() {
        assertThat(adapter.parseBeforeShellCommand("")).isEmpty();
        assertThat(adapter.parseBeforeShellCommand("not json {{{")).isEmpty();
        assertThat(adapter.parseBeforeShellCommand("[1,2,3]")).isEmpty();
        assertThat(adapter.parseBeforeShellCommand("{\"tool_name\":\"Bash\"}")).isEmpty();
    }

    @Test
    void allowingPrintsNothing() {
        assertThat(adapter.render(HookDecision.allow())).isEmpty();
    }

    @Test
    void blockingPrintsClaudeCodesDenyFormat() {
        String out = adapter.render(HookDecision.block("Update docs/a.md\nthen \"push\" again"));

        assertThat(out).isEqualTo("{\"hookSpecificOutput\":{\"hookEventName\":\"PreToolUse\","
                + "\"permissionDecision\":\"deny\","
                + "\"permissionDecisionReason\":\"Update docs/a.md\\nthen \\\"push\\\" again\"}}\n");
    }

    @Test
    void isDiscoverableByItsId() {
        assertThat(AgentAdapters.find("claude-code")).containsInstanceOf(ClaudeCodeAdapter.class);
        assertThat(AgentAdapters.find("no-such-agent")).isEmpty();
        assertThat(AgentAdapters.ids()).contains("claude-code");
    }
}
