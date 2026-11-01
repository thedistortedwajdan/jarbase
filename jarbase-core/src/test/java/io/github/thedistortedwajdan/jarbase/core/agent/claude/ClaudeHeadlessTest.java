package io.github.thedistortedwajdan.jarbase.core.agent.claude;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.thedistortedwajdan.jarbase.core.agent.HeadlessCommand;
import org.junit.jupiter.api.Test;

/** What Claude Code is asked to do when jarbase runs it headlessly. */
class ClaudeHeadlessTest {

    private final HeadlessCommand command = new ClaudeCodeAdapter().headlessCommand("update the docs", "docs").orElseThrow();

    @Test
    void runsTheClaudeProgramInPrintMode() {
        assertThat(command.program()).isEqualTo("claude");
        assertThat(command.args()).startsWith("-p", "--allowedTools");
    }

    @Test
    void theInstructionTravelsOnStandardInputNotAsAnArgument() {
        assertThat(command.stdin()).isEqualTo("update the docs");
        assertThat(command.args()).doesNotContain("update the docs");
    }

    @Test
    void theAgentMayEditOnlyTheDocsFolder() {
        String tools = command.args().get(2);

        assertThat(tools).contains("Edit(docs/**)");
        // Claude Code rejects Write(path) rules outright ("only Edit(path) rules are matched"); Edit covers creating files too
        assertThat(tools).doesNotContain("Write(");
        assertThat(tools.split(",")).noneMatch(t -> t.startsWith("Edit(") && !t.equals("Edit(docs/**)"));
    }

    @Test
    void theAgentMayReadTheProjectAndRunOnlyGitReadsAndJarbase() {
        String tools = command.args().get(2);

        assertThat(tools).contains("Read", "Grep", "Glob", "Bash(git diff:*)", "Bash(sh .jarbase/jarbase:*)");
        assertThat(tools).doesNotContain("Bash(git push").doesNotContain("Bash(git commit").doesNotContain("Bash(*");
    }

    @Test
    void aDifferentDocsFolderIsRespected() {
        HeadlessCommand other = new ClaudeCodeAdapter().headlessCommand("x", "documentation").orElseThrow();

        assertThat(other.args().get(2)).contains("Edit(documentation/**)").doesNotContain("Edit(docs/**)");
    }
}
