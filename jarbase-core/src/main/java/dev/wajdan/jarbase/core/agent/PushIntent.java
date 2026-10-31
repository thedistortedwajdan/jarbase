package dev.wajdan.jarbase.core.agent;

import java.nio.file.Path;

/**
 * "The agent is about to run this shell command from this folder." The agent-neutral form of a hook call.
 *
 * @param command          the shell command text
 * @param workingDirectory where the agent runs it ({@code .} when the agent did not say)
 */
public record PushIntent(String command, Path workingDirectory) {
}
