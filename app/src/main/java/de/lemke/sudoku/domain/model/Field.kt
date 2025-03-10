package de.lemke.sudoku.domain.model

class Field(
    val position: Position,
    var solution: Int,
    var value: Int? = null,
    var given: Boolean = false,
    var hint: Boolean = false,
    val notes: MutableList<Char> = mutableListOf(),
) {
    val error: Boolean
        get() = value != null && value != solution
    val correct: Boolean
        get() = value != null && value == solution

    fun getInitialField() = Field(
        position = position,
        solution = solution,
        value = if (given) value else null,
        notes = mutableListOf(),
        given = given,
        hint = false,
    )

    fun toggleNote(note: Int?): Boolean {
        val noteChar = note.toSudokuChar() ?: return false
        if (!notes.remove(noteChar)) {
            notes.add(noteChar)
            notes.sort()
            return true
        }
        return false
    }

    fun removeNote(note: Int?) = notes.remove(note.toSudokuChar())

    fun setHint() {
        hint = true
        value = solution
    }

    fun copy(
        position: Position = this.position,
        solution: Int = this.solution,
        value: Int? = this.value,
        notes: MutableList<Char> = this.notes.toMutableList(),
        given: Boolean = this.given,
        hint: Boolean = this.hint,
    ): Field = Field(
        position = position,
        solution = solution,
        value = value,
        given = given,
        hint = hint,
        notes = notes
    )

    fun reset() {
        if (!given) value = null
        hint = false
        notes.clear()
    }
}