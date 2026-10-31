package dev.wajdan.jarbase.core.hook;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PushDetectorTest {

    @ParameterizedTest
    @ValueSource(strings = {
        "git push",
        "git push origin main",
        "git push --force-with-lease",
        "  git   push  ",
        "git -C app push",
        "git -c user.name=x push origin HEAD",
        "git --no-pager push",
        "/usr/bin/git push",
        "C:\\Git\\cmd\\git.exe push",
        "GIT_SSH_COMMAND=ssh git push",
        "sudo git push",
        "mvn test && git push",
        "mvn test; git push origin main",
        "git add -A && git commit -m x && git push",
        "false || git push",
        "echo done | git push",
        "(cd app && git push)",
        "gh pr create --title x",
        "gh pr create",
        "glab mr create",
        "hub pull-request -m hi",
        "git commit -m x\ngit push"
    })
    void detectsPushes(String command) {
        assertThat(PushDetector.isPush(command)).as(command).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "git status",
        "git pull",
        "git commit -m 'push it'",
        "git log --oneline",
        "git stash push",
        "echo git push",
        "gh pr view 12",
        "gh pr list",
        "gh issue create",
        "glab mr list",
        "mvn verify",
        "ls -la",
        "pushd /tmp",
        ""
    })
    void ignoresEverythingElse(String command) {
        assertThat(PushDetector.isPush(command)).as(command).isFalse();
    }

    @org.junit.jupiter.api.Test
    void nullIsNotAPush() {
        assertThat(PushDetector.isPush(null)).isFalse();
    }
}
