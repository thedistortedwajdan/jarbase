package io.github.thedistortedwajdan.jarbase.core.agent;

import io.github.thedistortedwajdan.jarbase.core.hook.HookDecision;
import io.github.thedistortedwajdan.jarbase.core.install.Change;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Everything that is specific to one coding agent (Claude Code, Codex, Cursor...). The rest of
 * jarbase never mentions an agent by name: it asks an adapter to translate.
 *
 * <p>New agents plug in by adding an implementation and listing it in
 * {@code META-INF/services/io.github.thedistortedwajdan.jarbase.core.agent.AgentAdapter}.
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
     * How to run this agent once, headlessly, to carry out an instruction. This is what lets {@code git push}
     * update the docs for someone who is coding by hand with no agent session open.
     *
     * <p>The command must let the agent read the project and edit files under the docs folder only, and must
     * not let it ask questions.
     *
     * @param instruction the full text of what the agent should do
     * @param docsFolder  the only folder the agent may edit, relative to the repository root
     * @return the command, or empty if this agent has no headless mode
     */
    Optional<HeadlessCommand> headlessCommand(String instruction, String docsFolder);

    /**
     * Whether the current process is running inside a session of this agent (for example because the agent
     * started the shell command that is running now). The git hook uses this to stay out of the agent's way:
     * the agent has its own hook and instructions.
     *
     * @param env the process's environment variables
     */
    boolean isRunningInside(Map<String, String> env);

    /**
     * Registers the hook with this agent and writes any agent-specific files. Must be safe to run twice.
     * The shared tool files (the hook script under {@code .jarbase/}) are already in place when this runs.
     *
     * @param projectRoot the repository's top folder
     */
    List<Change> install(Path projectRoot);
}
