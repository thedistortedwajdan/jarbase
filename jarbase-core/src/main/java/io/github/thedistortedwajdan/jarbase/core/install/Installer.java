package io.github.thedistortedwajdan.jarbase.core.install;

import io.github.thedistortedwajdan.jarbase.core.Jarbase;
import io.github.thedistortedwajdan.jarbase.core.agent.AgentAdapter;
import io.github.thedistortedwajdan.jarbase.core.config.JarbaseConfig;
import io.github.thedistortedwajdan.jarbase.core.content.DocsLoader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Sets a project up for jarbase. Safe to run again: it only changes what is out of date. */
public final class Installer {

    private static final String HOOK_SCRIPT = Jarbase.HOME_FOLDER + "/jarbase-hook.sh";
    private static final String LAUNCHER = Jarbase.HOME_FOLDER + "/jarbase";
    private static final String VERSION_FILE = Jarbase.HOME_FOLDER + "/version";
    private static final String HOME_GITIGNORE = Jarbase.HOME_FOLDER + "/.gitignore";
    private static final String HOME_GITATTRIBUTES = Jarbase.HOME_FOLDER + "/.gitattributes";
    private static final String JAR = Jarbase.HOME_FOLDER + "/jarbase.jar";

    private Installer() {
    }

    /**
     * @param root       the repository's top folder
     * @param docsFolder docs folder relative to {@code root}
     * @param adapters   the coding agents to set up
     * @param assets     files that come from the command-line tool
     */
    public static List<Change> install(Path root, String docsFolder, List<AgentAdapter> adapters, InstallAssets assets) {
        List<Change> changes = new ArrayList<>();

        // 1. the tool's scripts, inside the project (committed, so every clone has them)
        changes.add(ProjectFiles.write(root, HOOK_SCRIPT, assets.hookScript()));
        changes.add(ProjectFiles.write(root, LAUNCHER, assets.launcherScript()));
        // the scripts must keep Unix line endings even when git is set to convert them on Windows
        changes.add(ProjectFiles.write(root, HOME_GITATTRIBUTES, "* text eol=lf\n"));
        // the jar is a build artifact: kept out of git so we never commit a binary by accident
        changes.add(ProjectFiles.write(root, HOME_GITIGNORE, "jarbase.jar\n"));
        if (assets.version() != null) {
            changes.add(ProjectFiles.write(root, VERSION_FILE, assets.version() + "\n"));
        } else {
            changes.add(copyJar(root, assets.jarSource()));
        }

        // 2. the team's settings (created once, never overwritten, so edits survive)
        changes.add(ProjectFiles.createIfAbsent(root, JarbaseConfig.FILE, JarbaseConfig.defaultFileText()));
        JarbaseConfig config = JarbaseConfig.load(root, Map.of());

        // 3. the instructions every agent reads
        String agentsMd = ProjectFiles.read(root, "AGENTS.md").orElse("");
        String updated = MarkedBlock.apply(agentsMd, AgentInstructions.block(docsFolder, config.review()));
        changes.add(ProjectFiles.write(root, "AGENTS.md", updated));

        // 4. each agent's own hook and files
        for (AgentAdapter adapter : adapters) {
            changes.addAll(adapter.install(root));
        }

        // 5. the git hook, so the check also runs when a person pushes by hand
        changes.add(GitHook.install(root));

        // 6. the strict backstop for pull requests, only for projects that already use GitHub Actions
        changes.add(CiWorkflow.install(root, docsFolder, assets.version()));

        // 7. a first page, only when the project has no docs yet
        changes.add(starterDocs(root, docsFolder));
        return changes;
    }

    private static Change copyJar(Path root, Path source) {
        if (source == null || !Files.isRegularFile(source)) {
            return new Change(Change.Kind.SKIPPED, JAR,
                    "not running from a built jar; build with ./mvnw package and run the -all.jar to install it");
        }
        Path target = root.resolve(JAR);
        try {
            Files.createDirectories(target.getParent());
            if (Files.isRegularFile(target) && Files.mismatch(source, target) == -1) {
                return Change.of(Change.Kind.UNCHANGED, JAR);
            }
            boolean existed = Files.exists(target);
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            return Change.of(existed ? Change.Kind.UPDATED : Change.Kind.CREATED, JAR);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not copy the jarbase jar to " + target, e);
        }
    }

    private static Change starterDocs(Path root, String docsFolder) {
        String page = docsFolder + "/index.md";
        if (!DocsLoader.load(root, docsFolder).isEmpty()) {
            return new Change(Change.Kind.SKIPPED, page, "the project already has docs");
        }
        return ProjectFiles.write(root, page, """
                ---
                title: Introduction
                description: What this project is and where to start.
                ---
                # Introduction

                Describe what this project does and who it is for.

                <!-- Each page should list the code it describes, so jarbase can tell when the page is stale:

                ---
                title: Billing
                sources: ["src/main/java/com/example/billing/**"]
                ---
                -->
                """);
    }
}
