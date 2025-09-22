package com.chambua.vismart.util;

public class TeamNameNormalizer {
    public static String normalize(String name) {
        if (name == null) return null;
        return name.trim()
                .replaceAll("\\s+", " ")
                .toLowerCase();
    }
}
