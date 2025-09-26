package com.hundefined.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class BotConfig {
    private static final Properties properties = new Properties();

    static {
        try (InputStream input = BotConfig.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (input == null) {
                throw new RuntimeException("Unable to find config.properties");
            }
            properties.load(input);
        } catch (IOException e) {
            throw new RuntimeException("Error loading config.properties", e);
        }
    }

    public static String getBotToken() {
        return properties.getProperty("BOT TOKEN");
    }

    // Add this new method for Riot token
    public static String getRiotToken() {
        return properties.getProperty("RIOT TOKEN");
    }

    // Add database configuration methods
    public static String getDatabaseUrl() {
        return properties.getProperty("DATA BASE URL", "ENTER URL");
    }

    public static String getDatabaseUsername() {
        return properties.getProperty("DATA BASE USER", "root");
    }

    public static String getDatabasePassword() {
        return properties.getProperty("DATA BASE PASS", "");
    }
}