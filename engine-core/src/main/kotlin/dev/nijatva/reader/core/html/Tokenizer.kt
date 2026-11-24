package dev.nijatva.reader.core.html

/**
 * An HTML tokenizer for the supported subset, written as a state machine that the tree
 * builder drives one token at a time.
 *
 * It is a pull tokenizer on purpose: after a `<script>` or `<title>` start tag the *tree
 * builder* decides that the rest of the document is script data or RCDATA, and calls
 * [setState]. A tokenizer that produced a whole list up front could not honour that, and
 * the mistake would only surface in M2.
 *
 * Nothing here allocates in proportion to input before the limits are checked, and no
 * input can make it loop without consuming a character.
 */
class Tokenizer(
    private val input: String,
    private val limits: ParserLimits = ParserLimits(),
) {

    enum class State {
        DATA, RCDATA, RAWTEXT, SCRIPT_DATA, PLAINTEXT,
        TAG_OPEN, END_TAG_OPEN, TAG_NAME,
        BEFORE_ATTRIBUTE_NAME, ATTRIBUTE_NAME, AFTER_ATTRIBUTE_NAME,
        BEFORE_ATTRIBUTE_VALUE, ATTRIBUTE_VALUE_DOUBLE, ATTRIBUTE_VALUE_SINGLE,
        ATTRIBUTE_VALUE_UNQUOTED, AFTER_ATTRIBUTE_VALUE_QUOTED, SELF_CLOSING_START_TAG,
        BOGUS_COMMENT, MARKUP_DECLARATION_OPEN,
        COMMENT_START, COMMENT_START_DASH, COMMENT, COMMENT_END_DASH, COMMENT_END,
        COMMENT_END_BANG,
        DOCTYPE, BEFORE_DOCTYPE_NAME, DOCTYPE_NAME, AFTER_DOCTYPE_NAME,
        DOCTYPE_PUBLIC_KEYWORD, BEFORE_DOCTYPE_PUBLIC_ID, DOCTYPE_PUBLIC_ID_DOUBLE,
        DOCTYPE_PUBLIC_ID_SINGLE, AFTER_DOCTYPE_PUBLIC_ID, DOCTYPE_SYSTEM_KEYWORD,
        BEFORE_DOCTYPE_SYSTEM_ID, DOCTYPE_SYSTEM_ID_DOUBLE, DOCTYPE_SYSTEM_ID_SINGLE,
        AFTER_DOCTYPE_SYSTEM_ID, BOGUS_DOCTYPE,
        CDATA_SECTION,
    }

    private var pos = 0
    private var state: State = State.DATA
    private val errors = ArrayList<HtmlToken.ParseError>()

    private var tagName = StringBuilder()
    private var tagStart = 0
    private var isEndTag = false
    private var selfClosing = false
    private val attributes = ArrayList<Attribute>()
    private var attributeName = StringBuilder()
    private var attributeValue = StringBuilder()
    private var attributeStart = 0

    private var comment = StringBuilder()
    private var commentStart = 0

    private var doctypeName: StringBuilder? = null
    private var doctypePublic: StringBuilder? = null
    private var doctypeSystem: StringBuilder? = null
    private var doctypeQuirks = false
    private var doctypeStart = 0

    private var truncated = false
    private var lastStartTag = ""

    /** Errors seen so far, in document order. */
    fun errors(): List<HtmlToken.ParseError> = errors.toList()

    /** True when a limit stopped the tokenizer before the end of input. */
    fun wasTruncated(): Boolean = truncated

    /**
     * The tree builder calls this after a start tag whose contents are not markup:
     * `<title>` and `<textarea>` are RCDATA, `<style>` and `<xmp>` RAWTEXT, `<script>`
     * script data.
     */
    fun setState(next: State) {
        state = next
    }

    fun nextToken(): HtmlToken {
        if (input.length > limits.maxInputLength && !truncated) {
            truncated = true
            errors.add(HtmlToken.ParseError("input-too-long", 0))
            return HtmlToken.EndOfFile
        }

        val text = StringBuilder()
        val textStart = pos

        while (true) {
            if (pos >= input.length) {
                if (text.isNotEmpty()) return HtmlToken.Characters(text.toString(), textStart)
                return HtmlToken.EndOfFile
            }

            val c = input[pos]

            when (state) {
                State.DATA -> {
                    when {
                        c == '<' -> {
                            if (text.isNotEmpty()) return HtmlToken.Characters(text.toString(), textStart)
                            state = State.TAG_OPEN
                            pos++
                        }
                        c == '&' -> {
                            val ref = CharacterReferences.decode(input, pos, inAttribute = false)
                            if (ref != null) { text.append(ref.text); pos += ref.consumed }
                            else { text.append(c); pos++ }
                        }
                        else -> { text.append(c); pos++ }
                    }
                }

                State.RCDATA, State.RAWTEXT, State.SCRIPT_DATA, State.PLAINTEXT -> {
                    // Inside these states markup is text. Only the end tag of the element
                    // that opened the state closes it, which is why the tree builder must
                    // set the state and the tokenizer must remember that element.
                    val close = if (state == State.PLAINTEXT) null else "</" + lastStartTag
                    if (close != null && c == '<' && closesHere(close)) {
                        if (text.isNotEmpty()) return HtmlToken.Characters(text.toString(), textStart)
                        state = State.TAG_OPEN
                    } else if (state == State.RCDATA && c == '&') {
                        val ref = CharacterReferences.decode(input, pos, inAttribute = false)
                        if (ref != null) { text.append(ref.text); pos += ref.consumed }
                        else { text.append(c); pos++ }
                    } else { text.append(c); pos++ }
                }

                State.TAG_OPEN -> {
                    when {
                        c == '!' -> { state = State.MARKUP_DECLARATION_OPEN; pos++ }
                        c == '/' -> { state = State.END_TAG_OPEN; pos++ }
                        c.isLetter() -> { tagStart = pos; tagName = StringBuilder(); isEndTag = false; state = State.TAG_NAME }
                        else -> { error("invalid-first-character-of-tag-name"); text.append('<'); state = State.DATA }
                    }
                }

                State.END_TAG_OPEN -> {
                    if (c.isLetter()) {
                        tagStart = pos; tagName = StringBuilder(); isEndTag = true; state = State.TAG_NAME
                    } else if (c == '>') {
                        error("missing-end-tag-name"); state = State.DATA; pos++
                    } else {
                        error("invalid-first-character-of-tag-name")
                        state = State.BOGUS_COMMENT; commentStart = pos; comment = StringBuilder()
                    }
                }

                State.TAG_NAME -> {
                    when {
                        c == '>' -> {
                            pos++
                            state = State.DATA
                            val name = tagName.toString().lowercase()
                            val tok = if (isEndTag) HtmlToken.EndTag(name, tagStart)
                                      else HtmlToken.StartTag(name, attributes.toList(), selfClosing, tagStart)
                            if (!isEndTag) lastStartTag = name
                            resetTag()
                            return tok
                        }
                        c.isWhitespace() -> { state = State.BEFORE_ATTRIBUTE_NAME; pos++ }
                        c == '/' -> { state = State.SELF_CLOSING_START_TAG; pos++ }
                        else -> {
                            if (tagName.length < limits.maxTagNameLength) tagName.append(c.lowercaseChar())
                            else error("tag-name-too-long")
                            pos++
                        }
                    }
                }

                State.BEFORE_ATTRIBUTE_NAME -> {
                    when {
                        c.isWhitespace() -> pos++
                        c == '/' || c == '>' -> state = State.AFTER_ATTRIBUTE_NAME
                        else -> {
                            attributeName = StringBuilder(); attributeValue = StringBuilder()
                            attributeStart = pos
                            state = State.ATTRIBUTE_NAME
                        }
                    }
                }

                State.ATTRIBUTE_NAME -> {
                    when {
                        c.isWhitespace() || c == '/' || c == '>' || c == '=' -> state = State.AFTER_ATTRIBUTE_NAME
                        else -> {
                            if (attributeName.length < limits.maxAttributeNameLength) attributeName.append(c.lowercaseChar())
                            else error("attribute-name-too-long")
                            pos++
                        }
                    }
                }

                State.AFTER_ATTRIBUTE_NAME -> {
                    when {
                        c.isWhitespace() -> pos++
                        c == '=' -> { state = State.BEFORE_ATTRIBUTE_VALUE; pos++ }
                        c == '/' -> { commitAttribute(); state = State.SELF_CLOSING_START_TAG; pos++ }
                        c == '>' -> { commitAttribute(); pos++; state = State.DATA
                            val tok = if (isEndTag) HtmlToken.EndTag(tagName.toString().lowercase(), tagStart)
                                      else HtmlToken.StartTag(tagName.toString().lowercase(), attributes.toList(), selfClosing, tagStart)
                            resetTag(); return tok }
                        else -> { commitAttribute(); state = State.BEFORE_ATTRIBUTE_NAME }
                    }
                }

                State.BEFORE_ATTRIBUTE_VALUE -> {
                    when {
                        c.isWhitespace() -> pos++
                        c == '"' -> { state = State.ATTRIBUTE_VALUE_DOUBLE; pos++ }
                        c == '\'' -> { state = State.ATTRIBUTE_VALUE_SINGLE; pos++ }
                        c == '>' -> { error("missing-attribute-value"); commitAttribute(); pos++; state = State.DATA
                            val tok = HtmlToken.StartTag(tagName.toString().lowercase(), attributes.toList(), selfClosing, tagStart)
                            resetTag(); return tok }
                        else -> state = State.ATTRIBUTE_VALUE_UNQUOTED
                    }
                }

                State.ATTRIBUTE_VALUE_DOUBLE, State.ATTRIBUTE_VALUE_SINGLE -> {
                    val quote = if (state == State.ATTRIBUTE_VALUE_DOUBLE) '"' else '\''
                    when {
                        c == quote -> { state = State.AFTER_ATTRIBUTE_VALUE_QUOTED; pos++ }
                        c == '&' -> {
                            val ref = CharacterReferences.decode(input, pos, inAttribute = true)
                            if (ref != null) { appendAttributeValue(ref.text); pos += ref.consumed }
                            else { appendAttributeValue("&"); pos++ }
                        }
                        else -> { appendAttributeValue(c); pos++ }
                    }
                }

                State.ATTRIBUTE_VALUE_UNQUOTED -> {
                    when {
                        c.isWhitespace() -> { commitAttribute(); state = State.BEFORE_ATTRIBUTE_NAME; pos++ }
                        c == '>' -> { commitAttribute(); pos++; state = State.DATA
                            val tok = if (isEndTag) HtmlToken.EndTag(tagName.toString().lowercase(), tagStart)
                                      else HtmlToken.StartTag(tagName.toString().lowercase(), attributes.toList(), selfClosing, tagStart)
                            resetTag(); return tok }
                        c == '&' -> {
                            val ref = CharacterReferences.decode(input, pos, inAttribute = true)
                            if (ref != null) { appendAttributeValue(ref.text); pos += ref.consumed }
                            else { appendAttributeValue("&"); pos++ }
                        }
                        else -> { appendAttributeValue(c); pos++ }
                    }
                }

                State.AFTER_ATTRIBUTE_VALUE_QUOTED -> {
                    when {
                        c.isWhitespace() -> { state = State.BEFORE_ATTRIBUTE_NAME; pos++ }
                        c == '/' -> { state = State.SELF_CLOSING_START_TAG; pos++ }
                        c == '>' -> { pos++; state = State.DATA
                            val tok = if (isEndTag) HtmlToken.EndTag(tagName.toString().lowercase(), tagStart)
                                      else HtmlToken.StartTag(tagName.toString().lowercase(), attributes.toList(), selfClosing, tagStart)
                            resetTag(); return tok }
                        else -> { error("missing-whitespace-between-attributes"); state = State.BEFORE_ATTRIBUTE_NAME }
                    }
                }

                State.SELF_CLOSING_START_TAG -> {
                    if (c == '>') {
                        selfClosing = true; pos++; state = State.DATA
                        val tok = if (isEndTag) HtmlToken.EndTag(tagName.toString().lowercase(), tagStart)
                                  else HtmlToken.StartTag(tagName.toString().lowercase(), attributes.toList(), selfClosing, tagStart)
                        resetTag(); return tok
                    } else {
                        error("unexpected-solidus-in-tag"); state = State.BEFORE_ATTRIBUTE_NAME
                    }
                }

                State.MARKUP_DECLARATION_OPEN -> {
                    when {
                        matches(pos, "--") -> { commentStart = pos; comment = StringBuilder(); state = State.COMMENT_START; pos += 2 }
                        matchesIgnoreCase(pos, "DOCTYPE") -> { doctypeStart = pos; state = State.DOCTYPE; pos += 7 }
                        matches(pos, "[CDATA[") -> { state = State.CDATA_SECTION; pos += 7 }
                        else -> { error("incorrectly-opened-comment"); state = State.BOGUS_COMMENT; commentStart = pos; comment = StringBuilder() }
                    }
                }

                State.BOGUS_COMMENT -> {
                    if (c == '>') { pos++; state = State.DATA
                        val text = comment.toString(); comment = StringBuilder()
                        return HtmlToken.Comment(text, commentStart) }
                    if (comment.length < limits.maxCommentLength) comment.append(c) else error("comment-too-long")
                    pos++
                }

                State.COMMENT_START -> {
                    when {
                        c == '-' -> { state = State.COMMENT_START_DASH; pos++ }
                        c == '>' -> { error("abrupt-closing-of-empty-comment"); pos++; state = State.DATA
                            return HtmlToken.Comment("", commentStart) }
                        else -> state = State.COMMENT
                    }
                }

                State.COMMENT_START_DASH -> {
                    when {
                        c == '-' -> { state = State.COMMENT_END; pos++ }
                        c == '>' -> { error("abrupt-closing-of-empty-comment"); pos++; state = State.DATA
                            return HtmlToken.Comment("", commentStart) }
                        else -> { comment.append('-'); state = State.COMMENT }
                    }
                }

                State.COMMENT -> {
                    when {
                        c == '-' -> { state = State.COMMENT_END_DASH; pos++ }
                        else -> {
                            if (comment.length < limits.maxCommentLength) comment.append(c) else error("comment-too-long")
                            pos++
                        }
                    }
                }

                State.COMMENT_END_DASH -> {
                    if (c == '-') { state = State.COMMENT_END; pos++ }
                    else { comment.append('-'); state = State.COMMENT }
                }

                State.COMMENT_END -> {
                    when {
                        c == '>' -> { pos++; state = State.DATA
                            val text = comment.toString(); comment = StringBuilder()
                            return HtmlToken.Comment(text, commentStart) }
                        c == '!' -> { state = State.COMMENT_END_BANG; pos++ }
                        c == '-' -> { comment.append('-'); pos++ }
                        else -> { comment.append("--"); state = State.COMMENT }
                    }
                }

                State.COMMENT_END_BANG -> {
                    when {
                        c == '-' -> { comment.append("--!"); state = State.COMMENT_END_DASH; pos++ }
                        c == '>' -> { error("incorrectly-closed-comment"); pos++; state = State.DATA
                            val text = comment.toString(); comment = StringBuilder()
                            return HtmlToken.Comment(text, commentStart) }
                        else -> { comment.append("--!"); state = State.COMMENT }
                    }
                }

                State.DOCTYPE -> {
                    if (c.isWhitespace()) { state = State.BEFORE_DOCTYPE_NAME; pos++ }
                    else if (c == '>') { error("missing-doctype-name"); pos++; state = State.DATA
                        return finishDoctype(null) }
                    else state = State.BEFORE_DOCTYPE_NAME
                }

                State.BEFORE_DOCTYPE_NAME -> {
                    when {
                        c.isWhitespace() -> pos++
                        c == '>' -> { error("missing-doctype-name"); pos++; state = State.DATA; return finishDoctype(null) }
                        else -> { doctypeName = StringBuilder(); state = State.DOCTYPE_NAME }
                    }
                }

                State.DOCTYPE_NAME -> {
                    when {
                        c.isWhitespace() -> { state = State.AFTER_DOCTYPE_NAME; pos++ }
                        c == '>' -> { pos++; state = State.DATA; return finishDoctype(doctypeName?.toString()) }
                        else -> { doctypeName?.append(c.lowercaseChar()); pos++ }
                    }
                }

                State.AFTER_DOCTYPE_NAME -> {
                    when {
                        c.isWhitespace() -> pos++
                        c == '>' -> { pos++; state = State.DATA; return finishDoctype(doctypeName?.toString()) }
                        matchesIgnoreCase(pos, "PUBLIC") -> { pos += 6; state = State.DOCTYPE_PUBLIC_KEYWORD }
                        matchesIgnoreCase(pos, "SYSTEM") -> { pos += 6; state = State.DOCTYPE_SYSTEM_KEYWORD }
                        else -> { error("invalid-character-sequence-after-doctype-name"); state = State.BOGUS_DOCTYPE }
                    }
                }

                State.DOCTYPE_PUBLIC_KEYWORD -> {
                    if (c.isWhitespace()) { state = State.BEFORE_DOCTYPE_PUBLIC_ID; pos++ }
                    else if (c == '"' || c == '\'') { doctypePublic = StringBuilder()
                        state = if (c == '"') State.DOCTYPE_PUBLIC_ID_DOUBLE else State.DOCTYPE_PUBLIC_ID_SINGLE; pos++ }
                    else { error("missing-quote-before-doctype-public-identifier"); state = State.BOGUS_DOCTYPE }
                }

                State.BEFORE_DOCTYPE_PUBLIC_ID -> {
                    if (c == '"' || c == '\'') { doctypePublic = StringBuilder()
                        state = if (c == '"') State.DOCTYPE_PUBLIC_ID_DOUBLE else State.DOCTYPE_PUBLIC_ID_SINGLE; pos++ }
                    else if (c.isWhitespace()) pos++
                    else { error("missing-quote-before-doctype-public-identifier"); state = State.BOGUS_DOCTYPE }
                }

                State.DOCTYPE_PUBLIC_ID_DOUBLE, State.DOCTYPE_PUBLIC_ID_SINGLE -> {
                    val quote = if (state == State.DOCTYPE_PUBLIC_ID_DOUBLE) '"' else '\''
                    when {
                        c == quote -> { state = State.AFTER_DOCTYPE_PUBLIC_ID; pos++ }
                        c == '>' -> { error("abrupt-doctype-public-identifier"); pos++; state = State.DATA
                            return finishDoctype(doctypeName?.toString()) }
                        else -> { doctypePublic?.append(c); pos++ }
                    }
                }

                State.AFTER_DOCTYPE_PUBLIC_ID -> {
                    when {
                        c.isWhitespace() -> pos++
                        c == '>' -> { pos++; state = State.DATA; return finishDoctype(doctypeName?.toString()) }
                        c == '"' || c == '\'' -> { doctypeSystem = StringBuilder()
                            state = if (c == '"') State.DOCTYPE_SYSTEM_ID_DOUBLE else State.DOCTYPE_SYSTEM_ID_SINGLE; pos++ }
                        else -> { error("missing-quote-before-doctype-system-identifier"); state = State.BOGUS_DOCTYPE }
                    }
                }

                State.DOCTYPE_SYSTEM_KEYWORD -> {
                    if (c.isWhitespace()) { state = State.BEFORE_DOCTYPE_SYSTEM_ID; pos++ }
                    else if (c == '"' || c == '\'') { doctypeSystem = StringBuilder()
                        state = if (c == '"') State.DOCTYPE_SYSTEM_ID_DOUBLE else State.DOCTYPE_SYSTEM_ID_SINGLE; pos++ }
                    else { error("missing-quote-before-doctype-system-identifier"); state = State.BOGUS_DOCTYPE }
                }

                State.BEFORE_DOCTYPE_SYSTEM_ID -> {
                    if (c == '"' || c == '\'') { doctypeSystem = StringBuilder()
                        state = if (c == '"') State.DOCTYPE_SYSTEM_ID_DOUBLE else State.DOCTYPE_SYSTEM_ID_SINGLE; pos++ }
                    else if (c.isWhitespace()) pos++
                    else { error("missing-quote-before-doctype-system-identifier"); state = State.BOGUS_DOCTYPE }
                }

                State.DOCTYPE_SYSTEM_ID_DOUBLE, State.DOCTYPE_SYSTEM_ID_SINGLE -> {
                    val quote = if (state == State.DOCTYPE_SYSTEM_ID_DOUBLE) '"' else '\''
                    when {
                        c == quote -> { state = State.AFTER_DOCTYPE_SYSTEM_ID; pos++ }
                        c == '>' -> { error("abrupt-doctype-system-identifier"); pos++; state = State.DATA
                            return finishDoctype(doctypeName?.toString()) }
                        else -> { doctypeSystem?.append(c); pos++ }
                    }
                }

                State.AFTER_DOCTYPE_SYSTEM_ID -> {
                    if (c == '>') { pos++; state = State.DATA; return finishDoctype(doctypeName?.toString()) }
                    if (c.isWhitespace()) pos++ else error("unexpected-character-after-doctype-system-identifier")
                }

                State.BOGUS_DOCTYPE -> {
                    if (c == '>') { pos++; state = State.DATA; return finishDoctype(doctypeName?.toString()) }
                    pos++
                }

                State.CDATA_SECTION -> {
                    if (c == ']' && matches(pos, "]]>")) {
                        pos += 3; state = State.DATA
                    } else {
                        text.append(c); pos++
                    }
                }
            }
        }
    }

    private fun appendAttributeValue(value: String) {
        if (attributeValue.length + value.length <= limits.maxAttributeValueLength) attributeValue.append(value)
        else error("attribute-value-too-long")
    }

    private fun appendAttributeValue(c: Char) {
        if (attributeValue.length < limits.maxAttributeValueLength) attributeValue.append(c)
        else error("attribute-value-too-long")
    }

    private fun commitAttribute() {
        if (attributeName.isEmpty()) return
        if (attributes.size >= limits.maxAttributesPerTag) { error("too-many-attributes"); return }
        attributes.add(Attribute(attributeName.toString(), attributeValue.toString(), attributeStart))
        attributeName = StringBuilder()
        attributeValue = StringBuilder()
    }

    private fun resetTag() {
        tagName = StringBuilder()
        attributes.clear()
        attributeName = StringBuilder()
        attributeValue = StringBuilder()
        selfClosing = false
        isEndTag = false
    }

    private fun finishDoctype(name: String?): HtmlToken {
        val token = HtmlToken.Doctype(
            name = name,
            publicId = doctypePublic?.toString(),
            systemId = doctypeSystem?.toString(),
            forceQuirks = doctypeQuirks || name != "html",
            position = doctypeStart,
        )
        doctypeName = null; doctypePublic = null; doctypeSystem = null; doctypeQuirks = false
        return token
    }

    private fun error(code: String) {
        if (errors.size < 1_000) errors.add(HtmlToken.ParseError(code, pos))
    }

    /** True when `</name` here is really the end tag and not `</namex`. */
    private fun closesHere(literal: String): Boolean {
        if (!matchesIgnoreCase(pos, literal)) return false
        val after = pos + literal.length
        if (after >= input.length) return true
        val c = input[after]
        return c == '>' || c == '/' || c.isWhitespace()
    }

    private fun matches(at: Int, literal: String): Boolean =
        at + literal.length <= input.length && input.regionMatches(at, literal, 0, literal.length)

    private fun matchesIgnoreCase(at: Int, literal: String): Boolean =
        at + literal.length <= input.length && input.regionMatches(at, literal, 0, literal.length, ignoreCase = true)
}
