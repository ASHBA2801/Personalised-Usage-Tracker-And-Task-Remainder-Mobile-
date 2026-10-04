package com.example.usagetracker.data

/** Starter rules, inserted once. Anything not listed falls back to UNCATEGORIZED. */
object DefaultCategoryRules {
    const val YOUTUBE = "com.google.android.youtube"

    private val useful = listOf(
        // Google Workspace
        "com.google.android.apps.docs.editors.docs", "com.google.android.apps.docs.editors.sheets",
        "com.google.android.apps.docs.editors.slides", "com.google.android.apps.docs",
        "com.google.android.gm", "com.google.android.calendar", "com.google.android.keep",
        "com.google.android.apps.tasks",
        // Other calendar / mail / office / notes
        "com.samsung.android.calendar", "com.microsoft.office.outlook", "com.microsoft.office.word",
        "com.microsoft.office.excel", "com.microsoft.office.powerpoint", "notion.id", "com.Slack",
        // Mobile IDEs / terminals
        "com.termux", "com.foxdebug.acode", "com.aide.ui",
    )

    private val lowValue = listOf(
        "com.instagram.android", "com.zhiliaoapp.musically", "com.ss.android.ugc.trill",
        "com.snapchat.android", "com.facebook.katana", "com.twitter.android", "com.reddit.frontpage",
    )

    val rules: List<CategoryRule> =
        listOf(
            CategoryRule(packageName = YOUTUBE, contentTag = "shorts", category = Category.LOW_VALUE),
            CategoryRule(packageName = YOUTUBE, contentTag = "video", category = Category.USEFUL),
            CategoryRule(packageName = YOUTUBE, contentTag = "other", category = Category.UNCATEGORIZED),
        ) + useful.map { CategoryRule(packageName = it, category = Category.USEFUL) } +
            lowValue.map { CategoryRule(packageName = it, category = Category.LOW_VALUE) }
}
