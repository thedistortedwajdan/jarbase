package io.github.thedistortedwajdan.jarbase.core.agent;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** Finds a coding agent's program on the {@code PATH} and runs it once with a time limit. */
public final class AgentRunner {

    private static final int OUTPUT_TAIL = 1500;

    private AgentRunner() {
    }

    /**
     * What happened when the agent ran.
     *
     * @param started  false if the program could not be started at all
     * @param exitCode the program's exit code (meaningless when it timed out or did not start)
     * @param timedOut true if it was stopped for taking too long
     * @param output   the end of whatever it printed
     */
    public record Result(boolean started, int exitCode, boolean timedOut, String output) {

        public boolean succeeded() {
            return started && !timedOut && exitCode == 0;
        }
    }

    /** The program's full path if it is on the {@code PATH}, trying the usual Windows extensions too. */
    public static Optional<Path> findExecutable(String program, Map<String, String> env) {
        String path = env.getOrDefault("PATH", env.getOrDefault("Path", ""));
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        List<String> suffixes = windows ? List.of(".exe", ".cmd", ".bat", "") : List.of("");

        for (String folder : path.split(java.io.File.pathSeparator)) {
            if (folder.isBlank()) {
                continue;
            }
            for (String suffix : suffixes) {
                Path candidate;
                try {
                    candidate = Path.of(folder).resolve(program + suffix);
                } catch (RuntimeException invalidPath) {
                    continue;
                }
                if (Files.isRegularFile(candidate) && (windows || Files.isExecutable(candidate))) {
                    return Optional.of(candidate);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Runs the program, sends it {@code stdin} (may be null), merges its output, and stops it when {@code timeout} passes.
     *
     * @param extraEnv environment variables to add for the program
     */
    public static Result run(Path executable, List<String> args, String stdin, Path workingDirectory,
            Map<String, String> extraEnv,
            Duration timeout) {
        List<String> command = new ArrayList<>();
        command.add(executable.toString());
        command.addAll(args);

        ProcessBuilder builder = new ProcessBuilder(command).directory(workingDirectory.toFile()).redirectErrorStream(true);
        builder.environment().putAll(extraEnv);

        Process process;
        try {
            process = builder.start();
            // The instruction goes in on standard input. After it nobody is typing: an agent that asks a question
            // gets end-of-input instead of waiting forever.
            if (stdin != null) {
                process.getOutputStream().write(stdin.getBytes(StandardCharsets.UTF_8));
            }
            process.getOutputStream().close();
        } catch (IOException e) {
            return new Result(false, -1, false, e.getMessage());
        }

        CompletableFuture<String> output = CompletableFuture.supplyAsync(() -> readAll(process.getInputStream()));
        try {
            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
            }
            return new Result(true, finished ? process.exitValue() : -1, !finished, tail(output));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            return new Result(true, -1, true, "interrupted");
        }
    }

    private static String tail(CompletableFuture<String> output) {
        String text;
        try {
            text = output.get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            return "";
        }
        text = text.strip();
        return text.length() <= OUTPUT_TAIL ? text : "..." + text.substring(text.length() - OUTPUT_TAIL);
    }

    private static String readAll(InputStream stream) {
        try (stream) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }
}
