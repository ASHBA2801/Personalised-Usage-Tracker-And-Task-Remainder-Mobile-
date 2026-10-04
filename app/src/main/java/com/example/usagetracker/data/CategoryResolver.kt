package com.example.usagetracker.data

/** Most specific rule wins: package + tag, then package-only, then UNCATEGORIZED. Pure, so it is unit tested. */
class CategoryResolver(rules: List<CategoryRule>) {
    private val specific = rules.filter { it.contentTag != null }.associate { (it.packageName to it.contentTag) to it.category }
    private val wholeApp = rules.filter { it.contentTag == null }.associate { it.packageName to it.category }

    fun resolve(packageName: String, contentTag: String?): String =
        (contentTag?.let { specific[packageName to it] }) ?: wholeApp[packageName] ?: Category.UNCATEGORIZED
}
