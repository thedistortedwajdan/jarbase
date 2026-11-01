package io.github.thedistortedwajdan.jarbase.core.install;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CiWorkflowTest {

    @TempDir
    Path project;

    private String workflow() throws IOException {
        return Files.readString(project.resolve(".github/workflows/jarbase-docs-check.yml"));
    }

    @Test
    void noGithubFolderMeansNoWorkflow() {
        Change change = CiWorkflow.install(project, "docs", "0.1.0");

        assertThat(change.kind()).isEqualTo(Change.Kind.SKIPPED);
        assertThat(project.resolve(".github")).doesNotExist();
    }

    @Test
    void aGithubFolderGetsTheWorkflow() throws IOException {
        Files.createDirectories(project.resolve(".github"));

        Change change = CiWorkflow.install(project, "docs", "0.1.0");

        assertThat(change.kind()).isEqualTo(Change.Kind.CREATED);
        assertThat(workflow())
                .contains("pull_request")
                .contains("fetch-depth: 0")
                .contains("skip-docs")
                .contains("mvn --batch-mode --no-transfer-progress io.github.thedistortedwajdan.jarbase:jarbase-maven-plugin:0.1.0:validate")
                .contains("jarbase-maven-plugin:0.1.0:affected -Djarbase.base=origin/${{ github.base_ref }} -Djarbase.failOnStale=true")
                .doesNotContain("-Djarbase.docs");
    }

    @Test
    void theMavenWrapperIsUsedWhenTheProjectHasOne() throws IOException {
        Files.createDirectories(project.resolve(".github"));
        Files.writeString(project.resolve("mvnw"), "#!/bin/sh\n");

        CiWorkflow.install(project, "docs", "0.1.0");

        assertThat(workflow()).contains("run: ./mvnw --batch-mode");
    }

    @Test
    void withoutAPinnedVersionMavenPicksTheNewestRelease() throws IOException {
        Files.createDirectories(project.resolve(".github"));

        CiWorkflow.install(project, "docs", null);

        assertThat(workflow()).contains("io.github.thedistortedwajdan.jarbase:jarbase-maven-plugin:validate");
    }

    @Test
    void aDifferentDocsFolderIsPassedOn() throws IOException {
        Files.createDirectories(project.resolve(".github"));

        CiWorkflow.install(project, "documentation", "0.1.0");

        assertThat(workflow()).contains("-Djarbase.docs=documentation");
    }

    @Test
    void anExistingWorkflowIsNeverOverwritten() throws IOException {
        Files.createDirectories(project.resolve(".github/workflows"));
        Files.writeString(project.resolve(".github/workflows/jarbase-docs-check.yml"), "name: my own edits\n");

        Change change = CiWorkflow.install(project, "docs", "0.1.0");

        assertThat(change.kind()).isEqualTo(Change.Kind.UNCHANGED);
        assertThat(workflow()).isEqualTo("name: my own edits\n");
    }

    @Test
    void theInstallerAddsItAlongsideEverythingElse() throws IOException {
        Files.createDirectories(project.resolve(".github"));
        InstallAssets assets = new InstallAssets("h\n", "l\n", null, "0.1.0");

        var changes = Installer.install(project, "docs", java.util.List.of(), assets);

        assertThat(changes).anyMatch(c -> c.path().equals(".github/workflows/jarbase-docs-check.yml")
                && c.kind() == Change.Kind.CREATED);
    }
}
