package com.churchdrive.app.ui

/** Which of the person's lists a sentence is about; [Mine] when it doesn't say. */
enum class AssistantList { Mine, Shared, Cleaning }

/** What Clive To Do understood. It only ever adds things or ticks them off: nothing else in the house is reachable from here. */
sealed interface AssistantTodoCommand {
    data class Add(val items: List<String>, val list: AssistantList) : AssistantTodoCommand
    data class Done(val text: String, val list: AssistantList?) : AssistantTodoCommand
    data object Unknown : AssistantTodoCommand
}

private fun filler(): Regex {
    val names = AssistantBrand.spokenNames().joinToString("|") { Regex.escape(it) }
    return Regex("^(?:(?:hey|hi|ok|okay)\\s+)?(?:$names)[,.!]?\\s*|^(?:please|can you|could you|would you|will you|i want you to|i need you to)\\s+|^(?:please\\s+)", RegexOption.IGNORE_CASE)
}
private val ADD_VERB = Regex("^(?:add|create|new|put|pop|stick|remind me to|i need to|we need)\\s+(.+)$", RegexOption.IGNORE_CASE)
private val DONE_MARK = Regex("^mark\\s+(.+?)\\s+(?:as\\s+)?(?:done|complete|completed|finished|ticked(?: off)?)$", RegexOption.IGNORE_CASE)
private val DONE_VERB = Regex("^(?:tick off|tick|check off|cross off|cross out|complete|finish|finished|done with|done|i(?:'ve| have) (?:done|finished|completed|bought|got))\\s+(.+)$", RegexOption.IGNORE_CASE)
private val LIST_TAIL = Regex("\\s+(?:to|on|onto|in|from|off)\\s+(?:the\\s+|my\\s+|our\\s+)?(shared|family|everyone'?s?|cleaning|chores?|my|to-?do|todo|priorities)?\\s*(?:to-?do\\s+)?(?:list)?\\s*$", RegexOption.IGNORE_CASE)

private fun listFor(word: String?): AssistantList? = when (word?.lowercase()?.trimEnd('s', '\'')) {
    "shared", "family", "everyone" -> AssistantList.Shared
    "cleaning", "chore" -> AssistantList.Cleaning
    "my", "to-do", "todo", "priorities" -> AssistantList.Mine
    else -> null
}

/** Pulls a trailing "to my list", "on the shared list", "off the cleaning list" off [text]; the list it names (null if none) and what is left. */
private fun withoutList(text: String): Pair<String, AssistantList?> {
    val m = LIST_TAIL.find(text) ?: return text to null
    // "add pens to list" is a list; "add talk to Gary" is not: only strip when something that names a list is there.
    val named = m.value.contains("list", true) || m.groupValues[1].isNotBlank()
    if (!named) return text to null
    return text.removeRange(m.range).trim() to listFor(m.groupValues[1])
}

private fun tidy(s: String) = s.trim().trim('.', ',', '!', '?', ' ').replaceFirstChar { it.uppercase() }

/** Understands one typed or spoken sentence. */
fun parseAssistantTodo(input: String): AssistantTodoCommand {
    var s = input.replace('\u2019', '\'').trim()
    if (s.isEmpty()) return AssistantTodoCommand.Unknown
    while (true) {
        val next = filler().replace(s, "").trim()
        if (next == s) break
        s = next
    }
    s = s.trimEnd('.', '!', '?', ' ')
    DONE_MARK.find(s)?.let { m ->
        val (what, list) = withoutList(m.groupValues[1])
        return if (what.isBlank()) AssistantTodoCommand.Unknown else AssistantTodoCommand.Done(what.trim(), list)
    }
    DONE_VERB.find(s)?.let { m ->
        val (what, list) = withoutList(m.groupValues[1])
        return if (what.isBlank()) AssistantTodoCommand.Unknown else AssistantTodoCommand.Done(what.trim(), list)
    }
    ADD_VERB.find(s)?.let { m ->
        val (what, list) = withoutList(m.groupValues[1])
        // "put the lights on" is not a to-do: put, pop and stick only add when a list is named.
        if (list == null && s.split(' ').first().lowercase() in setOf("put", "pop", "stick") && !LIST_TAIL.containsMatchIn(m.groupValues[1])) return AssistantTodoCommand.Unknown
        val parts = if (what.contains(',')) what.split(Regex(",\\s*(?:and\\s+)?|\\s+and\\s+")).map(::tidy).filter { it.isNotBlank() } else listOf(tidy(what))
        return if (parts.isEmpty() || parts.all { it.isBlank() }) AssistantTodoCommand.Unknown else AssistantTodoCommand.Add(parts, list ?: AssistantList.Mine)
    }
    return AssistantTodoCommand.Unknown
}

private val SMALL_WORDS = setOf("the", "a", "an", "my", "our", "to", "of", "and", "some", "for", "up", "on")
private fun tokens(s: String) = s.lowercase().split(Regex("[^a-z0-9']+")).filter { it.isNotBlank() && it !in SMALL_WORDS }.map { it.trimEnd('s') }

/** How the open tasks matched a spoken name: one clear winner, several equal ones, or none. */
sealed interface AssistantMatch {
    data class One(val item: TodoItem) : AssistantMatch
    data class Several(val items: List<TodoItem>) : AssistantMatch
    data object None : AssistantMatch
}

/** Finds the open task [query] means: every word said must be in it. More than one fits: they are all returned so the person can say which. */
fun matchOpenTask(items: List<TodoItem>, query: String): AssistantMatch {
    val want = tokens(query)
    if (want.isEmpty()) return AssistantMatch.None
    val open = items.filter { !it.done }
    val exact = open.filter { it.summary.trim().equals(query.trim(), true) }
    if (exact.size == 1) return AssistantMatch.One(exact[0])
    val hits = open.filter { t -> val have = tokens(t.summary); want.all { w -> have.any { it == w || it.startsWith(w) || w.startsWith(it) && it.length > 2 } } }
    if (hits.isEmpty()) return AssistantMatch.None
    if (hits.size == 1) return AssistantMatch.One(hits[0])
    return AssistantMatch.Several(hits)
}
