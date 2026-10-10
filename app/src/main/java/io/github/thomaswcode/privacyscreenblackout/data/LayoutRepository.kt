package io.github.thomaswcode.privacyscreenblackout.data

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import io.github.thomaswcode.privacyscreenblackout.model.Layout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/** Stores all layouts in filesDir/layouts.json. Reads once at start-up; writes are atomic and off the main thread. */
class LayoutRepository(context: Context, private val scope: CoroutineScope) {

    private val file = AtomicFile(File(context.filesDir, "layouts.json"))
    private val writeLock = Mutex()
    private val _layouts = MutableStateFlow(load())
    val layouts: StateFlow<List<Layout>> = _layouts.asStateFlow()

    fun get(id: String?): Layout? = id?.let { wanted -> _layouts.value.firstOrNull { it.id == wanted } }

    fun upsert(layout: Layout) = mutate { list ->
        if (list.any { it.id == layout.id }) list.map { if (it.id == layout.id) layout else it } else list + layout
    }

    fun delete(id: String) = mutate { list -> list.filterNot { it.id == id } }

    /** Creates an empty layout named "Layout N" and returns it. */
    fun create(): Layout {
        val names = _layouts.value.map { it.name }.toSet()
        var n = _layouts.value.size + 1
        while ("Layout $n" in names) n++
        val layout = Layout(id = UUID.randomUUID().toString(), name = "Layout $n")
        upsert(layout)
        return layout
    }

    private fun mutate(block: (List<Layout>) -> List<Layout>) {
        _layouts.update(block)
        val snapshot = _layouts.value
        scope.launch(Dispatchers.IO) { writeLock.withLock { save(snapshot) } }
    }

    private fun load(): List<Layout> = try {
        if (file.baseFile.exists()) json.decodeFromString<List<Layout>>(file.readFully().decodeToString()) else emptyList()
    } catch (e: Exception) {
        Log.e(TAG, "Could not read layouts", e)
        emptyList()
    }

    private fun save(list: List<Layout>) {
        val out = file.startWrite()
        try {
            out.write(json.encodeToString(list).encodeToByteArray())
            file.finishWrite(out)
        } catch (e: Exception) {
            file.failWrite(out)
            Log.e(TAG, "Could not save layouts", e)
        }
    }

    companion object {
        private const val TAG = "LayoutRepository"
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    }
}
