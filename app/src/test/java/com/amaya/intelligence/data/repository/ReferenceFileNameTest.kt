package com.amaya.intelligence.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ReferenceFileNameTest {
    @Test fun retainsChineseNamesAndMixedText() {
        assertEquals("中文项目资料 123.txt", sanitizeReferenceFileName("中文项目资料 123.txt"))
        assertEquals("Cafe\u0301 笔记.md", sanitizeReferenceFileName("Cafe\u0301 笔记.md"))
    }

    @Test fun keepsNamesInsideTheOwnerDirectory() {
        val name = sanitizeReferenceFileName("../资料\\子目录/笔记\u0000.txt")
        assertFalse(name.contains('/'))
        assertFalse(name.contains('\\'))
        assertFalse(name.any(Char::isISOControl))
        assertEquals(".._资料_子目录_笔记_.txt", name)
    }
}
