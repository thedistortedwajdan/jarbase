package io.github.thedistortedwajdan.jarbase.core.hook;

import io.github.thedistortedwajdan.jarbase.core.agent.AgentAdapter;
import io.github.thedistortedwajdan.jarbase.core.agent.HeadlessCommand;
import io.github.thedistortedwajdan.jarbase.core.agent.PushIntent;
import io.github.thedistortedwajdan.jarbase.core.install.Change;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** A pretend coding agent for tests: "running" it runs a shell script that plays the agent's part. */
final class FakeAgent implements AgentAdapter {

    private final String id;
    private final String program;
    private final String script;

    FakeAgent(String script) {
        this("fake-agent", "sh", script);
    }

    FakeAgent(String id, String program, String script) {
        this.id = id;
        this.program = program;
        this.script = script;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public Optional<PushIntent> parseBeforeShellCommand(String stdin) {
        return Optional.empty();
    }

    @Override
    public String render(HookDecision decision) {
        return "";
    }

    @Override
    public Optional<HeadlessCommand> headlessCommand(String instruction, String docsFolder) {
        return Optional.of(new HeadlessCommand(program, List.of("-c", script), instruction));
    }

    @Override
    public boolean isRunningInside(Map<String, String> env) {
        return false;
    }

    @Override
    public List<Change> install(Path projectRoot) {
        return List.of();
    }
}
