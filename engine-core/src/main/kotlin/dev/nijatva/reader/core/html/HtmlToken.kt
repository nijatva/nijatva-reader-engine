package dev.nijatva.reader.core.html

/**
 * What the tokenizer hands to the tree builder. One token per thing the parser cares
 * about; text is delivered in runs rather than per character so the tree builder is not
 * called once per byte of a paragraph.
 */
sealed interface HtmlToken {

    data class StartTag(
        val name: String,
        val attributes: List<Attribute>,
        val selfClosing: Boolean,
        val position: Int,
    ) : HtmlToken

    data class EndTag(val name: String, val position: Int) : HtmlToken

    data class Characters(val text: String, val position: Int) : HtmlToken

    data class Comment(val text: String, val position: Int) : HtmlToken

    data class Doctype(
        val name: String?,
        val publicId: String?,
        val systemId: String?,
        val forceQuirks: Boolean,
        val position: Int,
    ) : HtmlToken

    data class ParseError(val code: String, val position: Int) : HtmlToken

    data object EndOfFile : HtmlToken
}

/** An attribute as written. Duplicates are kept: the tree builder decides which one wins. */
data class Attribute(val name: String, val value: String, val position: Int)

/** Hard ceilings applied before any allocation proportional to input. */
data class ParserLimits(
    val maxInputLength: Int = 2_000_000,
    val maxTagNameLength: Int = 256,
    val maxAttributeNameLength: Int = 256,
    val maxAttributeValueLength: Int = 65_536,
    val maxAttributesPerTag: Int = 256,
    val maxCommentLength: Int = 65_536,
    val maxCharacterReferenceLength: Int = 32,
)
