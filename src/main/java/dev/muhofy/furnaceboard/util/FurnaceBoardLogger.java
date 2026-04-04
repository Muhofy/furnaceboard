package dev.muhofy.furnaceboard.util;

import dev.muhofy.furnaceboard.FurnaceBoardMod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared logger for all FurnaceBoard systems.
 * Use this instead of System.out.println everywhere.
 */
public final class FurnaceBoardLogger {

    private static final Logger LOGGER = LoggerFactory.getLogger(FurnaceBoardMod.MOD_ID);

    private FurnaceBoardLogger() {}

    public static void info(String message) {
        LOGGER.info("[FurnaceBoard] {}", message);
    }

    public static void warn(String message) {
        LOGGER.warn("[FurnaceBoard] {}", message);
    }

    public static void error(String message) {
        LOGGER.error("[FurnaceBoard] {}", message);
    }

    public static void error(String message, Throwable throwable) {
        LOGGER.error("[FurnaceBoard] {}", message, throwable);
    }

    public static void debug(String message) {
        LOGGER.debug("[FurnaceBoard] {}", message);
    }
}