package com.andcool.SillyLogger;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static java.lang.String.format;

public class SillyLogger {
    private final boolean colors;
    private final int LogLevel;
    private final List<String> levelWeights = List.of(new String[]{"DEBUG", "INFO", "WARN", "ERROR"});
    public String name;
    String YELLOW = "\u001B[33m";
    String RED = "\u001B[31m";
    String RESET = "\u001B[0m";
    String GRAY = "\u001B[37m";

    public SillyLogger(String name, boolean colors, Level logLevel) {
        this.name = name;
        this.colors = colors;
        this.LogLevel = levelWeights.indexOf(logLevel.toString());
    }

    public void log(Level level, String message) {
        int levelWeight = levelWeights.indexOf(level.toString());
        if (levelWeight < LogLevel) {
            return;
        }

        String color;
        switch (level) {
            case ERROR -> color = RED;
            case WARN -> color = YELLOW;
            case DEBUG -> color = GRAY;
            default -> color = "";
        }
        String nameThread = name.isEmpty() ? Thread.currentThread().getName() : name;
        System.out.println((colors ? color : "") + format("[%s] [%s][%s] %s", getCurrentDate(), nameThread, level.name(), message) + (colors ? RESET : ""));
    }

    public void log(Level level, Throwable throwable, boolean trace) {
        int levelWeight = levelWeights.indexOf(level.toString());
        if (levelWeight < LogLevel) {
            return;
        }

        if (!trace) {
            log(level, throwable.toString());
            return;
        }

        String color;
        switch (level) {
            case ERROR -> color = RED;
            case WARN -> color = YELLOW;
            default -> color = "";
        }

        String stackTrace = getStackTraceAsString(throwable);
        String nameThread = name.isEmpty() ? Thread.currentThread().getName() : name;
        System.out.println((colors ? color : "") + format("[%s] [%s][%s] %s", getCurrentDate(), nameThread, level.name(), stackTrace) + (colors ? RESET : ""));
    }

    private String getStackTraceAsString(Throwable throwable) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        throwable.printStackTrace(pw);
        return sw.toString();
    }

    private String getCurrentDate() {
        LocalDateTime currentDateTime = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
        return currentDateTime.format(formatter);
    }

}