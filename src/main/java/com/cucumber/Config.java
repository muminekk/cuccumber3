package com.cucumber;

import net.fabricmc.loader.api.FabricLoader;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class Config {
    public static boolean showDistance = true;
    public static boolean showHealth = true;
    public static boolean sortByName = false;
    public static boolean nametags = true;
    public static int dim = 56;      // background dim in %, 0-90
    public static int corner = 6;    // corner roundness in px, 0-10
    public static int radius = 300;  // 100-10000
    public static int theme = 0;
    public static int bombSound = 0;
    public static int bombDelay = 10; // seconds, 1-120

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("cucumber.properties");
    }

    private static int num(Properties p, String key, int def, int min, int max) {
        try {
            return Math.max(min, Math.min(max, Integer.parseInt(p.getProperty(key, String.valueOf(def)))));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public static void load() {
        try {
            if (!Files.exists(file())) return;
            Properties p = new Properties();
            try (InputStream in = Files.newInputStream(file())) {
                p.load(in);
            }
            showDistance = Boolean.parseBoolean(p.getProperty("showDistance", "true"));
            showHealth = Boolean.parseBoolean(p.getProperty("showHealth", "true"));
            sortByName = Boolean.parseBoolean(p.getProperty("sortByName", "false"));
            nametags = Boolean.parseBoolean(p.getProperty("nametags", "true"));
            dim = num(p, "dim", 56, 0, 90);
            corner = num(p, "corner", 6, 0, 10);
            radius = num(p, "radius", 300, 100, 10000);
            theme = num(p, "theme", 0, 0, Theme.ALL.length - 1);
            bombSound = num(p, "bombSound", 0, 0, Bomb.NAMES.length - 1);
            bombDelay = num(p, "bombDelay", 10, 1, 120);
        } catch (Exception ignored) {
        }
    }

    public static void save() {
        try {
            Properties p = new Properties();
            p.setProperty("showDistance", String.valueOf(showDistance));
            p.setProperty("showHealth", String.valueOf(showHealth));
            p.setProperty("sortByName", String.valueOf(sortByName));
            p.setProperty("nametags", String.valueOf(nametags));
            p.setProperty("dim", String.valueOf(dim));
            p.setProperty("corner", String.valueOf(corner));
            p.setProperty("radius", String.valueOf(radius));
            p.setProperty("theme", String.valueOf(theme));
            p.setProperty("bombSound", String.valueOf(bombSound));
            p.setProperty("bombDelay", String.valueOf(bombDelay));
            try (OutputStream out = Files.newOutputStream(file())) {
                p.store(out, "cucumber settings");
            }
        } catch (Exception ignored) {
        }
    }
}
