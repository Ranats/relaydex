package io.relaydex.android.data

import io.relaydex.android.model.ConversationKind
import io.relaydex.android.model.ConversationMessage
import io.relaydex.android.model.ConversationRole
import io.relaydex.android.model.ModelOption
import io.relaydex.android.model.ReasoningEffortOption
import io.relaydex.android.model.ThreadSummary
import org.json.JSONArray
import org.json.JSONObject
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

private val isoFormatters = listOf(
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX", Locale.US),
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX", Locale.US),
).onEach { formatter ->
    formatter.timeZone = TimeZone.getTimeZone("UTC")
}

fun JSONObject.stringOrNull(vararg keys: String): String? {
    for (key in keys) {
        val value = optString(key, "").trim()
        if (value.isNotEmpty()) {
            return value
        }
    }
    return null
}

fun JSONObject.objectOrNull(vararg keys: String): JSONObject? {
    for (key in keys) {
        val value = optJSONObject(key)
        if (value != null) {
            return value
        }
    }
    return null
}

fun JSONObject.arrayOrNull(vararg keys: String): JSONArray? {
    for (key in keys) {
        val value = optJSONArray(key)
        if (value != null) {
            return value
        }
    }
    return null
}

fun normalizeItemType(rawValue: String?): String {
    return rawValue
        ?.trim()
        ?.lowercase(Locale.US)
        ?.replace("_", "")
        ?.replace("-", "")
        ?.replace("/", "")
        ?: ""
}

fun parseTimestamp(rawValue: Any?): Long? {
    return when (rawValue) {
        is Number -> {
            val value = rawValue.toDouble()
            if (value > 10_000_000_000) value.toLong() else (value * 1000).toLong()
        }

        is String -> {
            val trimmed = rawValue.trim()
            if (trimmed.isEmpty()) {
                null
            } else {
                trimmed.toLongOrNull()?.let { numeric ->
                    if (numeric > 10_000_000_000) numeric else numeric * 1000
                } ?: parseIsoTimestamp(trimmed)
            }
        }

        else -> null
    }
}

private fun parseIsoTimestamp(value: String): Long? {
    for (formatter in isoFormatters) {
        try {
            return formatter.parse(value)?.time
        } catch (_: ParseException) {
        }
    }
    return null
}

fun decodeThreadSummary(json: JSONObject): ThreadSummary? {
    val id = json.stringOrNull("id") ?: return null
    val title = json.stringOrNull("name", "title", "preview") ?: "Conversation"
    val preview = json.stringOrNull("preview")
    val cwd = json.stringOrNull("cwd", "current_working_directory", "working_directory")
    val createdAt = parseTimestamp(
        json.opt("createdAt").takeUnless { it == null }
            ?: json.opt("created_at").takeUnless { it == null }
    )
    val updatedAt = parseTimestamp(
        json.opt("updatedAt").takeUnless { it == null }
            ?: json.opt("updated_at").takeUnless { it == null }
    )
    return ThreadSummary(
        id = id,
        title = title,
        preview = preview,
        cwd = cwd,
        createdAtEpochMs = createdAt,
        updatedAtEpochMs = updatedAt,
    )
}

fun decodeMessagesFromThreadRead(threadId: String, threadObject: JSONObject): List<ConversationMessage> {
    val turns = threadObject.optJSONArray("turns") ?: return emptyList()
    val baseTime = parseTimestamp(
        threadObject.opt("updatedAt").takeUnless { it == null }
            ?: threadObject.opt("updated_at").takeUnless { it == null }
            ?: threadObject.opt("createdAt").takeUnless { it == null }
            ?: threadObject.opt("created_at").takeUnless { it == null }
    ) ?: 0L

    val messages = mutableListOf<ConversationMessage>()
    var offset = 0L

    for (turnIndex in 0 until turns.length()) {
        val turnObject = turns.optJSONObject(turnIndex) ?: continue
        val turnId = turnObject.stringOrNull("id", "turnId", "turn_id")
        val turnTimestamp = parseTimestamp(
            turnObject.opt("createdAt").takeUnless { it == null }
                ?: turnObject.opt("created_at").takeUnless { it == null }
                ?: turnObject.opt("updatedAt").takeUnless { it == null }
                ?: turnObject.opt("updated_at").takeUnless { it == null }
        ) ?: (baseTime + offset)
        val items = turnObject.optJSONArray("items") ?: continue

        for (itemIndex in 0 until items.length()) {
            val itemObject = items.optJSONObject(itemIndex) ?: continue
            val createdAt = parseTimestamp(
                itemObject.opt("createdAt").takeUnless { it == null }
                    ?: itemObject.opt("created_at").takeUnless { it == null }
            ) ?: (turnTimestamp + offset)
            offset += 1L
            val itemType = normalizeItemType(itemObject.optString("type"))
            val itemId = itemObject.stringOrNull("id", "itemId", "item_id", "messageId", "message_id")

            when (itemType) {
                "usermessage" -> {
                    messages += ConversationMessage(
                        id = itemId ?: UUID.randomUUID().toString(),
                        threadId = threadId,
                        role = ConversationRole.USER,
                        kind = ConversationKind.CHAT,
                        text = decodeItemText(itemObject),
                        createdAtEpochMs = createdAt,
                        turnId = turnId,
                        itemId = itemId,
                    )
                }

                "agentmessage", "assistantmessage" -> {
                    messages += ConversationMessage(
                        id = itemId ?: UUID.randomUUID().toString(),
                        threadId = threadId,
                        role = ConversationRole.ASSISTANT,
                        kind = ConversationKind.CHAT,
                        text = decodeItemText(itemObject),
                        createdAtEpochMs = createdAt,
                        turnId = turnId,
                        itemId = itemId,
                    )
                }

                "message" -> {
                    val role = when (itemObject.optString("role").lowercase(Locale.US)) {
                        "user" -> ConversationRole.USER
                        "assistant" -> ConversationRole.ASSISTANT
                        else -> ConversationRole.SYSTEM
                    }
                    messages += ConversationMessage(
                        id = itemId ?: UUID.randomUUID().toString(),
                        threadId = threadId,
                        role = role,
                        kind = if (role == ConversationRole.SYSTEM) ConversationKind.THINKING else ConversationKind.CHAT,
                        text = decodeItemText(itemObject),
                        createdAtEpochMs = createdAt,
                        turnId = turnId,
                        itemId = itemId,
                    )
                }

                "reasoning" -> {
                    messages += ConversationMessage(
                        id = itemId ?: UUID.randomUUID().toString(),
                        threadId = threadId,
                        role = ConversationRole.SYSTEM,
                        kind = ConversationKind.THINKING,
                        text = decodeReasoningText(itemObject),
                        createdAtEpochMs = createdAt,
                        turnId = turnId,
                        itemId = itemId,
                    )
                }

                "filechange", "toolcall", "diff" -> {
                    messages += ConversationMessage(
                        id = itemId ?: UUID.randomUUID().toString(),
                        threadId = threadId,
                        role = ConversationRole.SYSTEM,
                        kind = ConversationKind.FILE_CHANGE,
                        text = decodeFileChangeText(itemObject),
                        createdAtEpochMs = createdAt,
                        turnId = turnId,
                        itemId = itemId,
                    )
                }

                "commandexecution" -> {
                    messages += ConversationMessage(
                        id = itemId ?: UUID.randomUUID().toString(),
                        threadId = threadId,
                        role = ConversationRole.SYSTEM,
                        kind = ConversationKind.COMMAND,
                        text = decodeCommandText(itemObject),
                        createdAtEpochMs = createdAt,
                        turnId = turnId,
                        itemId = itemId,
                    )
                }

                "plan" -> {
                    messages += ConversationMessage(
                        id = itemId ?: UUID.randomUUID().toString(),
                        threadId = threadId,
                        role = ConversationRole.SYSTEM,
                        kind = ConversationKind.PLAN,
                        text = decodePlanText(itemObject),
                        createdAtEpochMs = createdAt,
                        turnId = turnId,
                        itemId = itemId,
                    )
                }
            }
        }
    }

    return messages.sortedBy { it.createdAtEpochMs }
}

fun decodeModelOptions(resultObject: JSONObject): List<ModelOption> {
    val items = resultObject.optJSONArray("items")
        ?: resultObject.optJSONArray("data")
        ?: resultObject.optJSONArray("models")
        ?: JSONArray()

    val decoded = mutableListOf<ModelOption>()
    for (index in 0 until items.length()) {
        val item = items.optJSONObject(index) ?: continue
        val model = item.stringOrNull("model", "id") ?: continue
        val id = item.stringOrNull("id") ?: model
        val displayName = item.stringOrNull("displayName", "display_name") ?: model
        val description = item.stringOrNull("description") ?: ""
        val isDefault = item.optBoolean("isDefault", item.optBoolean("is_default", false))
        val supportedEfforts = decodeReasoningEffortOptions(
            item.optJSONArray("supportedReasoningEfforts")
                ?: item.optJSONArray("supported_reasoning_efforts")
                ?: JSONArray()
        )
        val defaultReasoningEffort = item.stringOrNull("defaultReasoningEffort", "default_reasoning_effort")
        decoded += ModelOption(
            id = id,
            model = model,
            displayName = displayName,
            description = description,
            isDefault = isDefault,
            supportedReasoningEfforts = supportedEfforts,
            defaultReasoningEffort = defaultReasoningEffort,
        )
    }
    return decoded
}

private fun decodeReasoningEffortOptions(items: JSONArray): List<ReasoningEffortOption> {
    val decoded = mutableListOf<ReasoningEffortOption>()
    for (index in 0 until items.length()) {
        val item = items.optJSONObject(index) ?: continue
        val effort = item.stringOrNull("reasoningEffort", "reasoning_effort") ?: continue
        decoded += ReasoningEffortOption(
            reasoningEffort = effort,
            description = item.stringOrNull("description") ?: "",
        )
    }
    return decoded
}

private fun decodeItemText(itemObject: JSONObject): String {
    val content = itemObject.optJSONArray("content")
    val parts = mutableListOf<String>()
    if (content != null) {
        for (index in 0 until content.length()) {
            val value = content.optJSONObject(index) ?: continue
            when (normalizeItemType(value.optString("type"))) {
                "text", "inputtext", "outputtext", "message" -> {
                    value.stringOrNull("text", "delta")?.let(parts::add)
                }

                "skill" -> {
                    val skill = value.stringOrNull("id", "name")
                    if (!skill.isNullOrBlank()) {
                        parts += "\$$skill"
                    }
                }
            }
        }
    }
    val combined = parts.joinToString("\n").trim()
    if (combined.isNotEmpty()) {
        return combined
    }
    return itemObject.stringOrNull("text", "message", "summary") ?: ""
}

private fun decodeReasoningText(itemObject: JSONObject): String {
    return itemObject.stringOrNull("summary", "text", "message")
        ?: itemObject.optJSONObject("summary")?.stringOrNull("text")
        ?: "Thinking..."
}

private fun decodeFileChangeText(itemObject: JSONObject): String {
    val status = itemObject.stringOrNull("status") ?: "completed"
    val directDiff = itemObject.stringOrNull("diff", "unified_diff", "unifiedDiff", "patch")
    if (!directDiff.isNullOrBlank()) {
        return "Status: $status\n\n$directDiff"
    }
    val path = itemObject.stringOrNull("path", "file", "file_path", "filePath")
    val summary = itemObject.stringOrNull("summary", "message", "text")
    return listOfNotNull(
        "Status: $status",
        path?.let { "Path: $it" },
        summary,
    ).joinToString("\n\n")
}

private fun decodeCommandText(itemObject: JSONObject): String {
    val status = itemObject.stringOrNull("status") ?: "completed"
    val command = itemObject.stringOrNull("command", "cmd", "raw_command", "rawCommand", "message")
        ?: "command"
    return "${status.replaceFirstChar(Char::uppercase)}: $command"
}

private fun decodePlanText(itemObject: JSONObject): String {
    val steps = itemObject.arrayOrNull("steps", "items")
    if (steps != null && steps.length() > 0) {
        val parts = mutableListOf<String>()
        for (index in 0 until steps.length()) {
            val step = steps.optJSONObject(index) ?: continue
            val text = step.stringOrNull("step", "title", "text") ?: continue
            val status = step.stringOrNull("status")
            parts += if (status.isNullOrBlank()) text else "[$status] $text"
        }
        if (parts.isNotEmpty()) {
            return parts.joinToString("\n")
        }
    }
    return itemObject.stringOrNull("summary", "message", "text") ?: "Plan updated"
}
