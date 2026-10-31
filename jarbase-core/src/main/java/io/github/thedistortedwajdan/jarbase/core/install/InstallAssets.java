package io.github.thedistortedwajdan.jarbase.core.install;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * The files the installer puts under {@code .jarbase/}, and how the tool will be found at run time.
 *
 * <p>Two ways to make the jar available:
 * <ul>
 *   <li>{@code version} set: the Maven plugin case. Maven keeps the jar in its local repository, and the
 *       version is written to {@code .jarbase/version} (committed) so every clone finds the same jar.</li>
 *   <li>{@code jarSource} set: the command-line case. The jar is copied to {@code .jarbase/jarbase.jar}
 *       (and kept out of git).</li>
 * </ul>
 *
 * @param hookScript     text of the fast hook gatekeeper script
 * @param launcherScript text of the launcher script that finds and runs the jar
 * @param jarSource      the runnable jar to copy, or null
 * @param version        the version Maven should provide, or null
 */
public record InstallAssets(String hookScript, String launcherScript, Path jarSource, String version) {

    /** The scripts that ship inside jarbase-core, with the given way of finding the jar. */
    public static InstallAssets bundled(Path jarSource, String version) {
        return new InstallAssets(resource("/jarbase/hook.sh"), resource("/jarbase/launcher.sh"), jarSource, version);
    }

    private static String resource(String name) {
        try (InputStream in = InstallAssets.class.getResourceAsStream(name)) {
            if (in == null) {
                throw new IllegalStateException("missing from jarbase-core: " + name);
            }
            // sh needs Unix line endings, even when this runs on Windows
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + name, e);
        }
    }
}
