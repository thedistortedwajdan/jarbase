package io.github.thedistortedwajdan.jarbase.cli;

import io.github.thedistortedwajdan.jarbase.core.agent.AgentAdapter;
import io.github.thedistortedwajdan.jarbase.core.agent.AgentAdapters;
import io.github.thedistortedwajdan.jarbase.core.git.GitChanges;
import io.github.thedistortedwajdan.jarbase.core.install.Change;
import io.github.thedistortedwajdan.jarbase.core.install.InstallAssets;
import io.github.thedistortedwajdan.jarbase.core.install.Installer;
import java.io.PrintWriter;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

@Command(
        name = "install",
        mixinStandardHelpOptions = true,
        description = "Sets up the current project: the push hook, the agent instructions and a starter docs page.",
        footer = {"", "Safe to run again: it only changes what is out of date."})
public class InstallCommand implements Callable<Integer> {

    @Spec
    CommandSpec spec;

    @Option(names = "--agent", split = ",", defaultValue = "claude-code",
            description = "Coding agent(s) to set up, comma separated. Default: ${DEFAULT-VALUE}.")
    List<String> agents;

    @Option(names = "--docs", defaultValue = "docs", description = "Docs folder relative to the repository root.")
    String docsFolder;

    @Option(names = "--repo", defaultValue = ".", hidden = true,
            description = "Folder inside the repository to work from. Defaults to the current directory.")
    Path repo;

    @Override
    public Integer call() {
        List<AgentAdapter> adapters = new ArrayList<>();
        for (String id : agents) {
            adapters.add(AgentAdapters.find(id).orElseThrow(() -> new IllegalArgumentException(
                    "unknown agent '" + id + "'; available: " + String.join(", ", AgentAdapters.ids()))));
        }

        Path root = GitChanges.repoRoot(repo);
        List<Change> changes = Installer.install(root, docsFolder, adapters, InstallAssets.bundled(ownJar(), null));

        PrintWriter out = spec.commandLine().getOut();
        out.println("jarbase installed in " + root);
        for (Change change : changes) {
            out.printf("  %-9s %s%s%n", change.kind().name().toLowerCase(), change.path(),
                    change.note().isEmpty() ? "" : "  (" + change.note() + ")");
        }
        out.println();
        out.println("Next: commit these files. Docs pages list the code they describe in a `sources:` field,");
        out.println("and your coding agent will be asked to update them before it pushes.");
        out.flush();
        return 0;
    }

    /** The jar this program is running from, or null when running from compiled classes. */
    private static Path ownJar() {
        try {
            Path location = Path.of(InstallCommand.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            return location.toString().endsWith(".jar") ? location : null;
        } catch (URISyntaxException | RuntimeException e) {
            return null;
        }
    }
}
