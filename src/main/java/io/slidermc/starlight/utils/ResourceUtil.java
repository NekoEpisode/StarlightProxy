package io.slidermc.starlight.utils;

import io.slidermc.starlight.api.translate.TranslateManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

public class ResourceUtil {

    private static final Logger log = LoggerFactory.getLogger(ResourceUtil.class);

    /**
     * 可选注入翻译管理器，注入后日志将使用翻译文本输出。
     * 未注入时日志使用翻译键原文（因 ResourceUtil 在翻译加载前即可能被调用）。
     */
    private static volatile TranslateManager translateManager;

    /** 注入翻译管理器。 */
    public static void setTranslateManager(TranslateManager tm) {
        translateManager = tm;
    }

    /** 翻译指定键，若翻译管理器未注入则直接返回键本身。 */
    private static String t(String key) {
        TranslateManager tm = translateManager;
        return tm != null ? tm.translate(key) : key;
    }

    public static List<String> listFiles(String path, String extension) {
        List<String> fileNames = new ArrayList<>();
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

        try {
            Enumeration<URL> resources = classLoader.getResources(path);
            while (resources.hasMoreElements()) {
                URL resourceUrl = resources.nextElement();

                if ("jar".equals(resourceUrl.getProtocol())) {
                    String jarPath = resourceUrl.getPath().substring(5, resourceUrl.getPath().indexOf("!"));
                    try (JarFile jarFile = new JarFile(jarPath)) {
                        Enumeration<JarEntry> entries = jarFile.entries();
                        while (entries.hasMoreElements()) {
                            JarEntry entry = entries.nextElement();
                            String entryName = entry.getName();
                            if (entryName.startsWith(path) && !entry.isDirectory()) {
                                String fileName = entryName.substring(path.length());
                                if (extension == null || fileName.endsWith(extension)) {
                                    fileNames.add(fileName);
                                }
                            }
                        }
                    }
                } else if ("file".equals(resourceUrl.getProtocol())) {
                    try {
                        java.io.File dir = new java.io.File(resourceUrl.toURI());
                        if (dir.exists() && dir.isDirectory()) {
                            scanDirectory(dir, "", extension, fileNames);
                        }
                    } catch (URISyntaxException e) {
                        log.error(t("starlight.logging.error.resource.invalid_uri"), resourceUrl, e);
                    }
                }
            }
        } catch (IOException e) {
            log.error(t("starlight.logging.error.resource.list_files_failed"), path, e);
        }

        return fileNames;
    }

    private static void scanDirectory(java.io.File dir, String relativePath, String extension, List<String> fileNames) {
        java.io.File[] files = dir.listFiles();
        if (files != null) {
            for (java.io.File file : files) {
                if (file.isDirectory()) {
                    scanDirectory(file, relativePath + file.getName() + "/", extension, fileNames);
                } else if (extension == null || file.getName().endsWith(extension)) {
                    fileNames.add(relativePath + file.getName());
                }
            }
        }
    }

    public static List<String> listAllFiles(String path) {
        return listFiles(path, null);
    }

    public static List<String> listJsonFiles(String path) {
        return listFiles(path, ".json");
    }

    public static String readResourceFile(String filePath) {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        try (InputStream inputStream = classLoader.getResourceAsStream(filePath)) {
            if (inputStream == null) {
                return null;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        } catch (IOException e) {
            log.error(t("starlight.logging.error.resource.read_failed"), filePath, e);
            return null;
        }
    }

    public static List<String> readAllJsonFiles(String directory) {
        List<String> jsonContents = new ArrayList<>();
        List<String> jsonFiles = listJsonFiles(directory);

        for (String fileName : jsonFiles) {
            String fullPath = directory + fileName;
            String content = readResourceFile(fullPath);
            if (content != null) {
                jsonContents.add(content);
            }
        }

        return jsonContents;
    }
}