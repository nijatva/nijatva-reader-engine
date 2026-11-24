package dev.nijatva.reader.core.html

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Seeded fuzzing. The seed is fixed so a failure reproduces exactly; the count can be
 * raised with -Dfuzz.count, and the default is the number the milestone asks for.
 *
 * What is asserted is not "it parses" but "it terminates, does not throw, and does not
 * allocate without bound" — the three ways a tokenizer of untrusted input fails badly.
 */
class FuzzTest {

    private val seed = 0x5EED1234
    private val count = System.getProperty("fuzz.count")?.toIntOrNull() ?: 200_000
    private val maxLength = 4096

    // Weighted toward the characters that drive the state machine.
    private val alphabet = (
        "<>&\"'=/!-[]".repeat(30) +
            "abcdefghijklmnopqrstuvwxyz".repeat(6) +
            "ABCDEFGHIJKLMNOPQRSTUVWXYZ".repeat(3) +
            "0123456789".repeat(4) +
            " \t\n\r".repeat(8) +
            "\u0000\u00A0\u200B\u200D\u20AC\u0930\u0915\u0BCD\uD83D\uDE00"
        ).toCharArray()

    @Test fun fuzzedInputNeverThrowsAndAlwaysTerminates() {
        val rnd = Random(seed)
        var worstTokens = 0

        repeat(count) { index ->
            val length = rnd.nextInt(0, maxLength)
            val sb = StringBuilder(length)
            repeat(length) { sb.append(alphabet[rnd.nextInt(alphabet.size)]) }
            val html = sb.toString()

            val tokenizer = Tokenizer(html, ParserLimits(maxInputLength = maxLength))
            var tokens = 0
            try {
                while (true) {
                    val tok = tokenizer.nextToken()
                    tokens++
                    if (tok is HtmlToken.EndOfFile) break
                    // a tokenizer that emits more tokens than characters is looping
                    assertTrue("token explosion at index $index", tokens <= html.length + 2)
                }
            } catch (e: Throwable) {
                throw AssertionError(
                    "seed=$seed index=$index length=$length failed: ${e::class.simpleName}: ${e.message}\n" +
                        "input=" + html.replace("\u0000", "\\0"), e
                )
            }
            if (tokens > worstTokens) worstTokens = tokens
        }

        println("fuzz: seed=$seed count=$count maxLength=$maxLength worstTokens=$worstTokens")
    }
}
