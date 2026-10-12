package com.churchdrive.app

import com.churchdrive.app.ui.TaskStep
import com.churchdrive.app.ui.flipStep
import com.churchdrive.app.ui.joinSteps
import com.churchdrive.app.ui.parseTaskWords
import com.churchdrive.app.ui.splitSteps
import com.churchdrive.app.ui.stepsCount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TodoStepsTest {
    @Test
    fun notesWithStepsComeApartAndGoBackTogether() {
        val notes = "Do it at home\n- [x] Power-cycle the Hub\n- [ ] Power-cycle the eero"
        val s = splitSteps(notes)
        assertEquals("Do it at home", s.text)
        assertEquals(listOf(TaskStep("Power-cycle the Hub", true), TaskStep("Power-cycle the eero", false)), s.steps)
        assertEquals(notes, joinSteps(s.text, s.steps))
        assertEquals("1 of 2", stepsCount(s.steps))
    }

    @Test
    fun plainNotesHaveNoStepsAndBlankStepsAreDropped() {
        assertEquals(0, splitSteps("just words").steps.size)
        assertEquals("", stepsCount(emptyList()))
        assertEquals("x\n- [ ] a - b c", joinSteps("x", listOf(TaskStep("  ", false), TaskStep("a · b\nc", false))))
    }

    @Test
    fun stepsSurviveARepeatDescription() {
        val d = "Once · for Jamie · - [ ] one\n- [x] two"
        assertEquals(listOf(TaskStep("one", false), TaskStep("two", true)), splitSteps(parseTaskWords(d).notes).steps)
    }

    @Test
    fun flipStepTouchesOnlyThatStep() {
        val d = "Once · for Jamie · - [ ] one\n- [x] two\n- [ ] three"
        assertEquals("Once · for Jamie · - [x] one\n- [x] two\n- [ ] three", flipStep(d, 0))
        assertEquals("Once · for Jamie · - [ ] one\n- [ ] two\n- [ ] three", flipStep(d, 1))
        assertNull(flipStep(d, 3))
        assertEquals("notes\n- [x] a", flipStep("notes\n- [ ] a", 0))
        assertNull(flipStep("no steps", 0))
    }
}
