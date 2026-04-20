package io.slidermc.starlight.utils;

public class StringUtils {
    /**
     * 按中英文混合字符长度截断字符串。
     * - ASCII字符：1字符
     * - CJK字符及中文：2字符
     * - Emoji及其他高Unicode字符：2字符
     *
     * @param str 原始字符串
     * @param maxCharCount 最大字符计数
     * @return 截断后的字符串
     */
    public static String truncateByCharCount(String str, int maxCharCount) {
        if (str == null || maxCharCount <= 0) return "";

        int count = 0;
        StringBuilder sb = new StringBuilder();
        int i = 0;

        while (i < str.length()) {
            int codePoint = str.codePointAt(i);
            int charWidth = getCharWidth(codePoint);

            // 如果添加此字符会超过限制，则停止
            if (count + charWidth > maxCharCount) {
                break;
            }

            count += charWidth;
            // 使用Character.toChars将code point转换为char序列（可能是1个或2个char）
            sb.appendCodePoint(codePoint);
            i += Character.charCount(codePoint);
        }

        return sb.toString();
    }

    /**
     * 计算单个Unicode code point的显示宽度。
     * - ASCII字符（0x00-0x7F）：1字符
     * - 其他字符（包括中文、CJK、Emoji等）：2字符
     *
     * @param codePoint Unicode code point
     * @return 宽度（字符计数）
     */
    private static int getCharWidth(int codePoint) {
        // ASCII范围内的字符计为1字符
        if (codePoint < 0x80) {
            return 1;
        }
        // CJK及其他高Unicode字符（包括emoji）计为2字符
        return 2;
    }
}

