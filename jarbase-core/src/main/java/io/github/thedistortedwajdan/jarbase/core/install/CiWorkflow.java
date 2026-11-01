package io.github.thedistortedwajdan.jarbase.core.install;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * An optional GitHub Actions workflow: the strict backstop. On every pull request it fails when docs have
 * problems, or when code changed and the docs pages describing it did not. It only checks; it never writes.
 * It is created only when the project already uses GitHub Actions (has a {@code .github} folder).
 */
public final class CiWorkflow {

    static final String FILE = ".github/workflows/jarbase-docs-check.yml";

    private static final String PLUGIN = "io.github.thedistortedwajdan.jarbase:jarbase-maven-plugin";

    private CiWorkflow() {
    }

    /**
     * @param version the plugin version to pin, or null to let Maven pick the newest release
     */
    public static Change install(Path root, String docsFolder, String version) {
        if (!Files.isDirectory(root.resolve(".github"))) {
            return new Change(Change.Kind.SKIPPED, FILE, "no .github folder, so no GitHub Actions workflow was added");
        }
        return ProjectFiles.createIfAbsent(root, FILE, render(docsFolder, version, Files.exists(root.resolve("mvnw"))));
    }

    static String render(String docsFolder, String version, boolean useWrapper) {
        String mvn = useWrapper ? "./mvnw" : "mvn";
        String plugin = PLUGIN + (version == null ? "" : ":" + version);
        String docs = docsFolder.equals("docs") ? "" : " -Djarbase.docs=" + docsFolder;
        return """
                name: Docs check

                # Fails a pull request when the docs have problems, or when code changed but the docs pages that
                # describe it did not. It only checks; it never changes anything.
                # To let one pull request through, add the label "skip-docs" to it.
                on:
                  pull_request:
                    types: [opened, synchronize, reopened, labeled, unlabeled]

                permissions:
                  contents: read

                jobs:
                  docs-check:
                    if: ${{ !contains(github.event.pull_request.labels.*.name, 'skip-docs') }}
                    runs-on: ubuntu-latest
                    steps:
                      - uses: actions/checkout@v4
                        with:
                          fetch-depth: 0   # the check compares your branch with the base branch

                      - uses: actions/setup-java@v4
                        with:
                          distribution: temurin
                          java-version: 21
                          cache: maven

                      - name: Check the docs
                        run: %1$s --batch-mode --no-transfer-progress %2$s:validate%3$s

                      - name: Check the docs were updated with the code
                        run: %1$s --batch-mode --no-transfer-progress %2$s:affected -Djarbase.base=origin/${{ github.base_ref }} -Djarbase.failOnStale=true%3$s
                """.formatted(mvn, plugin, docs);
    }
}
