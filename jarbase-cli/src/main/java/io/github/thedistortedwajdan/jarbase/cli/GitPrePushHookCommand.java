package io.github.thedistortedwajdan.jarbase.cli;

import io.github.thedistortedwajdan.jarbase.core.agent.AgentAdapters;
import io.github.thedistortedwajdan.jarbase.core.config.JarbaseConfig;
import io.github.thedistortedwajdan.jarbase.core.git.GitChanges;
import io.github.thedistortedwajdan.jarbase.core.hook.GitPrePush;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

/**
 * Called by git's {@code pre-push} hook, so it runs for every push: from a terminal, from an IDE or from an agent.
 *
 * <p>Git stops the push when this exits non-zero, so unlike the agent hook, any internal problem here exits 0
 * after printing a note. A bug in jarbase must never be able to stop a push.
 */
@Command(
        name = "git-pre-push",
        mixinStandardHelpOptions = true,
        description = "Checks the docs when git is about to push. Installed as a git pre-push hook.",
        footer = {"", "Set JARBASE_HOOKS=off to switch it off for a session, or push with --no-verify."})
public class GitPrePushHookCommand implements Callable<Integer> {

    @Spec
    CommandSpec spec;

    @Parameters(arity = "0..2", paramLabel = "<remote> <url>", description = "Passed by git. Not used.")
    List<String> gitArguments;

    @Option(names = "--docs", defaultValue = "docs", description = "Docs folder relative to the repository root.")
    String docsFolder;

    /** The process environment. A field so tests can pretend to run outside (or inside) an agent session. */
    Map<String, String> environment = System.getenv();

    @Option(names = "--repo", defaultValue = ".", hidden = true,
            description = "Folder inside the repository to work from. Defaults to the current directory.")
    Path repo;

    @Override
    public Integer call() {
        PrintWriter err = spec.commandLine().getErr();
        try {
            Map<String, String> env = environment;
            if (PrePushHookCommand.isDisabled(env.get("JARBASE_HOOKS"))) {
                return 0;
            }

            String stdin = new String(System.in.readAllBytes(), StandardCharsets.UTF_8);
            Path root = GitChanges.repoRoot(repo);
            JarbaseConfig config = JarbaseConfig.load(root, env);
            boolean insideAgent = AgentAdapters.all().stream().anyMatch(a -> a.isRunningInside(env));

            String remote = gitArguments == null || gitArguments.isEmpty() ? null : gitArguments.get(0);
            GitPrePush.Context context = new GitPrePush.Context(root, docsFolder, config, insideAgent, remote, env,
                    AgentAdapters.all(), message -> {
                        err.println(message);
                        err.flush();
                    });
            GitPrePush.Result result = GitPrePush.run(context, GitPrePush.parse(stdin));
            if (!result.message().isEmpty()) {
                err.println(result.message());
                err.flush();
            }
            return result.block() ? 1 : 0;
        } catch (Exception e) {
            err.println("jarbase hook: skipped (" + e.getMessage() + ")");
            err.flush();
            return 0;
        }
    }
}
