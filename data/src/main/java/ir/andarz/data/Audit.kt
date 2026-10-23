package ir.andarz.data

import ir.andarz.data.db.AuditDao
import ir.andarz.data.db.AuditLogEntity
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.UUID

/** Audit row types of 4.2. Rows store codes and numbers only; the UI renders them through content. */
enum class AuditType {
    PARAM_CHANGE, PARAM_CANCEL, TRADE_EDIT, TRADE_DELETE, LEDGER_EDIT, LEDGER_DELETE, PLEDGE_SIGNED,
    BACKUP_EXPORT, BACKUP_RESTORE, CSV_IMPORT, CSV_EXPORT, DATA_WIPE, DEMO_LOAD, DEMO_REMOVE,
}

/** Builds an audit row; the caller inserts it inside its own transaction. */
object Audit {
    fun row(time: Long, type: AuditType, refId: String?, payload: Map<String, Any?> = emptyMap()) = AuditLogEntity(
        id = UUID.randomUUID().toString(), time = time, type = type.name, refId = refId,
        payload = JsonObject(payload.mapValues { (_, v) -> json(v) }).toString(),
    )

    private fun json(v: Any?): JsonElement = when (v) {
        null -> kotlinx.serialization.json.JsonNull
        is Number -> JsonPrimitive(v)
        is Boolean -> JsonPrimitive(v)
        else -> JsonPrimitive(v.toString())
    }

    suspend fun AuditDao.log(time: Long, type: AuditType, refId: String?, payload: Map<String, Any?> = emptyMap()) =
        insert(row(time, type, refId, payload))
}
