package io.github.thedistortedwajdan.jarbase.core.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SourceGlobTest {

    private static boolean matches(String glob, String file) {
        return SourceGlob.of(glob).matches(file);
    }

    @Test
    void doubleStarMatchesAcrossFolders() {
        assertThat(matches("src/main/java/com/acme/billing/**", "src/main/java/com/acme/billing/Invoice.java")).isTrue();
        assertThat(matches("src/main/java/com/acme/billing/**", "src/main/java/com/acme/billing/web/Api.java")).isTrue();
        assertThat(matches("src/main/java/com/acme/billing/**", "src/main/java/com/acme/users/User.java")).isFalse();
    }

    @Test
    void singleStarStaysWithinOneFolder() {
        assertThat(matches("src/*.java", "src/A.java")).isTrue();
        assertThat(matches("src/*.java", "src/sub/A.java")).isFalse();
    }

    @Test
    void leadingDoubleStarSlashAlsoMatchesTheRoot() {
        assertThat(matches("**/Foo.java", "Foo.java")).isTrue();
        assertThat(matches("**/Foo.java", "a/b/Foo.java")).isTrue();
        assertThat(matches("**/Foo.java", "a/b/Bar.java")).isFalse();
    }

    @Test
    void middleDoubleStarMatchesZeroOrMoreFolders() {
        assertThat(matches("src/**/Foo.java", "src/Foo.java")).isTrue();
        assertThat(matches("src/**/Foo.java", "src/a/b/Foo.java")).isTrue();
    }

    @Test
    void questionMarkMatchesOneCharacter() {
        assertThat(matches("a?.txt", "ab.txt")).isTrue();
        assertThat(matches("a?.txt", "abc.txt")).isFalse();
        assertThat(matches("a?.txt", "a/.txt")).isFalse();
    }

    @Test
    void bracesMatchAlternatives() {
        assertThat(matches("config/*.{yml,yaml}", "config/app.yml")).isTrue();
        assertThat(matches("config/*.{yml,yaml}", "config/app.yaml")).isTrue();
        assertThat(matches("config/*.{yml,yaml}", "config/app.json")).isFalse();
    }

    @Test
    void exactFileNameMatchesOnlyThatFile() {
        assertThat(matches("pom.xml", "pom.xml")).isTrue();
        assertThat(matches("pom.xml", "sub/pom.xml")).isFalse();
        assertThat(matches("pom.xml", "pomxxml")).isFalse();
    }

    @Test
    void trailingSlashMeansEverythingBeneath() {
        assertThat(matches("src/main/resources/", "src/main/resources/application.yml")).isTrue();
        assertThat(matches("src/main/resources/", "src/main/resources/db/V1.sql")).isTrue();
        assertThat(matches("src/main/resources/", "src/main/java/A.java")).isFalse();
    }

    @Test
    void anchorSuffixIsIgnoredForMatching() {
        assertThat(matches("src/main/java/Billing.java#refund", "src/main/java/Billing.java")).isTrue();
    }

    @Test
    void windowsPathsAndDotSlashAreNormalized() {
        assertThat(matches("src/main/**", "src\\main\\A.java")).isTrue();
        assertThat(matches("./src/main/**", "src/main/A.java")).isTrue();
        assertThat(matches("src/main/**", "./src/main/A.java")).isTrue();
    }

    @Test
    void regexCharactersInNamesAreTakenLiterally() {
        assertThat(matches("a+b(1).txt", "a+b(1).txt")).isTrue();
        assertThat(matches("a.b", "axb")).isFalse();
    }

    @Test
    void emptyPatternIsRejected() {
        assertThatThrownBy(() -> SourceGlob.of("  ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SourceGlob.of("#only-anchor")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unclosedBraceIsRejected() {
        assertThatThrownBy(() -> SourceGlob.of("a/{b,c")).isInstanceOf(IllegalArgumentException.class);
    }
}
