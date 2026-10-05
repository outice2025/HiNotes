package com.hinotes.app.ui.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * Editor text state with an undo/redo history.
 *
 * Every mutation funnels through [push]/[applyEdit] so the history stays consistent, and
 * [markDirtyExternally] lets a debounced snapshot record typing without flooding the stack.
 */
class EditorState(initial: TextFieldValue = TextFieldValue()) {

    var value by mutableStateOf(initial)
        private set

    private val undoStack = ArrayDeque<Snapshot>()
    private val redoStack = ArrayDeque<Snapshot>()

    /** True while the latest edit has not yet been folded into the undo history. */
    private var pendingSnapshot: Snapshot? = null

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    data class Snapshot(val text: String, val selection: TextRange)

    /** Direct typing: records a coalesced snapshot so undo steps back over a burst of keys. */
    fun onUserInput(new: TextFieldValue) {
        if (new.text != value.text) {
            pendingSnapshot = Snapshot(value.text, value.selection)
            redoStack.clear()
        }
        value = new
    }

    /** Commits any pending typing snapshot. Call when a burst of edits settles. */
    fun commitPending() {
        val pending = pendingSnapshot ?: return
        if (undoStack.lastOrNull() != pending) {
            undoStack.addLast(pending)
            if (undoStack.size > MAX_HISTORY) undoStack.removeFirst()
        }
        pendingSnapshot = null
    }

    /** A discrete edit (toolbar action): always its own undo step. */
    fun applyEdit(new: TextFieldValue) {
        commitPending()
        if (new.text == value.text) {
            value = new
            return
        }
        undoStack.addLast(Snapshot(value.text, value.selection))
        if (undoStack.size > MAX_HISTORY) undoStack.removeFirst()
        redoStack.clear()
        value = new
    }

    fun undo() {
        commitPending()
        val previous = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(Snapshot(value.text, value.selection))
        value = TextFieldValue(
            text = previous.text,
            selection = previous.selection.constrainTo(previous.text.length),
        )
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(Snapshot(value.text, value.selection))
        value = TextFieldValue(
            text = next.text,
            selection = next.selection.constrainTo(next.text.length),
        )
    }

    /** Replaces the whole buffer, e.g. when the editor loads a different note. */
    fun reset(new: TextFieldValue) {
        undoStack.clear()
        redoStack.clear()
        pendingSnapshot = null
        value = new
    }

    private fun TextRange.constrainTo(length: Int): TextRange {
        val s = start.coerceIn(0, length)
        val e = end.coerceIn(0, length)
        return TextRange(s, e)
    }

    private companion object {
        const val MAX_HISTORY = 200
    }
}
