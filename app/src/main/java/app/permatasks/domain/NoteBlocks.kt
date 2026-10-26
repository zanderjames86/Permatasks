package app.permatasks.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

enum class BlockType { PARAGRAPH, H1, H2, H3, BULLET, NUMBERED, CHECK, QUOTE, CALLOUT, CODE, DIVIDER, IMAGE }

/**
 * One block of a note. A CHECK block can be linked to a task ([taskId]) so the checklist item
 * gets a due date and nags like any other task; ticking either side completes both.
 * IMAGE blocks reference an encrypted file in the app's private storage by [image] name.
 */
@Serializable
data class Block(
    val id: String = UUID.randomUUID().toString(),
    val type: BlockType = BlockType.PARAGRAPH,
    val text: String = "",
    val checked: Boolean = false,
    val taskId: String? = null,
    val image: String? = null,
)

object NoteBlocks {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    val linkRe = Regex("\\[\\[([^\\[\\]|]+)(?:\\|([^\\[\\]]+))?]]")
    val urlRe = Regex("https?://[^\\s)]+")

    fun decode(raw: String): List<Block> =
        runCatching { json.decodeFromString<List<Block>>(raw) }.getOrDefault(emptyList()).ifEmpty { listOf(Block()) }

    fun encode(blocks: List<Block>): String = json.encodeToString(blocks)

    fun plainText(blocks: List<Block>): String =
        blocks.filter { it.type != BlockType.DIVIDER && it.type != BlockType.IMAGE }.joinToString("\n") { it.text }.trim()

    /** Lower-cased titles this note links to with [[Title]] or [[Title|label]]. */
    fun links(blocks: List<Block>): Set<String> =
        blocks.flatMap { b -> linkRe.findAll(b.text).map { it.groupValues[1].trim().lowercase() } }.filter { it.isNotEmpty() }.toSet()

    fun toMarkdown(title: String, blocks: List<Block>): String = buildString {
        if (title.isNotBlank()) append("# ").append(title).append("\n\n")
        var number = 0
        blocks.forEach { b ->
            number = if (b.type == BlockType.NUMBERED) number + 1 else 0
            append(
                when (b.type) {
                    BlockType.PARAGRAPH -> b.text
                    BlockType.H1 -> "## ${b.text}"
                    BlockType.H2 -> "### ${b.text}"
                    BlockType.H3 -> "#### ${b.text}"
                    BlockType.BULLET -> "- ${b.text}"
                    BlockType.NUMBERED -> "$number. ${b.text}"
                    BlockType.CHECK -> "- [${if (b.checked) "x" else " "}] ${b.text}"
                    BlockType.QUOTE -> "> ${b.text}"
                    BlockType.CALLOUT -> "> 💡 ${b.text}"
                    BlockType.CODE -> "```\n${b.text}\n```"
                    BlockType.DIVIDER -> "---"
                    BlockType.IMAGE -> "![image](${b.image})"
                },
            )
            append('\n')
        }
    }

    /** Markdown-ish shortcuts typed at the start of a block: "# ", "- ", "[] ", "> ", "1. ", "```". */
    fun shortcut(text: String): Pair<BlockType, String>? = when {
        text.startsWith("# ") -> BlockType.H1 to text.drop(2)
        text.startsWith("## ") -> BlockType.H2 to text.drop(3)
        text.startsWith("### ") -> BlockType.H3 to text.drop(4)
        text.startsWith("- ") || text.startsWith("* ") -> BlockType.BULLET to text.drop(2)
        text.startsWith("[] ") || text.startsWith("[ ] ") -> BlockType.CHECK to text.substringAfter("] ")
        text.startsWith("1. ") -> BlockType.NUMBERED to text.drop(3)
        text.startsWith("> ") -> BlockType.QUOTE to text.drop(2)
        text.startsWith("```") -> BlockType.CODE to text.drop(3)
        text == "---" -> BlockType.DIVIDER to ""
        else -> null
    }
}
