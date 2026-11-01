package io.github.thedistortedwajdan.jarbase.core.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AgentRunnerTest {

    @TempDir
    Path folder;

    private Path sh() {
        assumeTrue(shellAvailable(), "no sh on this machine");
        return AgentRunner.findExecutable("sh", System.getenv()).orElseThrow();
    }

    @Test
    void findsAProgramOnThePath() throws IOException {
        Path tool = folder.resolve("my-agent");
        Files.writeString(tool, "#!/bin/sh\n");
        tool.toFile().setExecutable(true);

        Optional<Path> found = AgentRunner.findExecutable("my-agent", Map.of("PATH", "/nonexistent" + File.pathSeparator + folder));

        assertThat(found).isPresent();
        assertThat(found.get().getFileName().toString()).startsWith("my-agent");
    }

    @Test
    void aProgramThatIsNotThereIsNotFound() {
        assertThat(AgentRunner.findExecutable("no-such-agent-anywhere", Map.of("PATH", folder.toString()))).isEmpty();
        assertThat(AgentRunner.findExecutable("sh", Map.of())).isEmpty();
    }

    @Test
    void runsAProgramAndReturnsItsOutput() {
        AgentRunner.Result result = AgentRunner.run(sh(), List.of("-c", "echo hello; echo oops 1>&2"), null, folder,
                Map.of(), Duration.ofSeconds(30));

        assertThat(result.succeeded()).isTrue();
        assertThat(result.output()).contains("hello").contains("oops");
    }

    @Test
    void theInstructionArrivesOnStandardInput() {
        AgentRunner.Result result = AgentRunner.run(sh(), List.of("-c", "cat"), "line one\nline two with \"quotes\" and `ticks`\n",
                folder, Map.of(), Duration.ofSeconds(30));

        assertThat(result.output()).isEqualTo("line one\nline two with \"quotes\" and `ticks`");
    }

    @Test
    void aProgramThatAsksForMoreInputGetsEndOfInputInsteadOfWaiting() {
        AgentRunner.Result result = AgentRunner.run(sh(), List.of("-c", "read answer; echo done"), null, folder, Map.of(),
                Duration.ofSeconds(30));

        assertThat(result.timedOut()).isFalse();
        assertThat(result.output()).contains("done");
    }

    @Test
    void extraEnvironmentVariablesAreSet() {
        AgentRunner.Result result = AgentRunner.run(sh(), List.of("-c", "echo $JARBASE_TEST_VALUE"), null, folder,
                Map.of("JARBASE_TEST_VALUE", "visible"), Duration.ofSeconds(30));

        assertThat(result.output()).isEqualTo("visible");
    }

    @Test
    void aNonZeroExitCodeIsReported() {
        AgentRunner.Result result = AgentRunner.run(sh(), List.of("-c", "exit 4"), null, folder, Map.of(), Duration.ofSeconds(30));

        assertThat(result.started()).isTrue();
        assertThat(result.exitCode()).isEqualTo(4);
        assertThat(result.succeeded()).isFalse();
    }

    @Test
    void aSlowProgramIsStopped() {
        long start = System.nanoTime();

        AgentRunner.Result result = AgentRunner.run(sh(), List.of("-c", "sleep 30"), null, folder, Map.of(),
                Duration.ofSeconds(1));

        assertThat(result.timedOut()).isTrue();
        assertThat(result.succeeded()).isFalse();
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(15));
    }

    @Test
    void aProgramThatCannotBeStartedIsReportedNotThrown() {
        AgentRunner.Result result = AgentRunner.run(folder.resolve("does-not-exist"), List.of(), null, folder, Map.of(),
                Duration.ofSeconds(5));

        assertThat(result.started()).isFalse();
        assertThat(result.succeeded()).isFalse();
    }

    @Test
    void veryLongOutputIsCutToItsEnd() {
        AgentRunner.Result result = AgentRunner.run(sh(), List.of("-c", "i=0; while [ $i -lt 600 ]; do echo line-$i-xxxxxxxxxx; i=$((i+1)); done"),
                null, folder, Map.of(), Duration.ofSeconds(30));

        assertThat(result.output().length()).isLessThan(1600);
        assertThat(result.output()).endsWith("line-599-xxxxxxxxxx").startsWith("...");
    }

    private static boolean shellAvailable() {
        try {
            Process process = new ProcessBuilder("sh", "-c", "exit 0").start();
            return process.waitFor(10, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }
}
