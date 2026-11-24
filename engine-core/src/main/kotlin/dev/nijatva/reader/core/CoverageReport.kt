package dev.nijatva.reader.core

/**
 * What the engine could not do with a document.
 *
 * Every unsupported construct is counted here rather than dropped in silence, and the
 * host reads this to decide whether to render the document with its own engine. The
 * engine never makes that decision itself.
 */
data class CoverageReport(
    val unsupportedHtml: Map<String, Int> = emptyMap(),
    val unsupportedCss: Map<String, Int> = emptyMap(),
    val ignoredNodes: Int = 0,
    val imagesDenied: Int = 0,
) {
    val isEmpty: Boolean
        get() = unsupportedHtml.isEmpty() && unsupportedCss.isEmpty() &&
            ignoredNodes == 0 && imagesDenied == 0

    fun merge(other: CoverageReport): CoverageReport = CoverageReport(
        unsupportedHtml = add(unsupportedHtml, other.unsupportedHtml),
        unsupportedCss = add(unsupportedCss, other.unsupportedCss),
        ignoredNodes = ignoredNodes + other.ignoredNodes,
        imagesDenied = imagesDenied + other.imagesDenied,
    )

    private fun add(a: Map<String, Int>, b: Map<String, Int>): Map<String, Int> {
        val out = a.toMutableMap()
        for ((k, v) in b) out[k] = (out[k] ?: 0) + v
        return out
    }
}
