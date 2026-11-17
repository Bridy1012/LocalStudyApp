package com.localstudy.app.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DirectoryParser {
    private static final Pattern CHAPTER_CN = Pattern.compile("^第\\s*[一二三四五六七八九十百零〇0-9]+\\s*[章篇部分]\\s*(.*)$");
    private static final Pattern SECTION_CN = Pattern.compile("^第\\s*[一二三四五六七八九十百零〇0-9]+\\s*[节课讲单元]\\s*(.*)$");
    private static final Pattern DECIMAL = Pattern.compile("^(\\d+(?:\\.\\d+)+)\\.?\\s*[、.\\-—:]?\\s*(.+)$");
    private static final Pattern NUMBERED = Pattern.compile("^(\\d+)\\.?\\s*[、.\\-—:]\\s*(.+)$");
    private static final Pattern CHINESE_NUMBERED = Pattern.compile("^([一二三四五六七八九十]+)、\\s*(.+)$");
    private static final Pattern BULLET = Pattern.compile("^[•·▪◦\\-—]\\s*(.+)$");

    private DirectoryParser() {
    }

    public static Parsed parse(String rawText) {
        Parsed result = new Parsed();
        ParsedChapter current = null;
        String currentNumericRoot = null;
        String[] lines = rawText == null ? new String[0] : rawText.split("\\r?\\n");

        for (String raw : lines) {
            String line = raw == null ? "" : raw.trim().replaceAll("\\s+", " ");
            if (line.isEmpty() || isNoise(line)) {
                continue;
            }

            Matcher chapter = CHAPTER_CN.matcher(line);
            if (chapter.matches()) {
                current = result.addChapter(cleanTitle(line));
                currentNumericRoot = null;
                continue;
            }

            Matcher sectionCn = SECTION_CN.matcher(line);
            if (sectionCn.matches()) {
                if (current == null) {
                    current = result.addChapter("未分章");
                }
                current.sections.add(cleanTitle(line));
                continue;
            }

            Matcher decimal = DECIMAL.matcher(line);
            if (decimal.matches()) {
                String number = decimal.group(1);
                String[] parts = number.split("\\.");
                int depth = parts.length;
                String root = parts[0];
                if (depth == 2 && (current == null
                        || (currentNumericRoot != null && !currentNumericRoot.equals(root)))) {
                    current = result.addChapter(number + " " + clean(decimal.group(2)));
                    currentNumericRoot = root;
                } else {
                    if (current == null) {
                        current = result.addChapter("未分章");
                        currentNumericRoot = null;
                    }
                    current.sections.add(number + " " + clean(decimal.group(2)));
                }
                continue;
            }

            Matcher numbered = NUMBERED.matcher(line);
            if (numbered.matches()) {
                current = result.addChapter(cleanTitle(line));
                currentNumericRoot = numbered.group(1);
                continue;
            }

            Matcher chinese = CHINESE_NUMBERED.matcher(line);
            if (chinese.matches()) {
                current = result.addChapter(cleanTitle(line));
                currentNumericRoot = null;
                continue;
            }

            Matcher bullet = BULLET.matcher(line);
            if (bullet.matches()) {
                if (current == null) {
                    current = result.addChapter("未分章");
                }
                current.sections.add(clean(bullet.group(1)));
                continue;
            }

            if (current == null) {
                current = result.addChapter(cleanTitle(line));
            } else if (line.length() >= 2) {
                current.sections.add(cleanTitle(line));
            }
        }

        for (ParsedChapter chapter : result.chapters) {
            if (chapter.sections.isEmpty()) {
                chapter.sections.add(chapter.title);
            }
        }
        return result;
    }

    private static boolean isNoise(String line) {
        String lower = line.toLowerCase();
        return line.equals("目录")
                || line.startsWith("课程目录")
                || lower.startsWith("http://")
                || lower.startsWith("https://")
                || line.matches("^[\\d\\s./\\-]+$");
    }

    private static String cleanTitle(String line) {
        return clean(line.replaceFirst("[：:]\\s*$", ""));
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim().replaceAll("^[、.\\-—:\\s]+", "");
    }

    public static class Parsed {
        public final List<ParsedChapter> chapters = new ArrayList<>();

        public ParsedChapter addChapter(String title) {
            ParsedChapter chapter = new ParsedChapter(title);
            chapters.add(chapter);
            return chapter;
        }

        public int sectionCount() {
            int count = 0;
            for (ParsedChapter chapter : chapters) {
                count += chapter.sections.size();
            }
            return count;
        }
    }

    public static class ParsedChapter {
        public final String title;
        public final List<String> sections = new ArrayList<>();

        public ParsedChapter(String title) {
            this.title = title;
        }
    }
}
