package io.github.thedistortedwajdan.jarbase.maven;

import io.github.thedistortedwajdan.jarbase.core.agent.AgentAdapter;
import io.github.thedistortedwajdan.jarbase.core.agent.AgentAdapters;
import io.github.thedistortedwajdan.jarbase.core.git.GitChanges;
import io.github.thedistortedwajdan.jarbase.core.git.GitException;
import io.github.thedistortedwajdan.jarbase.core.install.Change;
import io.github.thedistortedwajdan.jarbase.core.install.InstallAssets;
import io.github.thedistortedwajdan.jarbase.core.install.Installer;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Sets the project up for jarbase: the push hook, the agent instructions and a starter docs page.
 *
 * <p>Bound to the {@code initialize} phase, so it runs on any Maven build. It only changes files that are
 * out of date, so a project that is already set up is left untouched. The jar itself is not copied into the
 * project: the plugin's version is written to {@code .jarbase/version} and Maven's own cache provides the jar.
 */
@Mojo(name = "install", defaultPhase = LifecyclePhase.INITIALIZE, threadSafe = true)
public class InstallMojo extends JarbaseMojo {

    /** The version of this plugin, which is also the version of the jar the hook will run. */
    @Parameter(defaultValue = "${plugin.version}", readonly = true)
    String pluginVersion;

    /** Where Maven was started. In a multi-module build only this module does the work. */
    @Parameter(defaultValue = "${session.executionRootDirectory}", readonly = true)
    String executionRoot;

    /** Coding agents to set up. */
    @Parameter(property = "jarbase.agents", defaultValue = "claude-code")
    List<String> agents;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("jarbase: skipped (jarbase.skip is set)");
            return;
        }
        if (executionRoot != null && !sameFolder(new File(executionRoot), basedir)) {
            return; // another module of the same build does this once for the whole repository
        }

        List<AgentAdapter> adapters = new ArrayList<>();
        for (String id : agents) {
            adapters.add(AgentAdapters.find(id).orElseThrow(() -> new MojoFailureException(
                    "Unknown agent '" + id + "'. Available: " + String.join(", ", AgentAdapters.ids()))));
        }

        Path root;
        try {
            root = GitChanges.repoRoot(basedir.toPath());
        } catch (GitException e) {
            getLog().warn("jarbase: not a git repository, nothing to install (" + e.getMessage() + ")");
            return;
        }

        List<Change> changes;
        try {
            changes = Installer.install(root, docs, adapters, InstallAssets.bundled(null, pluginVersion));
        } catch (RuntimeException e) {
            throw new MojoExecutionException("jarbase install failed: " + e.getMessage(), e);
        }

        long modified = changes.stream()
                .filter(c -> c.kind() == Change.Kind.CREATED || c.kind() == Change.Kind.UPDATED).count();
        if (modified == 0) {
            getLog().debug("jarbase: already set up");
            return;
        }
        getLog().info("jarbase: set up " + root + " (" + modified + " files)");
        for (Change change : changes) {
            if (change.kind() == Change.Kind.CREATED || change.kind() == Change.Kind.UPDATED) {
                getLog().info("  " + change.kind().name().toLowerCase() + " " + change.path());
            }
        }
        getLog().info("jarbase: commit these files so your teammates get the same protection.");
    }

    private static boolean sameFolder(File a, File b) {
        return a.toPath().toAbsolutePath().normalize().equals(b.toPath().toAbsolutePath().normalize());
    }
}
