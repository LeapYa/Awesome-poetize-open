package com.ld.poetry.utils;

import org.springframework.util.StringUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StringUtil {

    public static void main(String[] args) {
        System.out.println(removeHtml(""));
    }

    private static final String REGEX_SCRIPT = "<script[^>]*?>[\\s\\S]*?<\\/script>";

    private static final String REGEX_STYLE = "<style[^>]*?>[\\s\\S]*?<\\/style>";

    public static String removeHtml(String content) {
        return content.replace("<", "《").replace(">", "》");
    }

    public static boolean matchString(String text, String searchText) {
        if (!StringUtils.hasText(text) || !StringUtils.hasText(searchText)) {
            return false;
        }
        
        // 支持空格分隔的多关键词匹配
        String[] keywords = searchText.trim().split("\\s+");
        for (String keyword : keywords) {
            if (keyword.isEmpty()) continue;
            
            Pattern pattern = Pattern.compile(Pattern.quote(keyword), Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(text);
            
            if (!matcher.find()) {
                return false; // 只要有一个关键词不匹配，就返回false
            }
        }
        
        return true; // 所有关键词都匹配
    }
    
    /**
     * 高亮显示搜索结果
     * @param text 原始文本
     * @param searchText 搜索关键词
     * @param highlightStart 高亮开始标记
     * @param highlightEnd 高亮结束标记
     * @return 高亮后的文本
     */
    public static String highlightText(String text, String searchText, String highlightStart, String highlightEnd) {
        if (!StringUtils.hasText(text) || !StringUtils.hasText(searchText)) {
            return text;
        }
        
        String result = text;
        String[] keywords = searchText.trim().split("\\s+");
        
        for (String keyword : keywords) {
            if (keyword.isEmpty()) continue;
            
            // 不区分大小写替换
            Pattern pattern = Pattern.compile(Pattern.quote(keyword), Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(result);
            
            StringBuffer sb = new StringBuffer();
            while (matcher.find()) {
                String matchedText = matcher.group();
                matcher.appendReplacement(sb, highlightStart + matchedText + highlightEnd);
            }
            matcher.appendTail(sb);
            result = sb.toString();
        }
        
        return result;
    }

    public static boolean isValidFileName(String fileName) {
        if (!StringUtils.hasText(fileName) || fileName.length() > 128) {
            return false;
        }
        // 此示例允许文件名包含字母、数字、下划线、点号和连字符（减号），且不能以点号、下划线和连字符（减号）开头
        String regex = "^(?![-._])[a-zA-Z0-9-._]+$";
        return fileName.matches(regex);
    }

    public static boolean isValidDirectoryName(String directoryName) {
        if (!StringUtils.hasText(directoryName) || directoryName.length() > 128) {
            return false;
        }
        // 此示例允许目录名称只包含字母、数字、下划线和连字符（减号）
        String regex = "^[a-zA-Z0-9-_]+$";
        return directoryName.matches(regex);
    }
}
