# jarbase

**Docs that keep up with your code, for Java and Spring Boot projects.**

jarbase checks that your documentation changes together with the code it describes. Each docs page lists the code it covers. When someone (or a coding agent) is about to push a change that touches that code without updating the page, jarbase stops the push once and names the pages to update.

> **Status: early alpha (0.1.0).** The push check, the `affected` and `validate` commands and the Maven plugin work and are tested. It is not on Maven Central yet, and the docs website part (serving your docs from a Spring Boot app) is not built yet. [Claude Code](https://claude.com/claude-code) is supported first; other agents plug in through a small adapter interface.

## How it works

1. Docs are Markdown files in `docs/`. Each page says which code it describes:

   ```markdown
   ---
   title: Billing
   sources: ["src/main/java/com/acme/billing/**"]
   ---
   # Billing
   How invoices work...
   ```

2. `jarbase affected` compares your branch with `main` and lists the pages whose `sources` match changed files.
3. A hook for your coding agent runs that check just before `git push` (or `gh pr create`). If a page is stale it blocks the push **once** and tells the agent which pages to update. The agent updates them, you review the docs diff together with the code, and the next push goes through.
4. `AGENTS.md` and `CLAUDE.md` carry the same rule, so the agent knows it before it ever tries to push.

jarbase never calls an AI itself. The check is deterministic and offline; the agent you already use writes the docs.

## Quick start (Maven)

Add the plugin to your `pom.xml`:

```xml
<build>
  <plugins>
    <plugin>
      <groupId>io.github.thedistortedwajdan.jarbase</groupId>
      <artifactId>jarbase-maven-plugin</artifactId>
      <version>0.1.0</version>
      <executions>
        <execution>
          <goals><goal>install</goal></goals>
        </execution>
      </executions>
    </plugin>
  </plugins>
</build>
```

Run any Maven build once (for example `mvn clean package`). The `install` goal creates, only where out of date:

| File | Purpose |
|---|---|
| `.jarbase/jarbase-hook.sh`, `.jarbase/jarbase` | The fast hook gatekeeper and the launcher that runs the tool |
| `.jarbase/version` | The version Maven should provide, so every clone uses the same one |
| `.claude/settings.json` | Registers the hook with Claude Code (your other settings are kept) |
| `AGENTS.md`, `CLAUDE.md` | The instructions agents read (jarbase only edits its own marked block) |
| `docs/index.md` | A starter page, only when you have no docs yet |

Commit those files. Teammates who clone the project get the same protection once Maven has downloaded the plugin: the jar lives in their local Maven repository and is never committed.

## Commands

Run through the launcher, for example `sh .jarbase/jarbase affected`:

| Command | What it does |
|---|---|
| `affected [--base main] [--uncommitted] [--format json] [--fail-on-stale]` | Lists docs pages that describe code you changed, and whether each was updated too |
| `validate [--strict] [--format json]` | Checks titles, readable frontmatter, `sources` patterns that match files, and working links |
| `install [--agent claude-code]` | Sets a project up without Maven (copies the jar into `.jarbase/`) |
| `hook pre-push` | The entry point agents call; not meant to be run by hand |

The same checks are Maven goals for CI:

```bash
mvn io.github.thedistortedwajdan.jarbase:jarbase-maven-plugin:validate
mvn io.github.thedistortedwajdan.jarbase:jarbase-maven-plugin:affected -Djarbase.base=origin/main -Djarbase.failOnStale=true
```

Set `JARBASE_HOOKS=off` to switch the hook off for a session. The hook never blocks the same situation twice, and a problem inside jarbase can never stop a push.

## `sources` patterns

`*` matches within one folder, `**` across folders, `?` one character, `{a,b}` alternatives, and a trailing `/` means everything beneath a folder. Matching is the same on Windows and Linux.

## Requirements

- Java 21 or newer
- Git on your `PATH`
- `sh` for the hook scripts (Git Bash on Windows, which Claude Code already uses)

## Project layout

| Module | Role |
|---|---|
| `jarbase-core` | The logic. Plain Java, no Spring |
| `jarbase-cli` | The command-line tool (one runnable jar) |
| `jarbase-maven-plugin` | `install`, `validate` and `affected` goals |

Agent-specific behaviour lives behind one interface, `AgentAdapter`, so supporting another agent means adding one class and one line in `META-INF/services`.

## Building

```bash
./mvnw verify
```

Maintainers: see [RELEASING.md](RELEASING.md) for publishing to Maven Central.

## Credits

jarbase is inspired by [LaRecipe](https://github.com/saleem-hadad/larecipe) 3, which does this for Laravel apps. jarbase is an independent project for the Java ecosystem and shares no code with it.

## License

[MIT](LICENSE)
