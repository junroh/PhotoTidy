package com.comp.cli;

public class Console {

    private static final String RESET  = "\u001B[0m";
    private static final String BOLD   = "\u001B[1m";
    private static final String RED    = "\u001B[31m";
    private static final String GREEN  = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String CYAN   = "\u001B[36m";
    private static final String GRAY   = "\u001B[90m";

    public static void format(String format, Object... args) {
        System.out.printf(format, args);
    }

    public static void header(String title) {
        System.out.println();
        System.out.println(BOLD + CYAN + title.toUpperCase() + RESET);
        System.out.println(CYAN + "=".repeat(60) + RESET);
    }

    public static void section(String title) {
        System.out.println("\n" + BOLD + ">> " + title + RESET);
    }

    public static void success(String msg) {
        System.out.println(GREEN + " ✔ " + msg + RESET);
    }

    public static void warn(String msg) {
        System.out.println(YELLOW + " ! " + msg + RESET);
    }

    public static void error(String msg) {
        System.err.println(RED + " ✘ " + msg + RESET);
    }

    public static void kv(String key, Object value) {
        System.out.printf(GRAY + " %-12s : " + RESET + "%s\n", key, value);
    }

    public static void separator() {
        System.out.println(GRAY + "-".repeat(60) + RESET);
    }

    /**
     * A single-line progress bar that only repaints when the whole-percent changes, so it can be
     * fed once per item without flooding stdout. Not thread-safe; drive it from one thread.
     */
    public static final class ProgressBar {
        private static final int WIDTH = 30;
        private final int total;
        private final String label;
        private int lastPercent = -1;

        public ProgressBar(int total, String label) {
            this.total = total;
            this.label = label;
        }

        public void update(int current) {
            if (total <= 0) {
                return;
            }
            int percent = (int) ((long) current * 100 / total);
            if (percent == lastPercent && current < total) {
                return;
            }
            lastPercent = percent;

            int filled = percent * WIDTH / 100;
            StringBuilder bar = new StringBuilder(WIDTH + 2).append('[');
            for (int i = 0; i < WIDTH; i++) {
                bar.append(i < filled ? '=' : (i == filled ? '>' : ' '));
            }
            bar.append(']');

            System.out.print("\r" + CYAN + bar + RESET + String.format(" %3d%% %s", percent, label));
            if (current >= total) {
                System.out.println();
            }
        }
    }
}