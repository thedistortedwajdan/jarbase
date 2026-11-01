package io.github.thedistortedwajdan.jarbase.core.agent;

import java.util.List;

/**
 * How to run a coding agent once, with no chat session and no human at the keyboard.
 *
 * <p>The instruction is sent on the program's standard input rather than as an argument. A long, multi-line
 * instruction with quotes in it cannot be passed safely as an argument on every platform (Windows
 * {@code .cmd} launchers are the problem), but standard input has no such limits.
 *
 * @param program the program to look for on the {@code PATH}, for example {@code claude}
 * @param args    its arguments
 * @param stdin   the text to send to it on standard input (the instruction)
 */
public record HeadlessCommand(String program, List<String> args, String stdin) {

    public HeadlessCommand {
        args = List.copyOf(args);
    }
}
