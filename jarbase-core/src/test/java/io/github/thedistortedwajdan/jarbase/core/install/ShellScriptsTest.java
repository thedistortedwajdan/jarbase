package io.github.thedistortedwajdan.jarbase.core.install;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Runs the real installed scripts with the system's {@code sh}. Skipped on machines without one. */
class ShellScriptsTest {

    private record Result(int exit, String out, String err) {
    }

    @TempDir
    Path project;

    @TempDir
    Path fakeHome;

    private Path jarbaseDir;

    @BeforeEach
    void installScripts() throws IOException {
        assumeTrue(shellAvailable(), "no sh on this machine");
        InstallAssets assets = InstallAssets.bundled(null, "9.9.9");
        jarbaseDir = project.resolve(".jarbase");
        Files.createDirectories(jarbaseDir);
        Files.writeString(jarbaseDir.resolve("jarbase-hook.sh"), assets.hookScript(), StandardCharsets.UTF_8);
        Files.writeString(jarbaseDir.resolve("jarbase"), assets.launcherScript(), StandardCharsets.UTF_8);
        Files.writeString(jarbaseDir.resolve("version"), "9.9.9\n");
    }

    @Test
    void hookIgnoresCommandsThatAreNotPushesWithoutNeedingTheTool() throws Exception {
        Result result = runHook("{\"tool_name\":\"Bash\",\"tool_input\":{\"command\":\"ls -la\"}}", Map.of());

        assertThat(result.exit()).isZero();
        assertThat(result.out()).isEmpty();
        assertThat(result.err()).isEmpty();
    }

    @Test
    void launcherExitsWith127AndExplainsWhenTheJarIsMissing() throws Exception {
        Result result = run(List.of("sh", jarbaseDir.resolve("jarbase").toString(), "affected"), "", env());

        assertThat(result.exit()).isEqualTo(127);
        assertThat(result.err()).contains("cannot find the jarbase jar").contains("9.9.9");
    }

    @Test
    void aMissingJarNeverBlocksAPush() throws Exception {
        Result result = runHook("{\"tool_name\":\"Bash\",\"tool_input\":{\"command\":\"git push\"}}", Map.of());

        assertThat(result.exit()).isZero();
        assertThat(result.out()).isEmpty();
        assertThat(result.err()).contains("cannot find the jarbase jar");
    }

    @Test
    void launcherFindsTheJarInTheMavenRepositoryByTheCommittedVersion() throws Exception {
        Path jar = fakeHome.resolve("repo/io/github/thedistortedwajdan/jarbase/jarbase-cli/9.9.9/jarbase-cli-9.9.9-all.jar");
        Files.createDirectories(jar.getParent());
        Files.writeString(jar, "not a real jar");
        Path fakeBin = fakeHome.resolve("bin");
        Files.createDirectories(fakeBin);
        Files.writeString(fakeBin.resolve("java"), "#!/bin/sh\necho \"FAKE-JAVA $*\"\n");
        fakeBin.resolve("java").toFile().setExecutable(true);

        Map<String, String> env = env();
        env.put("JARBASE_M2_REPO", fakeHome.resolve("repo").toString());
        env.put("PATH", fakeBin + java.io.File.pathSeparator + System.getenv("PATH"));
        Result result = run(List.of("sh", jarbaseDir.resolve("jarbase").toString(), "validate", "--format", "json"), "", env);

        assertThat(result.exit()).isZero();
        assertThat(result.out()).contains("FAKE-JAVA -jar").contains("jarbase-cli-9.9.9-all.jar").contains("validate --format json");
    }

    // ---- helpers ----

    private Result runHook(String stdin, Map<String, String> extraEnv) throws Exception {
        Map<String, String> env = env();
        env.putAll(extraEnv);
        return run(List.of("sh", jarbaseDir.resolve("jarbase-hook.sh").toString(), "claude-code"), stdin, env);
    }

    /** An environment where the jar cannot be found by accident. */
    private Map<String, String> env() {
        Map<String, String> env = new java.util.HashMap<>();
        env.put("HOME", fakeHome.toString());
        env.put("JARBASE_M2_REPO", fakeHome.resolve("empty-repo").toString());
        return env;
    }

    private Result run(List<String> command, String stdin, Map<String, String> env) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(command).directory(project.toFile());
        builder.environment().putAll(env);
        Process process = builder.start();
        process.getOutputStream().write(stdin.getBytes(StandardCharsets.UTF_8));
        process.getOutputStream().close();
        String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String err = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.waitFor(30, TimeUnit.SECONDS)).isTrue();
        return new Result(process.exitValue(), out, err);
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
