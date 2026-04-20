package io.slidermc.starlight.utils;

public class StringUtils {
    /**
     * 按中英文混合字符长度截断字符串，中文算2字符，英文算1字符。
     * @param str 原始字符串
     * @param maxCharCount 最大字符数
     * @return 截断后的字符串
     */
    public static String truncateByCharCount(String str, int maxCharCount) {
        if (str == null || maxCharCount <= 0) return "";
        int count = 0;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            // 中文、全角符号等
            if (c > 127) {
                count += 2;
            } else {
                count += 1;
            }
            if (count > maxCharCount) {
                break;
            }
            sb.append(c);
        }
        return sb.toString();
    }
}

