package io.github.j4sp3rtm.todomanager

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.annotations.XCollection
import java.util.UUID

/**
 * Project-level persistent store for general (code-free) TODOs — items that are not backed by a
 * source comment. The [TodoScannerService] merges these with the comments it finds so both show up
 * together in the tool window.
 */
@Service(Service.Level.PROJECT)
@State(name = "TodoManagerGeneralTodos", storages = [Storage("todoManagerGeneralTodos.xml")])
class GeneralTodoStore : PersistentStateComponent<GeneralTodoStore.State> {

    /** One stored general todo. All fields are `var` with defaults so XML (de)serialization works. */
    data class Entry(
        var id: String = "",
        var keyword: String = "TODO",
        var tag: String? = null,
        var priority: String? = null,
        var description: String = "",
        var done: Boolean = false,
        var doneBy: String? = null,
        var doneAt: String? = null,
    )

    data class State(
        @XCollection(style = XCollection.Style.v2)
        var entries: MutableList<Entry> = mutableListOf(),
    )

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    /** Snapshot of the current entries. */
    val entries: List<Entry> get() = state.entries.toList()

    fun add(keyword: String, tag: String?, priority: String?, description: String): Entry {
        val entry = Entry(
            id = UUID.randomUUID().toString(),
            keyword = keyword,
            tag = tag,
            priority = priority,
            description = description,
        )
        state.entries.add(entry)
        return entry
    }

    /** Applies [mutate] to the entry with [id], if present. */
    fun update(id: String, mutate: (Entry) -> Unit) {
        state.entries.find { it.id == id }?.let(mutate)
    }

    fun remove(id: String) {
        state.entries.removeAll { it.id == id }
    }

    companion object {
        fun getInstance(project: Project): GeneralTodoStore =
            project.getService(GeneralTodoStore::class.java)
    }
}
