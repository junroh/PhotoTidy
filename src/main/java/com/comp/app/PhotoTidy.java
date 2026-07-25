package com.comp.app;

import com.comp.cli.Console;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

public class PhotoTidy {

    private static final Logger logger = LogManager.getLogger(PhotoTidy.class);

    public static void main(@NotNull final String[] args) {
        final String configPath = (args.length == 0) ? "./src/main/resources/config.properties" : args[0];
        final Options options;
        try {
            options = Options.newOptionsFromFile(configPath);
        } catch (Exception e) {
            Console.error("Invalid Configuration: " + e.getMessage());
            logger.error("Configuration failure", e);
            return;
        }

        try {
            new Pipeline(options).execute();
        } catch (Exception e) {
            Console.error("Application Failed: " + e.getMessage());
            logger.error("Application crash", e);
        }
    }
}
