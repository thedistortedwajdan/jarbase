package io.github.thedistortedwajdan.jarbase.maven;

import io.github.thedistortedwajdan.jarbase.core.sync.AffectedReport;
import io.github.thedistortedwajdan.jarbase.core.sync.AffectedService;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Lists the docs pages that describe code changed since {@code base}. Meant for CI:
 * {@code mvn io.github.thedistortedwajdan.jarbase:jarbase-maven-plugin:affected -Djarbase.failOnStale=true}.
 */
@Mojo(name = "affected", threadSafe = true)
public class AffectedMojo extends JarbaseMojo {

    /** Branch, tag or commit to compare against. */
    @Parameter(property = "jarbase.base", defaultValue = "origin/main")
    String base;

    /** Also count edits that are not committed yet. */
    @Parameter(property = "jarbase.uncommitted", defaultValue = "false")
    boolean uncommitted;

    /** Fail the build when an affected page was not updated alongside the code. */
    @Parameter(property = "jarbase.failOnStale", defaultValue = "false")
    boolean failOnStale;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            return;
        }
        AffectedReport report;
        try {
            report = AffectedService.run(basedir.toPath(), docs, base, uncommitted);
        } catch (RuntimeException e) {
            throw new MojoExecutionException("jarbase affected could not run: " + e.getMessage(), e);
        }
        getLog().info("\n" + report.text().stripTrailing());
        if (failOnStale && report.hasStalePages()) {
            throw new MojoFailureException(report.staleCount() + " docs page(s) describe changed code but were not updated.");
        }
    }
}
