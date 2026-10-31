package dev.wajdan.jarbase.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;

/** The top-level {@code jarbase} command. On its own it just shows help. */
@Command(
        name = "jarbase",
        mixinStandardHelpOptions = true,
        versionProvider = VersionProvider.class,
        description = "Living documentation that keeps up with your code.",
        subcommands = {InstallCommand.class, AffectedCommand.class, ValidateCommand.class, HookCommand.class})
public class JarbaseCommand implements Runnable {

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @Override
    public void run() {
        spec.commandLine().usage(spec.commandLine().getOut());
    }
}
