package dev.joshlucem.nowquiz.core;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Thin logger wrapper so debug verbosity can be toggled from configuration.
 */
public final class PluginLogger {

    private final Logger logger;
    private volatile boolean debugEnabled;

    public PluginLogger(Logger logger, boolean debugEnabled) {
        this.logger = logger;
        this.debugEnabled = debugEnabled;
    }

    public void setDebugEnabled(boolean debugEnabled) {
        this.debugEnabled = debugEnabled;
    }

    public void info(String message) {
        this.logger.info(message);
    }

    public void warn(String message) {
        this.logger.warning(message);
    }

    public void warn(String message, Throwable throwable) {
        this.logger.log(Level.WARNING, message, throwable);
    }

    public void error(String message, Throwable throwable) {
        this.logger.log(Level.SEVERE, message, throwable);
    }

    public void debug(String message) {
        if (this.debugEnabled) {
            this.logger.info("[debug] " + message);
        }
    }
}
