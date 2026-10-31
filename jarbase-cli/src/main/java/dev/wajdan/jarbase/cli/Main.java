package dev.wajdan.jarbase.cli;

import picocli.CommandLine;

public final class Main {

    private Main() {
    }

    /** Builds the command line. Kept separate from {@link #main} so tests can use it without exiting the JVM. */
    static CommandLine commandLine() {
        return new CommandLine(new JarbaseCommand())
                .setExecutionExceptionHandler((exception, commandLine, parseResult) -> {
                    commandLine.getErr().println("jarbase: " + exception.getMessage());
                    commandLine.getErr().flush();
                    return 2;
                });
    }

    public static void main(String[] args) {
        System.exit(commandLine().execute(args));
    }
}
