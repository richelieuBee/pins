package com.richelieu.tools.plugins.pins.logger

import com.android.utils.ILogger

import java.text.SimpleDateFormat
import org.gradle.api.Project


/**
 *  Logger tools
 *
 * @author richelieu  09.26 2018
 */
class Logger implements ILogger {

    static final String TEMPLATE = "[%s]  %s  %s  %d  %s/%s   %s"
    static final String DEFAULT_DATE_FORMAT = "mm:ss.SSS"
    static final int VERBOSE = 0
    static final int INFO = 1
    static final int WARNING = 2
    static final int ERROR = 3
    static final int QUIET = 4
    static final String[] LEVEL_NAMES = ["V", "I", "W", "E"]

    static int logLevel = 1
    static Project project
    static SimpleDateFormat format = new SimpleDateFormat(DEFAULT_DATE_FORMAT)

    //member method for ManifestMerger2
    Logger() {
    }


    @Override
    void error(Throwable t, String message, Object... args) {
        printInternal("ManifestMerger2", ERROR, methodName, message, args)
    }


    @Override
    void warning(String message, Object... args) {
        printInternal("ManifestMerger2", WARNING, "unknown", message, args)
    }


    @Override
    void info(String message, Object... args) {
        printInternal("ManifestMerger2", INFO, "unknown", message, args)
    }


    @Override
    void verbose(String message, Object... args) {
        printInternal("ManifestMerger2", VERBOSE, "unknown", message, args)
    }

    // static method for all
    static void initialize(Project project) {
        this.project = project
    }

    static void setLogLevel(int level) {
        logLevel = level
    }

    static void verbose(String tag, String methodName, String message, Object... args) {
        printInternal(tag, VERBOSE, methodName, message, args)
    }

    static void info(String tag, String methodName, String message, Object... args) {
        printInternal(tag, INFO, methodName, message, args)
    }


    static void warning(String tag, String methodName, String message, Object... args) {
        printInternal(tag, WARNING, methodName, message, args)
    }


    static void error(String tag, String methodName, String message, Object... args) {
        printInternal(tag, ERROR, methodName, message, args)
    }


    static void println() {
        printLine("")
    }

    static void printLine(String message) {
        if (logLevel > WARNING) {
            return
        }

        println(message)
    }


    private static printInternal(String tag, int level, String methodName, String message, Object... args) {
        if (logLevel > level) {
            return
        }

        println(String.format(TEMPLATE, tag, ":${project.name}", format.format(new Date()), Thread.currentThread().getId(), LEVEL_NAMES[level], methodName, String.format(message, args)))

    }
}
