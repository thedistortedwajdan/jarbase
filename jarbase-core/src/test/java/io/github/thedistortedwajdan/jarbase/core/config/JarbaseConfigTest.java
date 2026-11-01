package io.github.thedistortedwajdan.jarbase.core.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JarbaseConfigTest {

    @TempDir
    Path project;

    private void writeConfig(String text) throws IOException {
        Files.createDirectories(project.resolve(".jarbase"));
        Files.writeString(project.resolve(JarbaseConfig.FILE), text);
    }

    @Test
    void withoutAFileTheDefaultsApply() {
        JarbaseConfig config = JarbaseConfig.load(project, Map.of());

        assertThat(config).isEqualTo(JarbaseConfig.defaults());
        assertThat(config.review()).isEqualTo(JarbaseConfig.Review.AUTO);
        assertThat(config.onPush()).isEqualTo(JarbaseConfig.OnPush.FIX);
        assertThat(config.agent()).isNull();
        assertThat(config.timeoutSeconds()).isEqualTo(180);
    }

    @Test
    void theFileOverridesTheDefaults() throws IOException {
        writeConfig("review=required\nonPush=block\nagent=claude-code\ntimeoutSeconds=60\n");

        JarbaseConfig config = JarbaseConfig.load(project, Map.of());

        assertThat(config.review()).isEqualTo(JarbaseConfig.Review.REQUIRED);
        assertThat(config.onPush()).isEqualTo(JarbaseConfig.OnPush.BLOCK);
        assertThat(config.agent()).isEqualTo("claude-code");
        assertThat(config.timeoutSeconds()).isEqualTo(60);
    }

    @Test
    void environmentVariablesBeatTheFile() throws IOException {
        writeConfig("review=required\nonPush=block\nagent=claude-code\n");

        JarbaseConfig config = JarbaseConfig.load(project, Map.of(
                "JARBASE_REVIEW", "auto", "JARBASE_ON_PUSH", "warn", "JARBASE_AGENT", "codex",
                "JARBASE_TIMEOUT_SECONDS", "5"));

        assertThat(config.review()).isEqualTo(JarbaseConfig.Review.AUTO);
        assertThat(config.onPush()).isEqualTo(JarbaseConfig.OnPush.WARN);
        assertThat(config.agent()).isEqualTo("codex");
        assertThat(config.timeoutSeconds()).isEqualTo(5);
    }

    @Test
    void valuesAreCaseInsensitiveAndBlankMeansUnset() throws IOException {
        writeConfig("review=REQUIRED\nonPush=Warn\nagent=\ntimeoutSeconds=\n");

        JarbaseConfig config = JarbaseConfig.load(project, Map.of());

        assertThat(config.review()).isEqualTo(JarbaseConfig.Review.REQUIRED);
        assertThat(config.onPush()).isEqualTo(JarbaseConfig.OnPush.WARN);
        assertThat(config.agent()).isNull();
        assertThat(config.timeoutSeconds()).isEqualTo(180);
    }

    @Test
    void anInvalidValueNamesTheKeyAndTheChoices() throws IOException {
        writeConfig("onPush=sometimes\n");

        assertThatThrownBy(() -> JarbaseConfig.load(project, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("onPush").hasMessageContaining("sometimes")
                .hasMessageContaining("fix, block, warn");
    }

    @Test
    void aBadTimeoutIsRejected() throws IOException {
        writeConfig("timeoutSeconds=soon\n");

        assertThatThrownBy(() -> JarbaseConfig.load(project, Map.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("timeoutSeconds");
    }

    @Test
    void theGeneratedFileSaysWhatTheDefaultsSay() throws IOException {
        writeConfig(JarbaseConfig.defaultFileText());

        assertThat(JarbaseConfig.load(project, Map.of())).isEqualTo(JarbaseConfig.defaults());
    }
}
