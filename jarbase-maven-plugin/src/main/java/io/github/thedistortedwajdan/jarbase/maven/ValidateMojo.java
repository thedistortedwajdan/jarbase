package io.github.thedistortedwajdan.jarbase.maven;

import io.github.thedistortedwajdan.jarbase.core.validation.DocsValidator;
import io.github.thedistortedwajdan.jarbase.core.validation.ValidationReport;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/** Checks the docs and fails the build on errors. Not bound to a phase; run it with {@code jarbase:validate}. */
@Mojo(name = "validate", threadSafe = true)
public class ValidateMojo extends JarbaseMojo {

    /** Treat warnings as errors. */
    @Parameter(property = "jarbase.strict", defaultValue = "false")
    boolean strict;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            return;
        }
        ValidationReport report;
        try {
            report = DocsValidator.validate(basedir.toPath(), docs);
        } catch (RuntimeException e) {
            throw new MojoExecutionException("jarbase validate could not run: " + e.getMessage(), e);
        }
        getLog().info("\n" + report.text().stripTrailing());
        if (report.hasErrors() || (strict && report.warningCount() > 0)) {
            throw new MojoFailureException("The docs have problems; see the report above.");
        }
    }
}
