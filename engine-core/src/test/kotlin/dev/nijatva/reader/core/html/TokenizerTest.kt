package dev.nijatva.reader.core.html

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Focused tests per state and per error class. The tokenizer is a solved, well-specified
 * problem, so this file is about coverage of the states rather than about volume: the
 * weight of the test budget belongs to inline layout, not here.
 */
class TokenizerTest {

    private fun all(html: String): List<HtmlToken> {
        val t = Tokenizer(html)
        val out = ArrayList<HtmlToken>()
        while (true) {
            val tok = t.nextToken()
            out.add(tok)
            if (tok is HtmlToken.EndOfFile) break
        }
        return out
    }

    private fun first(html: String): HtmlToken = all(html).first()

    private fun chars(html: String): String =
        all(html).filterIsInstance<HtmlToken.Characters>().joinToString("") { it.text }

    // ---- data state -----------------------------------------------------------------

    @Test fun plainTextIsOneRun() {
        assertEquals("hello", chars("hello"))
    }

    @Test fun emptyInputIsOnlyEndOfFile() {
        assertEquals(listOf(HtmlToken.EndOfFile), all(""))
    }

    @Test fun textBeforeAndAfterATag() {
        val toks = all("a<b>c")
        assertTrue(toks[0] is HtmlToken.Characters)
        assertTrue(toks[1] is HtmlToken.StartTag)
        assertTrue(toks[2] is HtmlToken.Characters)
    }

    // ---- tag open / name ------------------------------------------------------------

    @Test fun startTagNameIsLowercased() {
        val t = first("<DIV>") as HtmlToken.StartTag
        assertEquals("div", t.name)
    }

    @Test fun endTagIsRecognised() {
        val t = all("</p>")[0] as HtmlToken.EndTag
        assertEquals("p", t.name)
    }

    @Test fun lessThanFollowedBySpaceIsText() {
        assertEquals("a < b", chars("a < b"))
    }

    @Test fun strayEndTagOpenIsAnErrorAndATextLessThan() {
        val t = Tokenizer("</>")
        val toks = ArrayList<HtmlToken>()
        while (true) { val x = t.nextToken(); toks.add(x); if (x is HtmlToken.EndOfFile) break }
        assertTrue(t.errors().any { it.code == "missing-end-tag-name" })
    }

    // ---- attributes -----------------------------------------------------------------

    @Test fun doubleQuotedAttribute() {
        val t = first("<a href=\"x\">") as HtmlToken.StartTag
        assertEquals("x", t.attributes.single().value)
    }

    @Test fun singleQuotedAttribute() {
        val t = first("<a href='x'>") as HtmlToken.StartTag
        assertEquals("x", t.attributes.single().value)
    }

    @Test fun unquotedAttribute() {
        val t = first("<a href=x>") as HtmlToken.StartTag
        assertEquals("x", t.attributes.single().value)
    }

    @Test fun attributeNamesAreLowercased() {
        val t = first("<a HREF=\"x\">") as HtmlToken.StartTag
        assertEquals("href", t.attributes.single().name)
    }

    @Test fun valuelessAttributeHasEmptyValue() {
        val t = first("<input disabled>") as HtmlToken.StartTag
        assertEquals("disabled", t.attributes.single().name)
        assertEquals("", t.attributes.single().value)
    }

    @Test fun duplicateAttributesAreBothKept() {
        val t = first("<a x=1 x=2>") as HtmlToken.StartTag
        assertEquals(2, t.attributes.size)
    }

    @Test fun selfClosingIsRecorded() {
        val t = first("<br/>") as HtmlToken.StartTag
        assertTrue(t.selfClosing)
    }

    @Test fun missingAttributeValueIsAnError() {
        val tk = Tokenizer("<a href=>")
        tk.nextToken()
        assertTrue(tk.errors().any { it.code == "missing-attribute-value" })
    }

    @Test fun missingWhitespaceBetweenAttributesIsAnError() {
        val tk = Tokenizer("<a x=\"1\"y=\"2\">")
        tk.nextToken()
        assertTrue(tk.errors().any { it.code == "missing-whitespace-between-attributes" })
    }

    // ---- character references -------------------------------------------------------

    @Test fun namedReferenceInText() {
        assertEquals("a&b", chars("a&amp;b"))
    }

    @Test fun referenceWithoutSemicolonIsDecodedInText() {
        assertEquals("a&b", chars("a&ampb"))
    }

    @Test fun unknownReferenceIsLeftAlone() {
        assertEquals("&nosuchname;", chars("&nosuchname;"))
    }

    @Test fun numericDecimalReference() {
        assertEquals("A", chars("&#65;"))
    }

    @Test fun numericHexReference() {
        assertEquals("A", chars("&#x41;"))
    }

    @Test fun nullReferenceBecomesReplacementCharacter() {
        assertEquals("\uFFFD", chars("&#0;"))
    }

    @Test fun c1ReferenceIsRemappedToWindows1252() {
        assertEquals("\u20AC", chars("&#x80;"))
    }

    @Test fun referenceInAttributeValue() {
        val t = first("<a title=\"a&amp;b\">") as HtmlToken.StartTag
        assertEquals("a&b", t.attributes.single().value)
    }

    @Test fun ambiguousAmpersandInAttributeStaysLiteral() {
        // &not would decode to ¬, but inside an attribute followed by an alphanumeric the
        // specification says leave it alone. This is the rule most implementations get
        // wrong first.
        val t = first("<a href=\"?a=1&notit=2\">") as HtmlToken.StartTag
        assertEquals("?a=1&notit=2", t.attributes.single().value)
    }

    @Test fun referenceInUnquotedAttributeValue() {
        val t = first("<a x=a&amp;b>") as HtmlToken.StartTag
        assertEquals("a&b", t.attributes.single().value)
    }

    // ---- comments -------------------------------------------------------------------

    @Test fun simpleComment() {
        val c = all("<!-- hi -->")[0] as HtmlToken.Comment
        assertEquals(" hi ", c.text)
    }

    @Test fun emptyComment() {
        val c = all("<!---->")[0] as HtmlToken.Comment
        assertEquals("", c.text)
    }

    @Test fun commentWithSingleDash() {
        val c = all("<!-- a - b -->")[0] as HtmlToken.Comment
        assertEquals(" a - b ", c.text)
    }

    @Test fun bogusCommentAfterBang() {
        val c = all("<!foo>")[0] as HtmlToken.Comment
        assertEquals("foo", c.text)
    }

    @Test fun abruptlyClosedCommentIsAnError() {
        val tk = Tokenizer("<!-->")
        tk.nextToken()
        assertTrue(tk.errors().any { it.code == "abrupt-closing-of-empty-comment" })
    }

    // ---- doctype --------------------------------------------------------------------

    @Test fun simpleDoctype() {
        val d = all("<!DOCTYPE html>")[0] as HtmlToken.Doctype
        assertEquals("html", d.name)
        assertTrue(!d.forceQuirks)
    }

    @Test fun doctypeWithPublicAndSystemId() {
        val d = all("<!DOCTYPE html PUBLIC \"-//W3C//DTD HTML 4.01//EN\" \"http://x\">")[0] as HtmlToken.Doctype
        assertEquals("-//W3C//DTD HTML 4.01//EN", d.publicId)
        assertEquals("http://x", d.systemId)
    }

    @Test fun doctypeWithSystemIdOnly() {
        val d = all("<!DOCTYPE html SYSTEM \"about:legacy-compat\">")[0] as HtmlToken.Doctype
        assertEquals("about:legacy-compat", d.systemId)
    }

    @Test fun nonHtmlDoctypeForcesQuirks() {
        val d = all("<!DOCTYPE foo>")[0] as HtmlToken.Doctype
        assertTrue(d.forceQuirks)
    }

    @Test fun missingDoctypeNameIsAnError() {
        val tk = Tokenizer("<!DOCTYPE>")
        tk.nextToken()
        assertTrue(tk.errors().any { it.code == "missing-doctype-name" })
    }

    // ---- raw text states ------------------------------------------------------------

    @Test fun scriptContentsAreText() {
        val t = Tokenizer("<script>if (a<b) x()</script>")
        assertEquals("script", (t.nextToken() as HtmlToken.StartTag).name)
        t.setState(Tokenizer.State.SCRIPT_DATA)
        val body = t.nextToken() as HtmlToken.Characters
        assertEquals("if (a<b) x()", body.text)
        val end = t.nextToken() as HtmlToken.EndTag
        assertEquals("script", end.name)
    }

    @Test fun rcdataDecodesReferencesButKeepsMarkup() {
        val t = Tokenizer("<title>a &amp; b <c></title>")
        t.nextToken()
        t.setState(Tokenizer.State.RCDATA)
        val body = t.nextToken() as HtmlToken.Characters
        assertEquals("a & b <c>", body.text)
        assertEquals("title", (t.nextToken() as HtmlToken.EndTag).name)
    }

    @Test fun rawtextDoesNotDecodeReferences() {
        val t = Tokenizer("<style>a &amp; b</style>")
        t.nextToken()
        t.setState(Tokenizer.State.RAWTEXT)
        val body = t.nextToken() as HtmlToken.Characters
        assertEquals("a &amp; b", body.text)
    }

    @Test fun anEndTagWithTheWrongNameDoesNotCloseRawText() {
        val t = Tokenizer("<script>a</scriptx>b</script>")
        t.nextToken()
        t.setState(Tokenizer.State.SCRIPT_DATA)
        val body = t.nextToken() as HtmlToken.Characters
        assertTrue(body.text.contains("</scriptx>"))
    }

    // ---- cdata ----------------------------------------------------------------------

    @Test fun cdataContentsAreText() {
        assertEquals("x", chars("<![CDATA[x]]>"))
    }

    // ---- limits ---------------------------------------------------------------------

    @Test fun longInputIsTruncatedAtTheLimit() {
        val tk = Tokenizer("a".repeat(100), ParserLimits(maxInputLength = 10))
        assertEquals(HtmlToken.EndOfFile, tk.nextToken())
        assertTrue(tk.wasTruncated())
    }

    @Test fun tooManyAttributesIsAnError() {
        val many = (1..10).joinToString(" ") { "a$it=\"$it\"" }
        val tk = Tokenizer("<p $many>", ParserLimits(maxAttributesPerTag = 3))
        val tag = tk.nextToken() as HtmlToken.StartTag
        assertEquals(3, tag.attributes.size)
        assertTrue(tk.errors().any { it.code == "too-many-attributes" })
    }

    @Test fun attributeValueCeilingIsEnforced() {
        val tk = Tokenizer("<p x=\"${"v".repeat(50)}\">", ParserLimits(maxAttributeValueLength = 10))
        val tag = tk.nextToken() as HtmlToken.StartTag
        assertEquals(10, tag.attributes.single().value.length)
    }

    @Test fun positionsPointAtTheTagStart() {
        val tag = all("ab<p>")[1] as HtmlToken.StartTag
        assertEquals(2, tag.position)
    }
}
