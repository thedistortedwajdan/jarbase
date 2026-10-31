package dev.wajdan.jarbase.core.install;

import java.nio.file.Path;

/**
 * Files the installer copies into a project but that live with the command-line tool, not in core.
 *
 * @param hookScript the text of the fast hook gatekeeper script
 * @param jarSource  the runnable jarbase jar to copy, or null when running from compiled classes (nothing to copy)
 */
public record InstallAssets(String hookScript, Path jarSource) {
}
