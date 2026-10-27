package com.onelineaday.dailydiary.ai.distill

import org.junit.Assert.*
import org.junit.Test

class LocalTextDistillationEngineTest {

    @Test
    fun testEmptyAndBlankInputs() {
        assertEquals("", LocalTextDistillationEngine.distillToSingleLine(""))
        assertEquals("", LocalTextDistillationEngine.distillToSingleLine("   "))
        assertEquals("", LocalTextDistillationEngine.distillToSingleLine("\n\t  \n"))
    }

    @Test
    fun testSingleSentencePreservedAndPunctuationAdded() {
        val input = "today was a wonderfully quiet and restful morning"
        val output = LocalTextDistillationEngine.distillToSingleLine(input)
        assertEquals("Today was a wonderfully quiet and restful morning.", output)
    }

    @Test
    fun testFillerWordsStripped() {
        val input = "Um, like, basically we went to the coffee shop and sort of relaxed."
        val output = LocalTextDistillationEngine.distillToSingleLine(input)
        assertFalse("Output should not contain 'basically'", output.contains("basically", ignoreCase = true))
        assertFalse("Output should not contain 'like'", output.contains("like", ignoreCase = true))
        assertTrue("Output should contain coffee shop", output.contains("coffee shop", ignoreCase = true))
    }

    @Test
    fun testMultiSentencePrefersSalientEmotionalClimax() {
        val input = """
            We woke up at 6am to heavy rain.
            Traffic was backed up across the highway for two hours.
            Work was busy and stressful with back-to-back meetings.
            Finally walked into the living room and felt deeply grateful to be home with family.
        """.trimIndent()

        val output = LocalTextDistillationEngine.distillToSingleLine(input)
        assertTrue(
            "Distilled line should choose the grateful climax rather than morning traffic: got '$output'",
            output.contains("grateful", ignoreCase = true)
        )
    }

    @Test
    fun testStrictNaturalWordBoundaryTruncation() {
        val veryLongSentence = "This is an extraordinarily verbose narrative describing each minute detail of the morning walk through the botanical gardens where every single flower bloomed with breathtaking vibrancy and the sun beamed through the ancient canopy of towering pine trees."
        val maxLen = 60
        val output = LocalTextDistillationEngine.distillToSingleLine(veryLongSentence, maxLength = maxLen)

        assertTrue("Output length (${output.length}) must be <= maxLen ($maxLen)", output.length <= maxLen)
        assertTrue("Output must end with ellipsis", output.endsWith("…"))
        assertFalse("Should not cut a word in half before ellipsis", output.endsWith(" flo…"))
    }
}

