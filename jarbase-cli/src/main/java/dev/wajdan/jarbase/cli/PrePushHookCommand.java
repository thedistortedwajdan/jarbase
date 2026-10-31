package dev.wajdan.jarbase.cli;

import dev.wajdan.jarbase.core.agent.AgentAdapter;
import dev.wajdan.jarbase.core.agent.AgentAdapters;
import dev.wajdan.jarbase.core.agent.PushIntent;
import dev.wajdan.jarbase.core.hook.HookDecision;
import dev.wajdan.jarbase.core.hook.PrePushCheck;
import dev.wajdan.jarbase.core.hook.PushDetector;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/**
 * Called by a coding agent just before it runs a shell command. If the command is a push and the
 * docs are stale, it answers "deny" once, with the list of pages to update.
 *
 * <p>This command never exits with code 2 (which Claude Code treats as "block"). Any internal
 * problem prints a note to standard error and exits 1, so a bug here can never stop a push.
 */
@Command(
        name = "pre-push",
        mixinStandardHelpOptions = true,
        description = "Blocks a push once when code changed but the docs pages describing it did not.",
        footer = {"", "Set JARBASE_HOOKS=off to turn the hook off for a session."})
public class PrePushHookCommand implements Callable<Integer> {

    @Spec
    CommandSpec spec;

    @Option(names = "--agent", defaultValue = "claude-code",
            description = "Which coding agent is calling. Default: ${DEFAULT-VALUE}.")
    String agent;

    @Option(names = "--docs", defaultValue = "docs", description = "Docs folder relative to the repository root.")
    String docsFolder;

    @Option(names = "--base", description = "Compare against this branch instead of working it out.")
    String base;

    @Override
    public Integer call() {
        try {
            return run();
        } catch (Exception e) {
            spec.commandLine().getErr().println("jarbase hook: skipped (" + e.getMessage() + ")");
            spec.commandLine().getErr().flush();
            return 1;
        }
    }

    private Integer run() throws IOException {
        if (isDisabled(System.getenv("JARBASE_HOOKS"))) {
            return 0;
        }

        AgentAdapter adapter = AgentAdapters.find(agent).orElseThrow(() -> new IllegalArgumentException(
                "unknown agent '" + agent + "'; available: " + String.join(", ", AgentAdapters.ids())));

        String stdin = new String(System.in.readAllBytes(), StandardCharsets.UTF_8);
        Optional<PushIntent> intent = adapter.parseBeforeShellCommand(stdin);
        if (intent.isEmpty() || !PushDetector.isPush(intent.get().command())) {
            return 0;
        }

        HookDecision decision = PrePushCheck.decide(intent.get().workingDirectory(), docsFolder, base);
        spec.commandLine().getOut().print(adapter.render(decision));
        spec.commandLine().getOut().flush();
        return 0;
    }

    static boolean isDisabled(String value) {
        if (value == null) {
            return false;
        }
        String v = value.trim().toLowerCase(Locale.ROOT);
        return v.equals("off") || v.equals("0") || v.equals("false");
    }
}
