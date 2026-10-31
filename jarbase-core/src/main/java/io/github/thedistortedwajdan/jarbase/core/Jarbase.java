package io.github.thedistortedwajdan.jarbase.core;

/** Entry point constants for jarbase-core. A placeholder that proves the module builds. */
public final class Jarbase {

    public static final String NAME = "jarbase";

    /** Where {@code jarbase install} puts the tool inside a project, and how agents are told to run it. */
    public static final String HOME_FOLDER = ".jarbase";

    public static final String CLI_COMMAND = "sh " + HOME_FOLDER + "/jarbase";

    private Jarbase() {
    }
}
