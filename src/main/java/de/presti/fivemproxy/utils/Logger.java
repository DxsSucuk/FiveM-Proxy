package de.presti.fivemproxy.utils;

import de.presti.fivemproxy.main.Main;
import lombok.Getter;
import lombok.Setter;

import java.util.Arrays;

public class Logger {

    @Getter
    @Setter
    private static Service[] allowedServices = new Service[] { Service.TCP, Service.UDP, Service.NONE };

    public static void log(LogLevel level, String message, Service logService) {
        if (level == LogLevel.DEBUG && !Main.isDebug()) return;
        if (Arrays.stream(allowedServices).anyMatch(x -> x == logService)) {
            System.out.println("[" + level.name() + "] " + message);
        }
    }

    public static void logTCP(LogLevel level, String message) {
        if (level == LogLevel.DEBUG && !Main.isDebug()) return;
        log(level, message, Service.TCP);
    }

    public static void logUDP(LogLevel level, String message) {
        if (level == LogLevel.DEBUG && !Main.isDebug()) return;
        log(level, message, Service.UDP);
    }

    public static void log(LogLevel level, String message) {
        if (level == LogLevel.DEBUG && !Main.isDebug()) return;
        log(level, message, Service.NONE);
    }

    public static void logException(Exception e, Service logService) {
        log(LogLevel.ERROR, e.getMessage(), logService);
    }

    public enum LogLevel {
        INFO, WARNING, ERROR, SUCCESS, DEBUG
    }

    public enum Service {
        TCP, UDP, NONE
    }

}
