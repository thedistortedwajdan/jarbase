package dev.wajdan.jarbase.core.git;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** These tests build a real throwaway git repository in a temp folder and ask it questions. */
class GitChangesTest {

    @TempDir
    Path repo;

    @BeforeEach
    void initRepoWithOneCommitOnMain() throws Exception {
        git("init", "-q", "-b", "main");
        git("config", "user.name", "Test");
        git("config", "user.email", "test@example.com");
        git("config", "commit.gpgsign", "false");
        write("README.md", "hello");
        commitAll("initial");
    }

    @Test
    void listsFilesChangedOnTheBranchSinceItSplitFromMain() throws Exception {
        git("checkout", "-q", "-b", "feature");
        write("src/A.java", "class A {}");
        write("README.md", "hello again");
        commitAll("feature work");

        assertThat(GitChanges.changedFiles(repo, "main", false)).containsExactly("README.md", "src/A.java");
    }

    @Test
    void ignoresCommitsThatLandedOnMainAfterTheBranchWasCreated() throws Exception {
        git("checkout", "-q", "-b", "feature");
        write("src/A.java", "class A {}");
        commitAll("feature work");

        git("checkout", "-q", "main");
        write("src/OnlyOnMain.java", "class M {}");
        commitAll("someone else's work");
        git("checkout", "-q", "feature");

        assertThat(GitChanges.changedFiles(repo, "main", false)).containsExactly("src/A.java");
    }

    @Test
    void uncommittedEditsAreIncludedOnlyWhenAsked() throws Exception {
        git("checkout", "-q", "-b", "feature");
        write("README.md", "edited but not committed");
        write("src/New.java", "class New {}");

        assertThat(GitChanges.changedFiles(repo, "main", false)).isEmpty();
        assertThat(GitChanges.changedFiles(repo, "main", true)).containsExactly("README.md", "src/New.java");
    }

    @Test
    void stagedFilesCountAsUncommitted() throws Exception {
        git("checkout", "-q", "-b", "feature");
        write("src/Staged.java", "class Staged {}");
        git("add", "src/Staged.java");

        assertThat(GitChanges.changedFiles(repo, "main", true)).containsExactly("src/Staged.java");
    }

    @Test
    void deletedFilesAreReported() throws Exception {
        git("checkout", "-q", "-b", "feature");
        Files.delete(repo.resolve("README.md"));
        commitAll("remove readme");

        assertThat(GitChanges.changedFiles(repo, "main", false)).containsExactly("README.md");
    }

    @Test
    void renamesListBothOldAndNewPaths() throws Exception {
        git("checkout", "-q", "-b", "feature");
        git("mv", "README.md", "GUIDE.md");
        commitAll("rename");

        assertThat(GitChanges.changedFiles(repo, "main", false)).containsExactly("GUIDE.md", "README.md");
    }

    @Test
    void unusualFileNamesComeBackIntact() throws Exception {
        git("checkout", "-q", "-b", "feature");
        write("src/with space.java", "class X {}");
        commitAll("odd name");

        assertThat(GitChanges.changedFiles(repo, "main", false)).containsExactly("src/with space.java");
    }

    @Test
    void unknownBaseGivesAClearError() {
        assertThatThrownBy(() -> GitChanges.changedFiles(repo, "no-such-branch", false))
                .isInstanceOf(GitException.class)
                .hasMessageContaining("merge-base");
    }

    @Test
    void notAGitRepositoryGivesAClearError(@TempDir Path plainFolder) {
        assertThatThrownBy(() -> GitChanges.changedFiles(plainFolder, "main", false))
                .isInstanceOf(GitException.class);
    }

    // ---- helpers ----

    private void write(String relativePath, String content) throws IOException {
        Path file = repo.resolve(relativePath);
        Files.createDirectories(file.getParent() == null ? repo : file.getParent());
        Files.writeString(file, content);
    }

    private void commitAll(String message) throws Exception {
        git("add", "-A");
        git("commit", "-q", "-m", message);
    }

    private void git(String... args) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command)
                .directory(repo.toFile())
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes());
        if (!process.waitFor(30, TimeUnit.SECONDS) || process.exitValue() != 0) {
            throw new IllegalStateException("git " + String.join(" ", args) + " failed: " + output);
        }
    }
}
