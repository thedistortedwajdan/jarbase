package io.github.thedistortedwajdan.jarbase.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JarbaseTest {

    @Test
    void nameIsJarbase() {
        assertThat(Jarbase.NAME).isEqualTo("jarbase");
    }
}
