package de.lemke.sudoku.domain.model

data class Field(
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

    fun clone(
        position: Position = this.position,
        value: Int? = this.value,
        solution: Int? = this.solution,
        notes: MutableList<Int> = ArrayList(this.notes),
        preNumber: Boolean = this.given,
        hint: Boolean = this.hint,
    ): Field = Field(position, value, solution, notes, preNumber, hint)

    fun reset() {
        if (!given) value = null
        hint = false
        notes.clear()
    }
}