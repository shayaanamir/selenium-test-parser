package com.testparser.utils;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

public class ConfigPropertiesReader {

    public static Map<String, String> loadUrls(String configFilePath) throws IOException {
        Map<String, String> urls = new HashMap<>();
        Properties properties = new Properties();

        try (InputStream input = new FileInputStream(configFilePath)) {
            properties.load(input);
            for (String key : properties.stringPropertyNames()) {
                String value = properties.getProperty(key);
                if (isUrlProperty(key, value)) {
                    urls.put(key, value);
                }
            }
        }

        return urls;
    }

    private static Optional<File> findConfigFileDFS(File dir) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) {
            return Optional.empty();
        }

        File[] files = dir.listFiles();
        if (files == null) {
            return Optional.empty();
        }

        for (File file : files) {
            if (file.isFile()) {
                String name = file.getName().toLowerCase();
                if (name.equals("config.properties")) {
                    return Optional.of(file);
                }
            } else if (file.isDirectory()) {
                Optional<File> found = findConfigFileDFS(file);
                if (found.isPresent()) {
                    return found;
                }
            }
        }

        return Optional.empty();
    }

    public static Map<String, String> loadUrlsFromProject(String projectPath) throws IOException {
        String[] possiblePaths = {
            projectPath + "/config.properties",
            projectPath + "/Config.properties",
            projectPath + "/src/main/resources/config.properties",
            projectPath + "/src/main/resources/Config.properties",
            projectPath + "/src/test/resources/config.properties",
            projectPath + "/src/test/resources/Config.properties",
            projectPath + "/resources/config.properties",
            projectPath + "/resources/Config.properties"
        };

        for (String path : possiblePaths) {
            try {
                return loadUrls(path);
            } catch (IOException ignored) {}
        }

        Optional<File> configFile = findConfigFileDFS(new File(projectPath));
        if (configFile.isPresent()) {
            return loadUrls(configFile.get().getAbsolutePath());
        }

        return new HashMap<>();
    }

    private static boolean isUrlProperty(String key, String value) {
        if (value == null || value.trim().isEmpty()) {
            return false;
        }

        String lowerKey = key.toLowerCase();
        boolean keyIndicatesUrl = lowerKey.contains("url") ||
                                  lowerKey.contains("link") ||
                                  lowerKey.contains("endpoint") ||
                                  lowerKey.contains("site");

        boolean valueIsUrl = value.startsWith("http://") ||
                             value.startsWith("https://") ||
                             value.startsWith("www.");

        return keyIndicatesUrl || valueIsUrl;
    }

    public static String findMatchingUrl(String testMethodName, Map<String, String> urls) {
        if (urls.isEmpty()) {
            return null;
        }

        String lowerTestName = testMethodName.toLowerCase();

        for (Map.Entry<String, String> entry : urls.entrySet()) {
            String key = entry.getKey().toLowerCase();
            if (key.equals(lowerTestName)) {
                return entry.getValue();
            }
        }

        for (Map.Entry<String, String> entry : urls.entrySet()) {
            String key = entry.getKey().toLowerCase();
            String cleanKey = key.replaceAll("(url|link|endpoint|site)$", "");
            if (lowerTestName.contains(cleanKey) || cleanKey.contains(lowerTestName)) {
                return entry.getValue();
            }
        }

        Map<String, String[]> semanticMappings = Map.of(
            "login", new String[]{"login", "signin", "auth", "account"},
            "register", new String[]{"register", "signup", "create", "account"},
            "search", new String[]{"search", "find", "filter", "query"},
            "product", new String[]{"product", "item", "detail", "view"},
            "cart", new String[]{"cart", "basket", "bag", "checkout"},
            "grocery", new String[]{"grocery", "food", "supermart"},
            "home", new String[]{"home", "main", "index", "landing"}
        );

        for (Map.Entry<String, String[]> mapping : semanticMappings.entrySet()) {
            String[] keywords = mapping.getValue();
            for (String keyword : keywords) {
                if (lowerTestName.contains(keyword)) {
                    for (Map.Entry<String, String> urlEntry : urls.entrySet()) {
                        String urlKey = urlEntry.getKey().toLowerCase();
                        if (urlKey.contains(keyword)) {
                            return urlEntry.getValue();
                        }
                    }
                }
            }
        }

        return urls.values().iterator().next();
    }
}
