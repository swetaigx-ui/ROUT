package com.rout.aiassistant.commands

/** Safe contact resolution: only a single plausible contact with a single number resolves. Anything else arms nothing. */
object ContactResolver {
    data class Row(val name: String, val number: String, val id: Long)
    sealed class Result {
        data class Unique(val name: String, val number: String, val id: Long) : Result()
        data class Ambiguous(val options: List<String>) : Result()
        object None : Result()
    }

    private fun digits(s: String) = s.filter { it.isDigit() }
    private fun norm(n: String): String { val d = digits(n); return if (d.length > 10) d.takeLast(10) else d }
    private fun fold(s: String) = s.trim().lowercase()

    fun resolve(query: String, rows: List<Row>): Result {
        val q = query.trim()
        // Explicit number: use it as given, no contact lookup needed.
        if (q.matches(Regex("^\\+?[0-9][0-9 \\-]{5,}$")) && digits(q).length >= 7) {
            val num = (if (q.startsWith("+")) "+" else "") + digits(q)
            return Result.Unique(num, num, 0L)
        }
        if (rows.isEmpty()) return Result.None
        val exact = rows.filter { fold(it.name) == fold(q) }
        val pool = if (exact.isNotEmpty()) exact else rows
        // Distinct people by contact id (fall back to name when id is 0).
        val people = pool.groupBy { if (it.id != 0L) "id${it.id}" else "n${fold(it.name)}" }
        val sameEntry = pool.map { fold(it.name) }.distinct().size == 1 && pool.map { norm(it.number) }.distinct().size == 1
        if (people.size > 1 && !sameEntry) {
            return Result.Ambiguous(people.values.map { it.first().name }.distinct().take(5))
        }
        val numbers = pool.distinctBy { norm(it.number) }
        if (numbers.size > 1) {
            return Result.Ambiguous(numbers.take(5).map { "${it.name} (${it.number})" })
        }
        val r = numbers.first()
        return Result.Unique(r.name, r.number, r.id)
    }

    fun ambiguousMessage(query: String, options: List<String>) =
        "\"$query\" ke liye ek se zyada contact mile: ${options.joinToString(", ")}. Poora naam ya number bolo - abhi kuch nahi bheja ya lagaya."
}
