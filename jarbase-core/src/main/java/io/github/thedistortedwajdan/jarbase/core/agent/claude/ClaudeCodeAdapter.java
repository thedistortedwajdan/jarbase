package io.github.thedistortedwajdan.jarbase.core.agent.claude;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import io.github.thedistortedwajdan.jarbase.core.agent.AgentAdapter;
import io.github.thedistortedwajdan.jarbase.core.agent.PushIntent;
import io.github.thedistortedwajdan.jarbase.core.hook.HookDecision;
import io.github.thedistortedwajdan.jarbase.core.install.Change;
import io.github.thedistortedwajdan.jarbase.core.install.ProjectFiles;
import io.github.thedistortedwajdan.jarbase.core.json.JsonText;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Claude Code's {@code PreToolUse} hook. Claude sends JSON on standard input, for example
 * {@code {"tool_name":"Bash","tool_input":{"command":"git push"},"cwd":"/work/app"}}, and reads
 * a JSON answer from standard output.
 */
public final class ClaudeCodeAdapter implements AgentAdapter {

    private static final String CLAUDE_MD = "CLAUDE.md";
    private static final String IMPORT_LINE = "@AGENTS.md";

    @Override
    public String id() {
        return "claude-code";
    }

    @Override
    public Optional<PushIntent> parseBeforeShellCommand(String stdin) {
        JsonElement parsed;
        try {
            parsed = JsonParser.parseString(stdin);
        } catch (JsonSyntaxException e) {
            return Optional.empty();
        }
        if (!parsed.isJsonObject()) {
            return Optional.empty();
        }
        JsonObject payload = parsed.getAsJsonObject();
        if (!"Bash".equals(string(payload, "tool_name"))) {
            return Optional.empty();
        }
        JsonElement input = payload.get("tool_input");
        if (input == null || !input.isJsonObject()) {
            return Optional.empty();
        }
        String command = string(input.getAsJsonObject(), "command");
        if (command == null) {
            return Optional.empty();
        }
        String cwd = string(payload, "cwd");
        Path directory = cwd == null || cwd.isBlank() ? Path.of(".") : Path.of(cwd);
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

    @Override
    public boolean isRunningInside(Map<String, String> env) {
        // Claude Code sets CLAUDECODE=1 in the environment of the shell commands it runs
        return env.containsKey("CLAUDECODE");
    }

    @Override
    public List<Change> install(Path projectRoot) {
        return List.of(ClaudeSettings.ensureHook(projectRoot), ensureClaudeMdImport(projectRoot));
    }

    /**
     * Claude Code reads {@code CLAUDE.md}, not {@code AGENTS.md}. A line {@code @AGENTS.md} in it pulls
     * the shared instructions in, so Claude sees the same rules as every other agent.
     */
    private static Change ensureClaudeMdImport(Path root) {
        Optional<String> existing = ProjectFiles.read(root, CLAUDE_MD);
        if (existing.isEmpty() || existing.get().isBlank()) {
            return ProjectFiles.write(root, CLAUDE_MD, IMPORT_LINE + "\n");
        }
        boolean alreadyImported = existing.get().lines().anyMatch(line -> line.strip().equals(IMPORT_LINE));
        if (alreadyImported) {
            return Change.of(Change.Kind.UNCHANGED, CLAUDE_MD);
        }
        return ProjectFiles.write(root, CLAUDE_MD, existing.get().stripTrailing() + "\n\n" + IMPORT_LINE + "\n");
    }

    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                ? value.getAsString()
                : null;
    }
}
