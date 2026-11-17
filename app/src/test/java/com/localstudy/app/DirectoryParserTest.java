package com.localstudy.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.localstudy.app.util.DirectoryParser;

import org.junit.Test;

public class DirectoryParserTest {
    @Test
    public void parsesChineseChaptersAndDecimalSections() {
        String input = "第1章 操作系统概述\n"
                + "1.1 进程与线程\n"
                + "1.2 内存管理\n"
                + "第2章 文件系统\n"
                + "2.1 文件目录\n";

        DirectoryParser.Parsed parsed = DirectoryParser.parse(input);

        assertEquals(2, parsed.chapters.size());
        assertEquals(2, parsed.chapters.get(0).sections.size());
        assertEquals(1, parsed.chapters.get(1).sections.size());
        assertTrue(parsed.chapters.get(0).title.contains("操作系统"));
    }

    @Test
    public void keepsDeepNumberingAsSection() {
        String input = "1. 计算机网络\n"
                + "1.1 基础\n"
                + "1.1.1 TCP\n"
                + "1.1.2 UDP\n";

        DirectoryParser.Parsed parsed = DirectoryParser.parse(input);

        assertEquals(1, parsed.chapters.size());
        assertEquals(3, parsed.sectionCount());
    }

    @Test
    public void unnumberedLinesBecomeSectionsUnderCurrentChapter() {
        String input = "第1章 数据结构\n"
                + "数组\n"
                + "链表\n";

        DirectoryParser.Parsed parsed = DirectoryParser.parse(input);

        assertEquals(2, parsed.chapters.get(0).sections.size());
    }
}
