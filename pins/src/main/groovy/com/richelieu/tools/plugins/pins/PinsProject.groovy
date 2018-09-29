package com.richelieu.tools.plugins.pins

/**
 * Pins project
 *
 * 1. sill project: contains base R.java and AndroidManifest.xml
 * 2. pin project: normal pins project, dependencies check unit only
 */
class PinsProject {
    String name
    File pinProjectDir

    @Override
    String toString() {
        return "PinsProject{" +
                "name='" + name + '\'' +
                ", pinProjectDir=" + pinProjectDir +
                '}';
    }
}