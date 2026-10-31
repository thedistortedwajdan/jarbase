package io.github.thedistortedwajdan.jarbase.core.agent.claude;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import io.github.thedistortedwajdan.jarbase.core.install.Change;
import io.github.thedistortedwajdan.jarbase.core.install.ProjectFiles;
import java.nio.file.Path;
import java.util.Optional;

/** Adds jarbase's hook to {@code .claude/settings.json} without disturbing anything else in it. */
final class ClaudeSettings {

    static final String FILE = ".claude/settings.json";
    static final String HOOK_COMMAND = "sh \"$CLAUDE_PROJECT_DIR/.jarbase/jarbase-hook.sh\" claude-code";

    private static final String OUR_SCRIPT = "jarbase-hook.sh";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private ClaudeSettings() {
    }

    static Change ensureHook(Path root) {
        Optional<String> existing = ProjectFiles.read(root, FILE);
        JsonObject settings = parse(existing);
        JsonObject before = settings.deepCopy();

        JsonObject hooks = object(settings, "hooks");
        JsonArray preToolUse = array(hooks, "PreToolUse");
        putOurEntry(preToolUse);

        if (existing.isPresent() && settings.equals(before)) {
            return Change.of(Change.Kind.UNCHANGED, FILE);
        }
        return ProjectFiles.write(root, FILE, GSON.toJson(settings) + "\n");
    }

    private static JsonObject parse(Optional<String> text) {
        if (text.isEmpty() || text.get().isBlank()) {
            return new JsonObject();
        }
        try {
            JsonElement parsed = JsonParser.parseString(text.get());
            if (parsed.isJsonObject()) {
                return parsed.getAsJsonObject();
            }
        } catch (JsonSyntaxException ignored) {
            // fall through to the error below
        }
        throw new IllegalStateException(FILE + " is not a valid JSON object. Fix it (or remove it) and run install again;"
                + " jarbase will not overwrite a file it cannot read.");
    }

    private static JsonObject object(JsonObject parent, String key) {
        if (!parent.has(key) || !parent.get(key).isJsonObject()) {
            parent.add(key, new JsonObject());
        }
        return parent.getAsJsonObject(key);
    }

    private static JsonArray array(JsonObject parent, String key) {
        if (!parent.has(key) || !parent.get(key).isJsonArray()) {
            parent.add(key, new JsonArray());
        }
        return parent.getAsJsonArray(key);
    }

    /**
     * Puts our entry in place of an earlier jarbase entry (keeping its position), or adds it at the end.
     * Running install twice therefore never duplicates it.
     */
    private static void putOurEntry(JsonArray preToolUse) {
        int first = -1;
        for (int i = preToolUse.size() - 1; i >= 0; i--) {
            if (preToolUse.get(i).toString().contains(OUR_SCRIPT)) {
                if (first >= 0) {
                    preToolUse.remove(first); // a duplicate further along; keep the earliest
                }
                first = i;
            }
        }
        if (first >= 0) {
            preToolUse.set(first, ourEntry());
        } else {
            preToolUse.add(ourEntry());
        }
    }

    private static JsonObject ourEntry() {
        JsonObject command = new JsonObject();
        command.addProperty("type", "command");
        command.addProperty("command", HOOK_COMMAND);

        JsonArray commands = new JsonArray();
        commands.add(command);

        JsonObject entry = new JsonObject();
        entry.addProperty("matcher", "Bash");
        entry.add("hooks", commands);
        return entry;
    }
}
