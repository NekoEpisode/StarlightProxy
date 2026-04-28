package io.slidermc.starlight.api.translate;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.slidermc.starlight.utils.ResourceUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 翻译管理器，支持加载内置语言文件和外部 JSON 语言文件。
 *
 * <p>翻译文件 JSON 格式：
 * <pre>{@code
 * {
 *   "metadata": { "name": "简体中文" },
 *   "translations": {
 *     "starlight.startup": "启动中",
 *     "starlight.shutdown": "关闭中"
 *   }
 * }
 * }</pre>
 *
 * <p>示例用法：
 * <pre>{@code
 * TranslateManager manager = new TranslateManager();
 * manager.loadBuiltin();
 * manager.setActiveLocale("zh_cn");
 * String text = manager.translate("starlight.startup");
 * }</pre>
 */
public class TranslateManager {

    private static final Logger log = LoggerFactory.getLogger(TranslateManager.class);

    /** 内置语言文件所在的资源目录 */
    private static final String LANG_RESOURCE_DIR = "lang/";

    /** locale → 语言条目。使用 {@link ConcurrentHashMap} 保证并发读写安全，迭代顺序不保证。 */
    private final Map<String, LanguageEntry> languages = new ConcurrentHashMap<>();

    /** 维护 locale 的插入顺序，供 {@link #getLocaleNames()} 等需要有序遍历的场景使用。 */
    private final List<String> localeOrder = new CopyOnWriteArrayList<>();

    private volatile String activeLocale = "en_us";

    /**
     * 从 {@code resources/lang} 目录自动加载所有内置 JSON 语言文件。
     * <p>文件名（不含 {@code .json} 后缀）即为语言的内部名称，如 {@code zh_cn}。
     */
    public void loadBuiltin() {
        List<String> files = ResourceUtil.listFiles(LANG_RESOURCE_DIR, ".json");
        for (String fileName : files) {
            // fileName 可能带前导 '/'，统一去除
            if (fileName.startsWith("/")) fileName = fileName.substring(1);
            String locale = fileName.endsWith(".json")
                    ? fileName.substring(0, fileName.length() - 5)
                    : fileName;
            String content = ResourceUtil.readResourceFile(LANG_RESOURCE_DIR + fileName);
            if (content != null) {
                parseAndLoad(locale, content);
            } else {
                log.warn("Cannot read built-in language file: {}", LANG_RESOURCE_DIR + fileName);
            }
        }
    }

    /**
     * 从 {@link InputStream} 加载翻译，语言内部名称由调用方指定。
     * 流将在读取完成后关闭。
     *
     * @param locale 语言内部名称，如 {@code zh_cn}
     * @param stream JSON 语言文件输入流
     */
    public void loadExternal(String locale, InputStream stream) {
        try (stream) {
            String content = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            parseAndLoad(locale, content);
        } catch (IOException e) {
            log.error("Load language from InputStream failed (locale={})", locale, e);
        }
    }

    /**
     * 从外部 JSON 文件加载翻译，语言内部名称由调用方指定。
     *
     * @param locale   语言内部名称，如 {@code zh_cn}
     * @param jsonFile 外部 JSON 语言文件
     */
    public void loadExternal(String locale, File jsonFile) {
        try {
            String content = Files.readString(jsonFile.toPath(), StandardCharsets.UTF_8);
            parseAndLoad(locale, content);
        } catch (IOException e) {
            log.error("Load external language file failed: {}", jsonFile.getAbsolutePath(), e);
        }
    }

    /**
     * 从外部 JSON 文件加载翻译，语言内部名称从文件名推断（文件名不含 {@code .json} 即为 locale）。
     *
     * @param jsonFile 外部 JSON 语言文件
     */
    public void loadExternal(File jsonFile) {
        String name = jsonFile.getName();
        String locale = name.endsWith(".json") ? name.substring(0, name.length() - 5) : name;
        loadExternal(locale, jsonFile);
    }

    /**
     * 设置当前激活的语言。
     *
     * @param locale 语言内部名称，如 {@code zh_cn}
     */
    public void setActiveLocale(String locale) {
        this.activeLocale = locale;
    }

    /**
     * 获取当前激活的语言内部名称。
     *
     * @return 当前激活语言的内部名称
     */
    public String getActiveLocale() {
        return activeLocale;
    }

    /**
     * 使用当前激活语言翻译指定键。若当前语言未找到，自动回退到 {@code en_us}；
     * 仍未找到则返回键本身。
     *
     * @param key 翻译键，如 {@code starlight.startup}
     * @return 翻译后的文本
     */
    public String translate(String key) {
        return translate(activeLocale, key);
    }

    /**
     * 使用指定语言翻译指定键。若目标语言未找到该键，自动回退到 {@code en_us}；
     * 仍未找到则返回键本身。
     *
     * @param locale 语言内部名称
     * @param key    翻译键
     * @return 翻译后的文本
     */
    public String translate(String locale, String key) {
        LanguageEntry entry = languages.get(locale);
        if (entry != null) {
            String result = entry.get(key);
            if (result != null) return result;
        }
        // 回退到 en_us
        if (!"en_us".equals(locale)) {
            LanguageEntry fallback = languages.get("en_us");
            if (fallback != null) {
                String result = fallback.get(key);
                if (result != null) return result;
            }
        }
        return key;
    }

    /**
     * 获取所有已加载语言的内部名称集合，如 {@code [zh_cn, en_us]}。
     *
     * @return 不可修改的内部名称集合
     */
    public Set<String> getLocales() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(localeOrder));
    }

    /**
     * 获取指定语言的显示名称，如 {@code 简体中文}、{@code English (US)}。
     *
     * @param locale 语言内部名称
     * @return 显示名称；若语言未加载则返回 locale 本身
     */
    public String getDisplayName(String locale) {
        LanguageEntry entry = languages.get(locale);
        return entry != null ? entry.getDisplayName() : locale;
    }

    /**
     * 获取所有已加载语言的 内部名称 → 显示名称 映射，如 {@code {zh_cn=简体中文, en_us=英语(美国)}}。
     *
     * @return 不可修改的映射
     */
    public Map<String, String> getLocaleNames() {
        Map<String, String> result = new LinkedHashMap<>();
        for (String locale : localeOrder) {
            LanguageEntry entry = languages.get(locale);
            if (entry != null) {
                result.put(locale, entry.getDisplayName());
            }
        }
        return Collections.unmodifiableMap(result);
    }

    /**
     * 判断指定语言是否已加载。
     *
     * @param locale 语言内部名称
     * @return 已加载返回 {@code true}
     */
    public boolean hasLocale(String locale) {
        return languages.containsKey(locale);
    }

    /**
     * 向已加载的语言中动态添加或覆盖一条翻译。若该语言尚未加载则自动创建。
     *
     * @param locale 语言内部名称，如 {@code zh_cn}
     * @param key    翻译键，如 {@code starlight.startup}
     * @param value  翻译文本
     */
    public void addTranslation(String locale, String key, String value) {
        boolean isNew = !languages.containsKey(locale);
        languages.computeIfAbsent(locale, LanguageEntry::new)
                 .merge(Map.of(key, value));
        if (isNew && !localeOrder.contains(locale)) {
            localeOrder.add(locale);
        }
    }

    /**
     * 从已加载的语言中移除一条翻译键。
     *
     * <p><b>警告：</b>滥用此方法可能导致翻译缺失，使其他模块在翻译时回退到键名原文甚至产生
     * 不可预期的显示问题。调用方需自行确认所移除的键不再被任何模块使用，后果自负。
     *
     * @param locale 语言内部名称，如 {@code zh_cn}
     * @param key    要移除的翻译键
     * @return 该键原有的翻译文本；若键不存在则返回 {@code null}
     */
    public String removeTranslation(String locale, String key) {
        LanguageEntry entry = languages.get(locale);
        if (entry == null) return null;
        return entry.remove(key);
    }

    // ---------- 内部解析 ----------

    private void parseAndLoad(String locale, String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();

            Map<String, String> translations = new LinkedHashMap<>();
            if (root.has("translations")) {
                JsonObject trans = root.getAsJsonObject("translations");
                for (String key : trans.keySet()) {
                    translations.put(key, trans.get(key).getAsString());
                }
            }

            boolean isNew = !languages.containsKey(locale);
            LanguageEntry entry;
            if (isNew) {
                String displayName = locale;
                if (root.has("metadata")) {
                    JsonObject meta = root.getAsJsonObject("metadata");
                    if (meta.has("name")) {
                        displayName = meta.get("name").getAsString();
                    }
                }
                entry = new LanguageEntry(displayName);
                languages.put(locale, entry);
                if (!localeOrder.contains(locale)) {
                    localeOrder.add(locale);
                }
                log.info("Loaded language {} ({}), total {} translations", locale, displayName, translations.size());
            } else {
                entry = languages.get(locale);
            }
            entry.merge(translations);
        } catch (Exception e) {
            log.error("Parse translation JSON failed (locale={})", locale, e);
        }
    }

    // ---------- 内部数据类 ----------

    private static final class LanguageEntry {
        private volatile String displayName;
        private final Map<String, String> translations = new ConcurrentHashMap<>();

        LanguageEntry(String displayName) {
            this.displayName = displayName;
        }

        void setDisplayName(String name) {
            this.displayName = name;
        }

        void merge(Map<String, String> map) {
            translations.putAll(map);
        }

        String get(String key) {
            return translations.get(key);
        }

        String remove(String key) {
            return translations.remove(key);
        }

        String getDisplayName() {
            return displayName;
        }
    }
}




