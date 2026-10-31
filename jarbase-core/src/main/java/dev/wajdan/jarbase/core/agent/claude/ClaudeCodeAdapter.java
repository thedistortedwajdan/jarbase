package dev.wajdan.jarbase.core.agent.claude;

import dev.wajdan.jarbase.core.agent.AgentAdapter;
import dev.wajdan.jarbase.core.agent.PushIntent;
import dev.wajdan.jarbase.core.hook.HookDecision;
import dev.wajdan.jarbase.core.json.JsonText;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

/**
 * Claude Code's {@code PreToolUse} hook. Claude sends JSON on standard input, for example
 * {@code {"tool_name":"Bash","tool_input":{"command":"git push"},"cwd":"/work/app"}}, and reads
 * a JSON answer from standard output.
 */
public final class ClaudeCodeAdapter implements AgentAdapter {

    @Override
    public String id() {
        return "claude-code";
    }

    @Override
    public Optional<PushIntent> parseBeforeShellCommand(String stdin) {
        Object parsed;
        try {
            // JSON is a subset of YAML, so the YAML reader we already have reads it safely.
            parsed = new Yaml(new SafeConstructor(new LoaderOptions())).load(stdin);
        } catch (YAMLException e) {
            return Optional.empty();
        }
        if (!(parsed instanceof Map<?, ?> payload) || !"Bash".equals(payload.get("tool_name"))) {
            return Optional.empty();
        }
        if (!(payload.get("tool_input") instanceof Map<?, ?> input) || !(input.get("command") instanceof String command)) {
            return Optional.empty();
        }
        Path directory = payload.get("cwd") instanceof String cwd && !cwd.isBlank() ? Path.of(cwd) : Path.of(".");
        return Optional.of(new PushIntent(command, directory));
    }

    @Override
    public String render(HookDecision decision) {
        if (!decision.block()) {
            return "";
        }
        return "{\"hookSpecificOutput\":{\"hookEventName\":\"PreToolUse\","
                + "\"permissionDecision\":\"deny\","
                + "\"permissionDecisionReason\":" + JsonText.quote(decision.reason()) + "}}\n";
    }
}
