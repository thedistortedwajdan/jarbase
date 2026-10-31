package dev.wajdan.jarbase.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;

/** {@code jarbase hook ...}: entry points that coding agents call, not meant to be typed by hand. */
@Command(
        name = "hook",
        mixinStandardHelpOptions = true,
        description = "Entry points for coding-agent hooks.",
        subcommands = {PrePushHookCommand.class})
public class HookCommand implements Runnable {

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @Override
    public void run() {
        spec.commandLine().usage(spec.commandLine().getOut());
    }
}
