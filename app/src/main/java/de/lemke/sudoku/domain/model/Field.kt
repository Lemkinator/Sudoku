package de.lemke.sudoku.domain.model

class Field(
    val position: Position,
    var value: Int? = null,
    var solution: Int? = null,
    val notes: MutableList<Int> = mutableListOf(),
    var given: Boolean = false,
    var hint: Boolean = false,
) {
    val error: Boolean
        get() = value != null && value != solution
    val correct: Boolean
        get() = value != null && value == solution

    fun getInitialField() = Field(
            position = position,
            value = if (given) value else null,
            solution = solution,
            notes = mutableListOf(),
            given = given,
            hint = false,
        )

    fun toggleNote(note: Int): Boolean {
        if (!notes.remove(note)) {
            notes.add(note)
            notes.sort()
            return true
        }
        return false
    }

    fun setHint() {
        hint = true
        value = solution
    }

    fun copy(
        position: Position = this.position,
        value: Int? = this.value,
        solution: Int? = this.solution,
        notes: MutableList<Int> = this.notes.toMutableList(),
        given: Boolean = this.given,
        hint: Boolean = this.hint,
    ): Field = Field(position, value, solution, notes, given, hint)

    fun reset() {
        if (!given) value = null
        hint = false
        notes.clear()
    }
}