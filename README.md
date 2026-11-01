# jarbase

[![Maven Central](https://img.shields.io/maven-central/v/io.github.thedistortedwajdan.jarbase/jarbase-maven-plugin?label=Maven%20Central)](https://central.sonatype.com/artifact/io.github.thedistortedwajdan.jarbase/jarbase-maven-plugin)
[![CI](https://github.com/thedistortedwajdan/jarbase/actions/workflows/ci.yml/badge.svg)](https://github.com/thedistortedwajdan/jarbase/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

**Docs that keep up with your code, for Java and Spring Boot projects.**

Each docs page lists the code it describes. When you push a change to that code without touching the page, jarbase notices, asks your own coding agent to update the page, and pushes the docs together with the code. If no agent is available, your code still goes up and you get a clear warning about which pages are stale.

> **Status: early alpha (0.1.0).** The checks, the hooks, the auto-fix and the Maven plugin are built and tested, and the whole flow has been tried end to end on Windows with [Claude Code](https://claude.com/claude-code): a plain `git push` updated, committed and pushed the docs. Only Claude Code is implemented as an agent; others plug in through a small adapter interface. The docs website part (serving your docs from a Spring Boot app) is not built yet.

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

2. jarbase compares your branch with the default branch and finds the pages whose `sources` match changed files.
3. Three layers act on that, so a forgotten page is caught whoever does the pushing:

| Layer | Who it covers | What it does |
|---|---|---|
| **Instructions** in `AGENTS.md` / `CLAUDE.md` | A coding agent in a chat session | The agent is told to update affected pages before it pushes. |
| **Agent hook** (Claude Code) | A coding agent that runs `git push` | Stops the push once and names the pages to update. |
| **Git `pre-push` hook** | Anyone: terminal, IDE, or agent | Runs your agent in the background to update the pages, commits them and pushes. Warns if it cannot. |
| **CI check** (optional) | Every pull request | Fails the PR if code changed but its pages did not. |

jarbase never holds an API key and never talks to a model itself. The agent you already use writes the docs, with your login, on your machine.

## What happens when you `git push` by hand

With the defaults (`onPush=fix`, `review=auto`):

1. The hook finds pages that describe the code you are pushing and that you did not update.
2. It runs your coding agent once, headlessly, allowed to edit only files under `docs/`.
3. If the pages are updated and the docs still validate, jarbase commits them under your name and pushes the branch itself. You will see git's `failed to push some refs` message afterwards: that is expected, because your original push was replaced by jarbase's, which already included the docs.
4. If anything goes wrong (no agent installed, no network, the agent fails or takes longer than `timeoutSeconds`, or it touches anything outside `docs/`), jarbase undoes the agent's changes and your push **goes through unchanged** with a warning listing the stale pages.

Rules that always hold:

- Only the branch you have checked out is fixed automatically. Other pushes get the warning.
- It tries once per situation. If it fails, pushing again goes straight through.
- The agent's edits are discarded if they make the docs validate worse.
- `git push --no-verify` or `JARBASE_HOOKS=off` skips everything.
- A bug in jarbase never stops a push.

Auto-fix needs a coding agent on your `PATH` (for Claude Code, the `claude` command). Without one you get the warning.

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
| `.jarbase/config.properties` | Team settings (see below). Created once, never overwritten. |
| `.jarbase/jarbase-hook.sh`, `.jarbase/jarbase` | The fast hook gatekeeper and the launcher that runs the tool |
| `.jarbase/version` | The version Maven should provide, so every clone uses the same one |
| `.claude/settings.json` | Registers the agent hook with Claude Code (your other settings are kept) |
| `AGENTS.md`, `CLAUDE.md` | Instructions agents read (jarbase only edits its own marked block) |
| `.git/hooks/pre-push` | The git hook, for pushes by hand. Not committed; each clone gets its own on its first build. |
| `.github/workflows/jarbase-docs-check.yml` | The CI check, only if the project already has a `.github` folder |
| `docs/index.md` | A starter page, only when you have no docs yet |

Commit everything except `.git/hooks`. A teammate who clones the project gets the files from git, and Maven downloads the jar into their local repository the first time they load the project. The jar is never committed.

An existing `pre-push` hook (or `core.hooksPath`, as Husky uses) is never overwritten: jarbase tells you the one line to add to it.

## Settings

`.jarbase/config.properties` is committed, so the team shares it:

```properties
# What a coding agent is told to do about docs.
#   auto      update the docs, commit them with the code and push, without asking
#   required  update the docs, show the diff and wait for approval
review=auto

# What `git push` does when docs describing the pushed code were not updated.
#   fix    run your coding agent to update the docs, commit them and push (a warning if no agent works)
#   block  stop the push once; the next push goes through
#   warn   print a warning and push anyway
onPush=fix

# Which coding agent to use for `fix`. Empty means the first one found on this machine.
agent=

# How long the agent may take, in seconds, before jarbase gives up and only warns.
timeoutSeconds=180
```

Any setting can be overridden on one machine with an environment variable: `JARBASE_REVIEW`, `JARBASE_ON_PUSH`, `JARBASE_AGENT`, `JARBASE_TIMEOUT_SECONDS`. With `review=required`, the agent hook and the instructions ask the agent to show you the docs diff first, and the git hook leaves the agent's edits uncommitted for you to review instead of pushing them.

## Commands

Run through the launcher, for example `sh .jarbase/jarbase affected`:

| Command | What it does |
|---|---|
| `affected [--base main] [--uncommitted] [--format json] [--fail-on-stale]` | Lists docs pages that describe code you changed, and whether each was updated too |
| `validate [--strict] [--format json]` | Checks titles, readable frontmatter, `sources` patterns that match files, and working links |
| `install [--agent claude-code]` | Sets a project up without Maven (copies the jar into `.jarbase/`) |
| `hook pre-push`, `hook git-pre-push` | Entry points for the agent hook and the git hook; not meant to be run by hand |

The same checks are Maven goals, which is what the CI workflow uses:

```bash
mvn io.github.thedistortedwajdan.jarbase:jarbase-maven-plugin:validate
mvn io.github.thedistortedwajdan.jarbase:jarbase-maven-plugin:affected -Djarbase.base=origin/main -Djarbase.failOnStale=true
```

## `sources` patterns

`*` matches within one folder, `**` across folders, `?` one character, `{a,b}` alternatives, and a trailing `/` means everything beneath a folder. Matching is the same on Windows and Linux.

## Requirements

- Java 21 or newer
- Git on your `PATH`
- `sh` for the hook scripts (Git Bash on Windows, which Claude Code already uses)
- For auto-fix: a coding agent CLI on your `PATH` (Claude Code's `claude`)

## Project layout

| Module | Role |
|---|---|
| `jarbase-core` | The logic. Plain Java, no Spring |
| `jarbase-cli` | The command-line tool (one runnable jar) |
| `jarbase-maven-plugin` | `install`, `validate` and `affected` goals |

Agent-specific behaviour lives behind one interface, `AgentAdapter` (reading the agent's hook input, answering it, installing its settings, and running it headlessly), so supporting another agent means adding one class and one line in `META-INF/services`.

## Building

```bash
./mvnw verify
```

Maintainers: see [RELEASING.md](RELEASING.md) for publishing to Maven Central.

## Credits

jarbase is inspired by [LaRecipe](https://github.com/saleem-hadad/larecipe) 3, which does this for Laravel apps. jarbase is an independent project for the Java ecosystem and shares no code with it.

## License

[MIT](LICENSE)
