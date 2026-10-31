package io.github.thedistortedwajdan.jarbase.cli;

import picocli.CommandLine.IVersionProvider;

/** Reads the version from the jar's manifest; shows "dev" when running from compiled classes. */
final class VersionProvider implements IVersionProvider {

    @Override
    public String[] getVersion() {
        String version = VersionProvider.class.getPackage().getImplementationVersion();
        return new String[] {"jarbase " + (version == null ? "dev" : version)};
    }
}
