package io.slidermc.starlight.api.lang;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 校验语言文件的内部一致性。
 *
 * <p>翻译键在定义处（{@code xx_xx.json}）和调用处（{@code TranslateManager#translate} 的实参）
 * 之间没有任何编译期约束，一旦两边脱节就会出现"占位符被异常填充、堆栈不打印"这类静默故障。
 * 本测试守住其中可以在离线状态下检查的部分。
 */
class LangFileConsistencyTest {

    private static final Path LANG_DIR = Path.of("src/main/resources/lang");

    /** 匹配一个 {@code {}} 占位符。 */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\}");

    @Test
    void allLanguageFilesDefineTheSameKeys() throws IOException {
        Map<String, Map<String, String>> byLocale = loadAll();
        assertTrue(byLocale.size() >= 2, "应当至少存在两种语言文件，实际：" + byLocale.keySet());

        List<String> locales = new ArrayList<>(byLocale.keySet());
        String reference = locales.get(0);
        Map<String, String> referenceEntries = byLocale.get(reference);

        for (String locale : locales.subList(1, locales.size())) {
            Map<String, String> entries = byLocale.get(locale);

            List<String> missing = referenceEntries.keySet().stream()
                    .filter(key -> !entries.containsKey(key))
                    .sorted()
                    .toList();
            assertTrue(missing.isEmpty(),
                    locale + " 缺少 " + reference + " 中存在的翻译键: " + missing);

            List<String> extra = entries.keySet().stream()
                    .filter(key -> !referenceEntries.containsKey(key))
                    .sorted()
                    .toList();
            assertTrue(extra.isEmpty(),
                    locale + " 存在 " + reference + " 中没有的翻译键: " + extra);
        }
    }

    @Test
    void placeholdersMatchAcrossLanguages() throws IOException {
        Map<String, Map<String, String>> byLocale = loadAll();
        List<String> locales = new ArrayList<>(byLocale.keySet());
        String reference = locales.get(0);
        Map<String, String> referenceEntries = byLocale.get(reference);

        List<String> mismatches = new ArrayList<>();
        for (Map.Entry<String, String> entry : referenceEntries.entrySet()) {
            String key = entry.getKey();
            int expected = placeholderCount(entry.getValue());

            for (String locale : locales.subList(1, locales.size())) {
                String text = byLocale.get(locale).get(key);
                if (text == null) {
                    continue;
                }
                int actual = placeholderCount(text);
                if (actual != expected) {
                    mismatches.add(key + " (" + reference + "=" + expected
                            + ", " + locale + "=" + actual + ")");
                }
            }
        }

        assertTrue(mismatches.isEmpty(),
                "占位符数量在不同语言间不一致，会导致日志实参错位: " + mismatches);
    }

    @Test
    void eventErrorKeysUseAtMostTwoPlaceholders() {
        // 事件派发路径的异常日志固定为 (处理器标识, 事件名, 异常)：
        // 占位符必须 <= 2 个，否则异常会被当作第三个 {} 的值，堆栈不会打印
        for (Map<String, String> entries : loadAll().values()) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                String key = entry.getKey();
                if (!key.startsWith("starlight.logging.error.event.")) {
                    continue;
                }
                assertTrue(placeholderCount(entry.getValue()) <= 2,
                        key + " 的占位符超过 2 个，事件异常日志至多接受 (处理器, 事件) 两个参数");
            }
        }
    }

    private static int placeholderCount(final String text) {
        Matcher matcher = PLACEHOLDER.matcher(text);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private static Map<String, Map<String, String>> loadAll() {
        Map<String, Map<String, String>> result = new LinkedHashMap<>();
        try (Stream<Path> files = Files.list(LANG_DIR)) {
            List<Path> jsonFiles = files
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .toList();
            assertFalse(jsonFiles.isEmpty(), "未在 " + LANG_DIR.toAbsolutePath() + " 找到语言文件");
            for (Path file : jsonFiles) {
                String locale = file.getFileName().toString().replace(".json", "");
                result.put(locale, parseSimpleJson(Files.readString(file, StandardCharsets.UTF_8)));
            }
        } catch (IOException e) {
            throw new AssertionError("读取语言文件失败（工作目录: "
                    + Path.of("").toAbsolutePath() + "）", e);
        }
        return result;
    }

    /**
     * 解析语言文件。
     *
     * <p>语言文件是扁平的 {@code "键": "值"} 映射，此处不引入 JSON 库，避免为测试增加依赖。
     *
     * @param json 文件内容
     * @return 键值映射
     */
    private static Map<String, String> parseSimpleJson(final String json) {
        Map<String, String> result = new LinkedHashMap<>();
        Matcher matcher = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
                .matcher(json);
        while (matcher.find()) {
            result.put(unescape(matcher.group(1)), unescape(matcher.group(2)));
        }
        return result;
    }

    private static String unescape(final String value) {
        return value.replace("\\\"", "\"").replace("\\\\", "\\");
    }

    @Test
    void langFilesAreLoadableFromClasspath() throws IOException {
        // 语言文件必须能被类加载器找到：TranslateManager 在启动时遍历它们
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream("lang/en_us.json")) {
            assertTrue(stream != null, "en_us.json 应当能通过类加载器读取");
        }
    }
}
