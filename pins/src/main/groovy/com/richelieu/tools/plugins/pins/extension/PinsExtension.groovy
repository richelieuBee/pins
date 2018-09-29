package com.richelieu.tools.plugins.pins.extension

interface PinsExtension {

    /**
     * pin  pins-project
     *
     * @param pinProjects pins-project array
     */
    void pin(String... pinProjects)

    /**
     * sill a sill-project
     *
     * @param sillProject sill project
     */
    void sill(String sillProject)

    /**
     * set debug model
     *
     * @param is debug
     */
    void debug(boolean isDebug)
}
