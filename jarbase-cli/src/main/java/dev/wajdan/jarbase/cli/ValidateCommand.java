package dev.wajdan.jarbase.cli;

import dev.wajdan.jarbase.core.validation.DocsValidator;
import dev.wajdan.jarbase.core.validation.ValidationReport;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Visibility;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

@Command(
        name = "validate",
        mixinStandardHelpOptions = true,
        description = "Checks that the docs are healthy: titles, readable settings, sources that match files, working links.",
        footer = {"", "Exit codes: 0 = no errors, 1 = errors found (or warnings with --strict), 2 = could not run."})
public class ValidateCommand implements Callable<Integer> {

    enum Format { text, json }

    @Spec
    CommandSpec spec;

    @Option(names = "--docs", defaultValue = "docs", showDefaultValue = Visibility.ALWAYS,
            description = "Docs folder, relative to the repository root.")
    String docsFolder;

    @Option(names = "--format", defaultValue = "text", showDefaultValue = Visibility.ALWAYS,
            description = "Output format: ${COMPLETION-CANDIDATES}.")
    Format format;

    @Option(names = "--strict", description = "Treat warnings as errors.")
    boolean strict;

    @Option(names = "--repo", defaultValue = ".", hidden = true,
            description = "Folder inside the repository to work from. Defaults to the current directory.")
    Path repo;

    @Override
    public Integer call() {
        ValidationReport report = DocsValidator.validate(repo, docsFolder);
        spec.commandLine().getOut().print(format == Format.json ? report.json() : report.text());
        spec.commandLine().getOut().flush();
        boolean failed = report.hasErrors() || (strict && report.warningCount() > 0);
        return failed ? 1 : 0;
    }
}
