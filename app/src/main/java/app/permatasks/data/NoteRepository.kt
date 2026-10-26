package app.permatasks.data

import app.permatasks.domain.Block
import app.permatasks.domain.BlockType
import app.permatasks.domain.NoteBlocks
import app.permatasks.security.FileVault

class NoteRepository(db: AppDatabase, private val tasks: TaskRepository, val vault: FileVault) {
    val notes = db.notes()

    suspend fun create(title: String = "", blocks: List<Block> = listOf(Block())): NoteEntity {
        val note = NoteEntity(title = title, blocks = NoteBlocks.encode(blocks), plainText = NoteBlocks.plainText(blocks))
        notes.upsert(note)
        refreshLinks(note.id, blocks)
        return note
    }

    suspend fun save(note: NoteEntity, blocks: List<Block>) {
        notes.upsert(
            note.copy(
                blocks = NoteBlocks.encode(blocks),
                plainText = NoteBlocks.plainText(blocks),
                updatedAt = System.currentTimeMillis(),
            ),
        )
        refreshLinks(note.id, blocks)
    }

    private suspend fun refreshLinks(noteId: String, blocks: List<Block>) {
        notes.clearLinks(noteId)
        notes.insertLinks(NoteBlocks.links(blocks).map { NoteLinkEntity(noteId, it) })
    }

    /** Follows a [[link]]: opens the note with that title, creating it if needed. */
    suspend fun openOrCreate(title: String): NoteEntity =
        notes.byTitle(title.trim()) ?: create(title.trim())

    suspend fun delete(note: NoteEntity) {
        NoteBlocks.decode(note.blocks).forEach { b ->
            b.image?.let(vault::delete)
            b.taskId?.let { tasks.delete(it) }
        }
        notes.clearLinks(note.id)
        notes.delete(note.id)
    }

    /** Turns a checklist item into a real task that nags; returns the new task id. */
    suspend fun remindChecklistItem(noteId: String, text: String): String? =
        tasks.quickAdd(text.ifBlank { "Checklist item" }, noteId = noteId)?.id

    suspend fun setChecked(block: Block, checked: Boolean) {
        val id = block.taskId ?: return
        if (checked) tasks.complete(id) else tasks.reopen(id)
    }

    fun isChecklist(b: Block) = b.type == BlockType.CHECK
}
