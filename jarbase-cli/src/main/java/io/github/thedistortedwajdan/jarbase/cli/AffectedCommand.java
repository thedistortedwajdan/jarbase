package io.github.thedistortedwajdan.jarbase.cli;

import io.github.thedistortedwajdan.jarbase.core.sync.AffectedReport;
import io.github.thedistortedwajdan.jarbase.core.sync.AffectedService;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

@Command(
        name = "affected",
        mixinStandardHelpOptions = true,
        description = "Lists the docs pages that describe code you changed.",
        footer = {
            "",
            "Exit codes: 0 = done, 1 = stale pages found (only with --fail-on-stale), 2 = error."
        })
public class AffectedCommand implements Callable<Integer> {

    enum Format { text, json }

    @Spec
    CommandSpec spec;

    @Option(names = "--base", defaultValue = "main", showDefaultValue = picocli.CommandLine.Help.Visibility.ALWAYS,
            description = "Branch, tag or commit to compare against, for example main or origin/main.")
    String base;

    @Option(names = "--uncommitted",
            description = "Also count edits that are not committed yet (staged, unstaged and new files).")
    boolean uncommitted;

    @Option(names = "--docs", defaultValue = "docs", showDefaultValue = picocli.CommandLine.Help.Visibility.ALWAYS,
            description = "Docs folder, relative to the repository root.")
    String docsFolder;

    @Option(names = "--format", defaultValue = "text", showDefaultValue = picocli.CommandLine.Help.Visibility.ALWAYS,
            description = "Output format: ${COMPLETION-CANDIDATES}.")
    Format format;

    @Option(names = "--fail-on-stale",
            description = "Exit with code 1 when an affected page was not updated alongside the code.")
    boolean failOnStale;

    @Option(names = "--repo", defaultValue = ".", hidden = true,
            description = "Folder inside the repository to work from. Defaults to the current directory.")
    Path repo;

    @Override
    public Integer call() {
        AffectedReport report = AffectedService.run(repo, docsFolder, base, uncommitted);
        spec.commandLine().getOut().print(format == Format.json ? report.json() : report.text());
        spec.commandLine().getOut().flush();
        return failOnStale && report.hasStalePages() ? 1 : 0;
    }
}
