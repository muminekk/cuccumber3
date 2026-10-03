package com.cucumber;

public record Theme(String name, int bg, int sidebar, int panel, int accent, int text, int dim) {
    public static final Theme[] ALL = {
        new Theme("Neverlose", 0xFF0B1220, 0xFF080D18, 0xFF121B2E, 0xFF00A2FF, 0xFFE6EEF9, 0xFF6B7A90),
        new Theme("Cucumber",  0xFF0D1410, 0xFF09100C, 0xFF14201A, 0xFF4CD964, 0xFFE8F5EA, 0xFF6F8A76),
        new Theme("Violet",    0xFF110D1C, 0xFF0C0914, 0xFF1A1429, 0xFFA855F7, 0xFFEFE8FA, 0xFF7B6E94),
        new Theme("Crimson",   0xFF170C0E, 0xFF100809, 0xFF241316, 0xFFFF3B52, 0xFFF9E8EA, 0xFF946E73),
        new Theme("Light",     0xFFEEF2F7, 0xFFDDE4ED, 0xFFFFFFFF, 0xFF0A84FF, 0xFF1B2430, 0xFF7C8796)
    };
}
