package io.github.thedistortedwajdan.jarbase.core.install;

import io.github.thedistortedwajdan.jarbase.core.Jarbase;
import io.github.thedistortedwajdan.jarbase.core.config.JarbaseConfig;

/**
 * The plain-text instructions written into {@code AGENTS.md}. Most coding agents read that file at the
 * start of every session, so this is how an agent learns the rule before it ever tries to push.
 */
public final class AgentInstructions {

    private AgentInstructions() {
    }

    public static String block(String docsFolder, JarbaseConfig.Review review) {
        String approval = review == JarbaseConfig.Review.AUTO
                ? """
                You do not need to ask the user to approve the docs change first: update the pages, commit them
                together with the code and push. Say in your summary which pages you updated.
                """
                : """
                Show the user the docs diff and wait for approval before committing it.
                """;
        return MarkedBlock.wrap("""
                ## Documentation (jarbase)

                This project's docs live in `%1$s/`. Docs ship in the same push as the code they describe.
                Each page lists the code it describes in its `sources:` frontmatter.

                Before you push a branch or open a pull request:

                1. Run `%2$s affected --uncommitted` to list the docs pages your change affects.
                2. Update each affected page. Keep unrelated text untouched.
                3. Commit the docs together with the code and push.

                %3$s
                If a change needs no docs update, say why and push again: a push is only blocked once.
                Never write secrets or configuration values into the docs.
                """.formatted(docsFolder, Jarbase.CLI_COMMAND, approval.strip()));
    }
}
