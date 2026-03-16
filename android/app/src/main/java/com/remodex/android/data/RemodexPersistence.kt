package io.relaydex.android.data

import android.content.Context
import io.relaydex.android.crypto.SecureStore
import io.relaydex.android.model.PairingPayload
import io.relaydex.android.model.PhoneIdentityState
import io.relaydex.android.model.TrustedMacRegistry
import org.json.JSONObject

class RemodexPersistence(context: Context) {
    private val secureStore = SecureStore(context)

    fun hasSavedPairing(): Boolean = loadPairing() != null

    fun loadPairing(): PairingPayload? {
        val raw = secureStore.readString(KEY_PAIRING) ?: return null
        return runCatching { PairingPayload.fromJson(JSONObject(raw)) }.getOrNull()
    }

    fun savePairing(pairingPayload: PairingPayload) {
        secureStore.writeString(KEY_PAIRING, pairingPayload.toJson().toString())
    }

    fun clearPairing() {
        secureStore.remove(KEY_PAIRING)
        secureStore.remove(KEY_LAST_APPLIED_SEQ)
    }

    fun loadPhoneIdentity(): PhoneIdentityState? {
        val raw = secureStore.readString(KEY_PHONE_IDENTITY) ?: return null
        return runCatching { PhoneIdentityState.fromJson(JSONObject(raw)) }.getOrNull()
    }

    fun savePhoneIdentity(identityState: PhoneIdentityState) {
        secureStore.writeString(KEY_PHONE_IDENTITY, identityState.toJson().toString())
    }

    fun loadTrustedMacRegistry(): TrustedMacRegistry {
        val raw = secureStore.readString(KEY_TRUSTED_MACS) ?: return TrustedMacRegistry.empty
        return runCatching { TrustedMacRegistry.fromJson(JSONObject(raw)) }.getOrDefault(TrustedMacRegistry.empty)
    }

    fun saveTrustedMacRegistry(registry: TrustedMacRegistry) {
        secureStore.writeString(KEY_TRUSTED_MACS, registry.toJson().toString())
    }

    fun loadLastAppliedBridgeOutboundSeq(): Int {
        return secureStore.readString(KEY_LAST_APPLIED_SEQ)?.toIntOrNull() ?: 0
    }

    fun saveLastAppliedBridgeOutboundSeq(value: Int) {
        secureStore.writeString(KEY_LAST_APPLIED_SEQ, value.toString())
    }

    fun loadSelectedModelId(): String? {
        return secureStore.readString(KEY_SELECTED_MODEL_ID)?.trim()?.takeIf { it.isNotEmpty() }
    }

    fun saveSelectedModelId(value: String?) {
        val normalized = value?.trim()?.takeIf { it.isNotEmpty() }
        if (normalized == null) {
            secureStore.remove(KEY_SELECTED_MODEL_ID)
        } else {
            secureStore.writeString(KEY_SELECTED_MODEL_ID, normalized)
        }
    }

    fun loadSelectedReasoningEffort(): String? {
        return secureStore.readString(KEY_SELECTED_REASONING_EFFORT)?.trim()?.takeIf { it.isNotEmpty() }
    }

    fun saveSelectedReasoningEffort(value: String?) {
        val normalized = value?.trim()?.takeIf { it.isNotEmpty() }
        if (normalized == null) {
            secureStore.remove(KEY_SELECTED_REASONING_EFFORT)
        } else {
            secureStore.writeString(KEY_SELECTED_REASONING_EFFORT, normalized)
        }
    }

    private companion object {
        const val KEY_PAIRING = "pairing_payload"
        const val KEY_PHONE_IDENTITY = "phone_identity"
        const val KEY_TRUSTED_MACS = "trusted_macs"
        const val KEY_LAST_APPLIED_SEQ = "last_applied_bridge_outbound_seq"
        const val KEY_SELECTED_MODEL_ID = "selected_model_id"
        const val KEY_SELECTED_REASONING_EFFORT = "selected_reasoning_effort"
    }
}
