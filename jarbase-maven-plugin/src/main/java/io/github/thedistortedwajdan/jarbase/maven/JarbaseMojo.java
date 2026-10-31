package io.github.thedistortedwajdan.jarbase.maven;

import java.io.File;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.Parameter;

/** What every jarbase goal shares. */
abstract class JarbaseMojo extends AbstractMojo {

    /** The project being built. */
    @Parameter(defaultValue = "${project.basedir}", readonly = true, required = true)
    File basedir;

    /** The docs folder, relative to the repository root. */
    @Parameter(property = "jarbase.docs", defaultValue = "docs")
    String docs;

    /** Skip this goal entirely. */
    @Parameter(property = "jarbase.skip", defaultValue = "false")
    boolean skip;
}
