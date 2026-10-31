package dev.wajdan.jarbase.core.agent;

import dev.wajdan.jarbase.core.hook.HookDecision;
import dev.wajdan.jarbase.core.install.Change;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Everything that is specific to one coding agent (Claude Code, Codex, Cursor...). The rest of
 * jarbase never mentions an agent by name: it asks an adapter to translate.
 *
 * <p>New agents plug in by adding an implementation and listing it in
 * {@code META-INF/services/dev.wajdan.jarbase.core.agent.AgentAdapter}.
 */
public interface AgentAdapter {

    /** Short stable name used on the command line, for example {@code claude-code}. */
    String id();

    /**
     * Reads what the agent sent to its "before a tool runs" hook.
     *
     * @param stdin the raw text the agent wrote to the hook's standard input
     * @return the shell command about to run, or empty if this is not a shell command or cannot be understood
     */
    Optional<PushIntent> parseBeforeShellCommand(String stdin);

    /**
     * Turns a decision into what the agent expects on standard output.
     *
     * @return the text to print; empty when the push is allowed and nothing needs to be said
     */
    String render(HookDecision decision);

    /**
     * Registers the hook with this agent and writes any agent-specific files. Must be safe to run twice.
     * The shared tool files (the hook script under {@code .jarbase/}) are already in place when this runs.
     *
     * @param projectRoot the repository's top folder
     */
    List<Change> install(Path projectRoot);
}
