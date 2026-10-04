package com.example.usagetracker.data

import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryResolverTest {
    private fun r(pkg: String, tag: String?, cat: String) = CategoryRule(packageName = pkg, contentTag = tag, category = cat)

    @Test fun fallsBackToUncategorized() {
        assertEquals(Category.UNCATEGORIZED, CategoryResolver(emptyList()).resolve("x", null))
    }

    @Test fun packageRuleAppliesToAnyTag() {
        val res = CategoryResolver(listOf(r("a", null, Category.LOW_VALUE)))
        assertEquals(Category.LOW_VALUE, res.resolve("a", null))
        assertEquals(Category.LOW_VALUE, res.resolve("a", "video"))
    }

    @Test fun tagRuleBeatsPackageRule() {
        val res = CategoryResolver(listOf(r("a", null, Category.LOW_VALUE), r("a", "video", Category.USEFUL)))
        assertEquals(Category.USEFUL, res.resolve("a", "video"))
        assertEquals(Category.LOW_VALUE, res.resolve("a", "shorts"))
    }

    @Test fun explicitUncategorizedOverridesMoreGeneralRule() {
        val res = CategoryResolver(listOf(r("a", null, Category.USEFUL), r("a", "other", Category.UNCATEGORIZED)))
        assertEquals(Category.UNCATEGORIZED, res.resolve("a", "other"))
    }

    @Test fun defaultsMatchSpec() {
        val res = CategoryResolver(DefaultCategoryRules.rules)
        val yt = DefaultCategoryRules.YOUTUBE
        assertEquals(Category.LOW_VALUE, res.resolve(yt, "shorts"))
        assertEquals(Category.USEFUL, res.resolve(yt, "video"))
        assertEquals(Category.UNCATEGORIZED, res.resolve(yt, "other"))
        assertEquals(Category.UNCATEGORIZED, res.resolve(yt, null))
        assertEquals(Category.USEFUL, res.resolve("com.google.android.gm", null))
        assertEquals(Category.LOW_VALUE, res.resolve("com.instagram.android", null))
        assertEquals(Category.UNCATEGORIZED, res.resolve("com.unknown.app", null))
    }
}
