package com.churchdrive.app.ui

/**
 * Steps inside a to-do task, kept as tick lines at the end of the task's notes, the same lines the dashboard's task
 * list writes (src/task-steps.js): "Do it at home\n- [x] Power-cycle the Hub\n- [ ] Power-cycle the eero".
 */
data class TaskStep(val text: String, val done: Boolean)

data class NotesAndSteps(val text: String, val steps: List<TaskStep>)

private val STEP_LINE = Regex("^\\s*[-*] \\[([ xX])\\] ?(.*)$")

// A step marker at the start of the description, a line, or after " · " (when the notes are only steps).
private val STEP_MARK = Regex("(^|\\n|· )([-*] \\[)([ xX])(\\])")

private fun cleanStep(s: String): String = s.replace(Regex("\\s*\\n\\s*"), " ").replace(" · ", " - ").trim()

/** Notes taken apart: the words, then the steps. */
fun splitSteps(notes: String?): NotesAndSteps {
    val words = mutableListOf<String>()
    val steps = mutableListOf<TaskStep>()
    for (line in notes.orEmpty().split('\n')) {
        val m = STEP_LINE.matchEntire(line)
        if (m != null) steps += TaskStep(m.groupValues[2].trim(), m.groupValues[1] != " ") else words += line
    }
    return NotesAndSteps(words.joinToString("\n").trim(), steps)
}

/** Notes and steps put back together. Blank steps are dropped. */
fun joinSteps(text: String, steps: List<TaskStep>): String {
    val lines = steps.map { TaskStep(cleanStep(it.text), it.done) }.filter { it.text.isNotEmpty() }.map { "- [${if (it.done) 'x' else ' '}] ${it.text}" }
    return (listOf(text.trim()) + lines).filter { it.isNotEmpty() }.joinToString("\n")
}

/** "2 of 5", or empty for a task without steps. */
fun stepsCount(steps: List<TaskStep>): String = if (steps.isEmpty()) "" else "${steps.count { it.done }} of ${steps.size}"

/** The whole description with step number [index] ticked or unticked, or null if there is no such step. */
fun flipStep(description: String?, index: Int): String? {
    var n = -1
    var found = false
    val out = STEP_MARK.replace(description.orEmpty()) { m ->
        n++
        if (n != index) m.value else {
            found = true
            m.groupValues[1] + m.groupValues[2] + (if (m.groupValues[3] == " ") "x" else " ") + m.groupValues[4]
        }
    }
    return if (found) out else null
}

/** A task's steps, wherever they sit in its description. */
fun stepsOf(description: String?): List<TaskStep> = splitSteps(parseTaskWords(description).notes).steps

/** The description without its step lines, for showing as a note. */
fun withoutSteps(description: String?): String =
    description.orEmpty().split('\n').map { it.replace(Regex("(^| · )[-*] \\[[ xX]\\] ?.*$"), "") }.filter { it.isNotBlank() }.joinToString("\n").trim()
