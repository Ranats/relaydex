package io.relaydex.android.data

import io.relaydex.android.crypto.aesGcmDecrypt
import io.relaydex.android.crypto.aesGcmEncrypt
import io.relaydex.android.crypto.buildClientAuthTranscript
import io.relaydex.android.crypto.buildTranscriptBytes
import io.relaydex.android.crypto.decodeBase64
import io.relaydex.android.crypto.deriveSharedSecret
import io.relaydex.android.crypto.encodeBase64
import io.relaydex.android.crypto.fingerprint
import io.relaydex.android.crypto.generatePhoneIdentityState
import io.relaydex.android.crypto.generateX25519PrivateKey
import io.relaydex.android.crypto.hkdfSha256
import io.relaydex.android.crypto.pairingQrVersion
import io.relaydex.android.crypto.randomNonce
import io.relaydex.android.crypto.secureClockSkewToleranceSeconds
import io.relaydex.android.crypto.secureNonce
import io.relaydex.android.crypto.secureProtocolVersion
import io.relaydex.android.crypto.sha256
import io.relaydex.android.crypto.signEd25519
import io.relaydex.android.crypto.verifyEd25519
import io.relaydex.android.model.ApprovalRequest
import io.relaydex.android.model.ClientUpdate
import io.relaydex.android.model.ConnectionStatus
import io.relaydex.android.model.ConversationMessage
import io.relaydex.android.model.PairingPayload
import io.relaydex.android.model.PhoneIdentityState
import io.relaydex.android.model.ThreadSummary
import io.relaydex.android.model.TrustedMacRecord
import io.relaydex.android.model.TrustedMacRegistry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.ArrayDeque
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private const val handshakeModeQrBootstrap = "qr_bootstrap"
private const val handshakeModeTrustedReconnect = "trusted_reconnect"

class RemodexClient(
    private val persistence: RemodexPersistence,
) {
    private val clientScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val okHttpClient = OkHttpClient()
    private val updatesFlow = MutableSharedFlow<ClientUpdate>(extraBufferCapacity = 64)
    private val requestMutex = Mutex()
    private val socketMutex = Mutex()
    private val pendingResponses = ConcurrentHashMap<String, CompletableDeferred<JSONObject>>()
    private val pendingSecureControlWaiters = mutableMapOf<String, MutableList<CompletableDeferred<String>>>()
    private val bufferedSecureControlMessages = mutableMapOf<String, ArrayDeque<String>>()

    private var webSocket: WebSocket? = null
    private var openSocketDeferred: CompletableDeferred<Unit>? = null
    private var secureSession: SecureSession? = null
    private var pendingHandshake: PendingHandshake? = null
    private var phoneIdentityState: PhoneIdentityState = persistence.loadPhoneIdentity() ?: generatePhoneIdentityState().also {
        persistence.savePhoneIdentity(it)
    }
    private var trustedMacRegistry: TrustedMacRegistry = persistence.loadTrustedMacRegistry()
    private var lastAppliedBridgeOutboundSeq: Int = persistence.loadLastAppliedBridgeOutboundSeq()
    private var savedPairingPayload: PairingPayload? = persistence.loadPairing()

    val updates: SharedFlow<ClientUpdate> = updatesFlow.asSharedFlow()

    init {
        emitPairingAvailability()
    }

    fun hasSavedPairing(): Boolean = savedPairingPayload != null

    fun currentFingerprint(): String? {
        val pairing = savedPairingPayload ?: return null
        val trusted = trustedMacRegistry.records[pairing.macDeviceId]
        return fingerprint(trusted?.macIdentityPublicKey ?: pairing.macIdentityPublicKey)
    }

    suspend fun connectWithPairingPayload(rawPayload: String) {
        val pairing = parsePairingPayload(rawPayload)
        persistence.savePairing(pairing)
        savedPairingPayload = pairing
        lastAppliedBridgeOutboundSeq = persistence.loadLastAppliedBridgeOutboundSeq()
        emitPairingAvailability()
        connect(pairing)
    }

    suspend fun reconnectSaved() {
        val pairing = savedPairingPayload ?: throw IllegalStateException("No saved pairing is available.")
        connect(pairing)
    }

    suspend fun disconnect(clearSavedPairing: Boolean = false) {
        socketMutex.withLock {
            webSocket?.close(1000, null)
            webSocket = null
            openSocketDeferred = null
            secureSession = null
            pendingHandshake = null
            clearPendingRequests()
            clearSecureWaiters(IllegalStateException("Disconnected"))
            if (clearSavedPairing) {
                persistence.clearPairing()
                savedPairingPayload = null
                lastAppliedBridgeOutboundSeq = 0
                emitPairingAvailability()
            }
        }
        updatesFlow.emit(ClientUpdate.Connection(ConnectionStatus.DISCONNECTED))
    }

    suspend fun listThreads(limit: Int = 40): List<ThreadSummary> {
        val threads = mutableListOf<ThreadSummary>()
        var nextCursor: Any? = JSONObject.NULL
        do {
            val params = JSONObject()
                .put("sourceKinds", JSONArray().apply {
                    put("cli")
                    put("vscode")
                    put("appServer")
                    put("exec")
                    put("unknown")
                })
                .put("limit", limit)
                .put("cursor", nextCursor)
            val result = sendRequest("thread/list", params)
            val page = result.optJSONArray("data")
                ?: result.optJSONArray("items")
                ?: result.optJSONArray("threads")
                ?: JSONArray()
            for (index in 0 until page.length()) {
                val thread = page.optJSONObject(index)?.let(::decodeThreadSummary) ?: continue
                threads += thread
            }
            nextCursor = result.opt("nextCursor").takeUnless { it == null }
                ?: result.opt("next_cursor").takeUnless { it == null }
        } while (nextCursor != null && nextCursor != JSONObject.NULL && threads.size < limit)

        val sorted = threads.sortedByDescending { it.updatedAtEpochMs ?: it.createdAtEpochMs ?: 0L }
        updatesFlow.emit(ClientUpdate.ThreadsLoaded(sorted))
        return sorted
    }

    suspend fun startThread(): ThreadSummary {
        val result = sendRequest("thread/start", JSONObject())
        val thread = decodeThreadSummary(result.optJSONObject("thread") ?: JSONObject())
            ?: throw IllegalStateException("thread/start response did not include a thread.")
        return thread
    }

    suspend fun loadThread(threadId: String): Pair<ThreadSummary?, List<ConversationMessage>> {
        resumeThread(threadId)
        val result = sendRequest(
            "thread/read",
            JSONObject()
                .put("threadId", threadId)
                .put("includeTurns", true),
        )
        val threadObject = result.optJSONObject("thread") ?: JSONObject()
        val summary = decodeThreadSummary(threadObject)
        val messages = decodeMessagesFromThreadRead(threadId, threadObject)
        updatesFlow.emit(ClientUpdate.ThreadLoaded(summary, messages))
        return summary to messages
    }

    suspend fun startTurn(threadId: String, userInput: String) {
        resumeThread(threadId)
        val params = JSONObject()
            .put("threadId", threadId)
            .put(
                "input",
                JSONArray().put(
                    JSONObject()
                        .put("type", "text")
                        .put("text", userInput.trim())
                )
            )
        sendRequest("turn/start", params)
    }

    suspend fun respondToApproval(request: ApprovalRequest, accept: Boolean) {
        sendResponse(request.idValue, if (accept) "accept" else "decline")
        updatesFlow.emit(ClientUpdate.ApprovalCleared)
    }

    private suspend fun connect(pairing: PairingPayload) {
        disconnect(clearSavedPairing = false)
        updatesFlow.emit(ClientUpdate.Connection(ConnectionStatus.CONNECTING, "Connecting to relay..."))

        val relayUrl = pairing.relay.trimEnd('/')
        val request = Request.Builder()
            .url("$relayUrl/${pairing.sessionId}")
            .header("x-role", "iphone")
            .build()

        val openDeferred = CompletableDeferred<Unit>()
        openSocketDeferred = openDeferred
        webSocket = okHttpClient.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    openDeferred.complete(Unit)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    clientScope.launch {
                        try {
                            handleIncomingWireText(text)
                        } catch (error: Throwable) {
                            updatesFlow.emit(ClientUpdate.Error(error.message ?: "Failed to process relay message."))
                        }
                    }
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(code, reason)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    clientScope.launch {
                        handleSocketClosed(code)
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    clientScope.launch {
                        updatesFlow.emit(
                            ClientUpdate.Connection(
                                status = ConnectionStatus.DISCONNECTED,
                                detail = t.message ?: "Relay connection failed.",
                            )
                        )
                    }
                    if (!openDeferred.isCompleted) {
                        openDeferred.completeExceptionally(t)
                    }
                }
            }
        )

        withTimeout(12_000) {
            openDeferred.await()
        }

        updatesFlow.emit(ClientUpdate.Connection(ConnectionStatus.HANDSHAKING, "Performing secure handshake..."))
        performSecureHandshake(pairing)
        initializeSession()
        updatesFlow.emit(
            ClientUpdate.Connection(
                status = ConnectionStatus.CONNECTED,
                detail = "Connected to ${pairing.macDeviceId.take(8)}",
                fingerprint = secureSession?.macIdentityPublicKey?.let(::fingerprint),
            )
        )
    }

    private suspend fun initializeSession() {
        val clientInfo = JSONObject()
            .put("name", "relaydex_android")
            .put("title", "Relaydex Android")
            .put("version", "0.1.0")

        val modernParams = JSONObject()
            .put("clientInfo", clientInfo)
            .put(
                "capabilities",
                JSONObject().put("experimentalApi", true)
            )

        try {
            sendRequest("initialize", modernParams)
        } catch (error: RpcException) {
            val lowered = error.message.lowercase(Locale.US)
            if (
                !lowered.contains("capabilities")
                && !lowered.contains("experimentalapi")
            ) {
                throw error
            }
            sendRequest("initialize", JSONObject().put("clientInfo", clientInfo))
        }

        sendNotification("initialized", null)
    }

    private suspend fun resumeThread(threadId: String) {
        val params = JSONObject().put("threadId", threadId)
        try {
            sendRequest("thread/resume", params)
        } catch (_: Throwable) {
        }
    }

    private suspend fun performSecureHandshake(pairing: PairingPayload) {
        val trustedMac = trustedMacRegistry.records[pairing.macDeviceId]
        val handshakeMode = if (trustedMac != null) handshakeModeTrustedReconnect else handshakeModeQrBootstrap
        val expectedMacIdentityPublicKey = trustedMac?.macIdentityPublicKey ?: pairing.macIdentityPublicKey

        val phoneEphemeralPrivateKey = generateX25519PrivateKey()
        val clientNonce = randomNonce()
        val phoneEphemeralPublicKey = encodeBase64(phoneEphemeralPrivateKey.generatePublicKey().encoded)

        val clientHello = JSONObject()
            .put("kind", "clientHello")
            .put("protocolVersion", secureProtocolVersion)
            .put("sessionId", pairing.sessionId)
            .put("handshakeMode", handshakeMode)
            .put("phoneDeviceId", phoneIdentityState.phoneDeviceId)
            .put("phoneIdentityPublicKey", phoneIdentityState.phoneIdentityPublicKey)
            .put("phoneEphemeralPublicKey", phoneEphemeralPublicKey)
            .put("clientNonce", encodeBase64(clientNonce))

        pendingHandshake = PendingHandshake(
            mode = handshakeMode,
            transcriptBytes = ByteArray(0),
            phoneEphemeralPrivateKey = phoneEphemeralPrivateKey,
            phoneDeviceId = phoneIdentityState.phoneDeviceId,
        )
        sendRawText(clientHello.toString())

        val serverHelloRaw = waitForMatchingServerHello(
            expectedSessionId = pairing.sessionId,
            expectedMacDeviceId = pairing.macDeviceId,
            expectedMacIdentityPublicKey = expectedMacIdentityPublicKey,
            expectedClientNonce = clientHello.getString("clientNonce"),
            clientNonce = clientNonce,
            phoneEphemeralPublicKey = phoneEphemeralPublicKey,
        )
        val serverHello = JSONObject(serverHelloRaw)
        val protocolVersion = serverHello.optInt("protocolVersion", 0)
        if (protocolVersion != secureProtocolVersion) {
            updatesFlow.emit(ClientUpdate.Connection(ConnectionStatus.UPDATE_REQUIRED, "Bridge version mismatch."))
            throw IllegalStateException("This bridge uses a different secure transport version.")
        }

        val transcriptBytes = buildTranscriptBytes(
            sessionId = pairing.sessionId,
            protocolVersion = protocolVersion,
            handshakeMode = serverHello.optString("handshakeMode"),
            keyEpoch = serverHello.optInt("keyEpoch"),
            macDeviceId = pairing.macDeviceId,
            phoneDeviceId = phoneIdentityState.phoneDeviceId,
            macIdentityPublicKey = serverHello.optString("macIdentityPublicKey"),
            phoneIdentityPublicKey = phoneIdentityState.phoneIdentityPublicKey,
            macEphemeralPublicKey = serverHello.optString("macEphemeralPublicKey"),
            phoneEphemeralPublicKey = phoneEphemeralPublicKey,
            clientNonce = clientNonce,
            serverNonce = decodeBase64(serverHello.optString("serverNonce")),
            expiresAtForTranscript = serverHello.optLong("expiresAtForTranscript"),
        )

        val macSignature = decodeBase64(serverHello.optString("macSignature"))
        val signatureValid = verifyEd25519(
            publicKeyBase64 = serverHello.optString("macIdentityPublicKey"),
            payload = transcriptBytes,
            signature = macSignature,
        )
        if (!signatureValid) {
            throw IllegalStateException("The secure host signature could not be verified.")
        }

        pendingHandshake = PendingHandshake(
            mode = handshakeMode,
            transcriptBytes = transcriptBytes,
            phoneEphemeralPrivateKey = phoneEphemeralPrivateKey,
            phoneDeviceId = phoneIdentityState.phoneDeviceId,
        )

        val phoneSignature = signEd25519(
            privateKeyBase64 = phoneIdentityState.phoneIdentityPrivateKey,
            payload = buildClientAuthTranscript(transcriptBytes),
        )
        sendRawText(
            JSONObject()
                .put("kind", "clientAuth")
                .put("sessionId", pairing.sessionId)
                .put("phoneDeviceId", phoneIdentityState.phoneDeviceId)
                .put("keyEpoch", serverHello.optInt("keyEpoch"))
                .put("phoneSignature", encodeBase64(phoneSignature))
                .toString()
        )

        val readyRaw = waitForSecureControlMessage("secureReady")
        val ready = JSONObject(readyRaw)
        if (
            ready.optString("sessionId") != pairing.sessionId
            || ready.optInt("keyEpoch") != serverHello.optInt("keyEpoch")
            || ready.optString("macDeviceId") != pairing.macDeviceId
        ) {
            throw IllegalStateException("The bridge returned a stale secureReady message.")
        }

        val sharedSecret = deriveSharedSecret(
            privateKey = phoneEphemeralPrivateKey,
            publicKeyBase64 = serverHello.optString("macEphemeralPublicKey"),
        )
        val salt = sha256(transcriptBytes)
        val infoPrefix = "${io.relaydex.android.crypto.secureHandshakeTag}|${pairing.sessionId}|${pairing.macDeviceId}|${phoneIdentityState.phoneDeviceId}|${serverHello.optInt("keyEpoch")}"
        val phoneToMacKey = hkdfSha256(sharedSecret, salt, "$infoPrefix|phoneToMac".toByteArray(), 32)
        val macToPhoneKey = hkdfSha256(sharedSecret, salt, "$infoPrefix|macToPhone".toByteArray(), 32)

        secureSession = SecureSession(
            sessionId = pairing.sessionId,
            keyEpoch = serverHello.optInt("keyEpoch"),
            macDeviceId = pairing.macDeviceId,
            macIdentityPublicKey = serverHello.optString("macIdentityPublicKey"),
            phoneToMacKey = phoneToMacKey,
            macToPhoneKey = macToPhoneKey,
            lastInboundBridgeOutboundSeq = lastAppliedBridgeOutboundSeq,
        )
        pendingHandshake = null

        if (handshakeMode == handshakeModeQrBootstrap) {
            val nextRegistry = TrustedMacRegistry(
                records = trustedMacRegistry.records + (
                    pairing.macDeviceId to TrustedMacRecord(
                        macDeviceId = pairing.macDeviceId,
                        macIdentityPublicKey = serverHello.optString("macIdentityPublicKey"),
                        lastPairedAtEpochMs = System.currentTimeMillis(),
                    )
                )
            )
            trustedMacRegistry = nextRegistry
            persistence.saveTrustedMacRegistry(nextRegistry)
        }

        sendRawText(
            JSONObject()
                .put("kind", "resumeState")
                .put("sessionId", pairing.sessionId)
                .put("keyEpoch", serverHello.optInt("keyEpoch"))
                .put("lastAppliedBridgeOutboundSeq", lastAppliedBridgeOutboundSeq)
                .toString()
        )
        emitPairingAvailability()
    }

    private suspend fun waitForMatchingServerHello(
        expectedSessionId: String,
        expectedMacDeviceId: String,
        expectedMacIdentityPublicKey: String,
        expectedClientNonce: String,
        clientNonce: ByteArray,
        phoneEphemeralPublicKey: String,
    ): String {
        while (true) {
            val raw = waitForSecureControlMessage("serverHello")
            val hello = JSONObject(raw)
            val echoedNonce = hello.optString("clientNonce")
            if (echoedNonce.isNotBlank() && echoedNonce != expectedClientNonce) {
                continue
            }
            val isLegacyMatch = if (echoedNonce.isBlank()) {
                val transcript = buildTranscriptBytes(
                    sessionId = expectedSessionId,
                    protocolVersion = hello.optInt("protocolVersion"),
                    handshakeMode = hello.optString("handshakeMode"),
                    keyEpoch = hello.optInt("keyEpoch"),
                    macDeviceId = hello.optString("macDeviceId"),
                    phoneDeviceId = phoneIdentityState.phoneDeviceId,
                    macIdentityPublicKey = hello.optString("macIdentityPublicKey"),
                    phoneIdentityPublicKey = phoneIdentityState.phoneIdentityPublicKey,
                    macEphemeralPublicKey = hello.optString("macEphemeralPublicKey"),
                    phoneEphemeralPublicKey = phoneEphemeralPublicKey,
                    clientNonce = clientNonce,
                    serverNonce = decodeBase64(hello.optString("serverNonce")),
                    expiresAtForTranscript = hello.optLong("expiresAtForTranscript"),
                )
                verifyEd25519(
                    publicKeyBase64 = hello.optString("macIdentityPublicKey"),
                    payload = transcript,
                    signature = decodeBase64(hello.optString("macSignature")),
                )
            } else {
                true
            }

            if (
                hello.optString("sessionId") == expectedSessionId
                && hello.optString("macDeviceId") == expectedMacDeviceId
                && hello.optString("macIdentityPublicKey") == expectedMacIdentityPublicKey
                && isLegacyMatch
            ) {
                return raw
            }
        }
    }

    private suspend fun waitForSecureControlMessage(kind: String): String {
        val secureError = popBufferedSecureError()
        if (secureError != null) {
            throw IllegalStateException(secureError)
        }

        val buffered = bufferedSecureControlMessages[kind]
        if (buffered != null && buffered.isNotEmpty()) {
            return buffered.pollFirst() ?: error("Buffered secure control message missing.")
        }

        val deferred = CompletableDeferred<String>()
        requestMutex.withLock {
            pendingSecureControlWaiters.getOrPut(kind) { mutableListOf() }.add(deferred)
        }
        return withTimeout(12_000) { deferred.await() }
    }

    private suspend fun handleIncomingWireText(text: String) {
        val payload = runCatching { JSONObject(text) }.getOrNull() ?: return
        when (payload.optString("kind")) {
            "serverHello", "secureReady", "secureError" -> bufferSecureControlMessage(payload.optString("kind"), text)
            "encryptedEnvelope" -> handleEncryptedEnvelope(payload)
            else -> {
                if (payload.has("method") || payload.has("id")) {
                    processIncomingApplicationText(text)
                }
            }
        }
    }

    private suspend fun handleEncryptedEnvelope(envelope: JSONObject) {
        val session = secureSession ?: return
        if (
            envelope.optString("sessionId") != session.sessionId
            || envelope.optInt("keyEpoch") != session.keyEpoch
            || envelope.optString("sender") != "mac"
            || envelope.optInt("counter") <= session.lastInboundCounter
        ) {
            updatesFlow.emit(ClientUpdate.Connection(ConnectionStatus.RECONNECT_REQUIRED, "The secure envelope could not be verified."))
            return
        }

        val plaintext = aesGcmDecrypt(
            key = session.macToPhoneKey,
            nonce = secureNonce("mac", envelope.optInt("counter")),
            ciphertext = decodeBase64(envelope.optString("ciphertext")),
            tag = decodeBase64(envelope.optString("tag")),
        )
        val payload = JSONObject(plaintext.toString(Charsets.UTF_8))
        session.lastInboundCounter = envelope.optInt("counter")
        val bridgeOutboundSeq = payload.optInt("bridgeOutboundSeq", -1)
        if (bridgeOutboundSeq > lastAppliedBridgeOutboundSeq) {
            lastAppliedBridgeOutboundSeq = bridgeOutboundSeq
            persistence.saveLastAppliedBridgeOutboundSeq(bridgeOutboundSeq)
        }
        processIncomingApplicationText(payload.optString("payloadText"))
    }

    private suspend fun processIncomingApplicationText(text: String) {
        val message = runCatching { JSONObject(text) }.getOrNull() ?: return
        if (message.has("method")) {
            val method = message.optString("method")
            if (message.has("id")) {
                handleServerRequest(method, message.get("id"), message.optJSONObject("params"))
            } else {
                handleNotification(method, message.optJSONObject("params"))
            }
            return
        }

        if (message.has("id")) {
            val id = message.get("id").toString()
            val continuation = pendingResponses.remove(id) ?: return
            continuation.complete(message)
        }
    }

    private suspend fun handleServerRequest(method: String, idValue: Any, params: JSONObject?) {
        if (
            method == "item/commandExecution/requestApproval"
            || method == "item/fileChange/requestApproval"
            || method.endsWith("requestApproval")
        ) {
            updatesFlow.emit(
                ClientUpdate.ApprovalRequested(
                    ApprovalRequest(
                        idValue = idValue,
                        method = method,
                        command = params?.stringOrNull("command"),
                        reason = params?.stringOrNull("reason"),
                        threadId = params?.stringOrNull("threadId", "thread_id"),
                        turnId = params?.stringOrNull("turnId", "turn_id"),
                    )
                )
            )
            return
        }

        sendErrorResponse(idValue, -32601, "Unsupported request method: $method")
    }

    private suspend fun handleNotification(method: String, params: JSONObject?) {
        when (method) {
            "thread/started", "thread/name/updated", "thread/status/changed" -> {
                try {
                    listThreads()
                } catch (_: Throwable) {
                }
            }

            "turn/completed", "turn/failed", "error" -> {
                updatesFlow.emit(ClientUpdate.TurnCompleted(extractThreadId(params)))
            }

            "item/agentMessage/delta",
            "codex/event/agent_message_content_delta",
            "codex/event/agent_message_delta" -> {
                val delta = extractAssistantDeltaText(params) ?: return
                updatesFlow.emit(
                    ClientUpdate.AssistantDelta(
                        threadId = extractThreadId(params),
                        turnId = extractTurnId(params),
                        itemId = extractItemId(params),
                        delta = delta,
                    )
                )
            }

            "item/completed", "codex/event/item_completed", "codex/event/agent_message" -> {
                val text = extractAssistantCompletedText(params) ?: return
                updatesFlow.emit(
                    ClientUpdate.AssistantCompleted(
                        threadId = extractThreadId(params),
                        turnId = extractTurnId(params),
                        itemId = extractItemId(params),
                        text = text,
                    )
                )
            }
        }
    }

    private suspend fun sendRequest(method: String, params: JSONObject?): JSONObject {
        val requestId = UUID.randomUUID().toString()
        val request = JSONObject()
            .put("id", requestId)
            .put("method", method)
        if (params != null) {
            request.put("params", params)
        }

        val responseDeferred = CompletableDeferred<JSONObject>()
        pendingResponses[requestId] = responseDeferred
        sendMessage(request)
        val response = withTimeout(12_000) { responseDeferred.await() }
        val error = response.optJSONObject("error")
        if (error != null) {
            throw RpcException(
                code = error.optInt("code"),
                message = error.optString("message"),
                data = error.optJSONObject("data"),
            )
        }
        return response.optJSONObject("result") ?: JSONObject()
    }

    private suspend fun sendNotification(method: String, params: JSONObject?) {
        val payload = JSONObject().put("method", method)
        if (params != null) {
            payload.put("params", params)
        }
        sendMessage(payload)
    }

    private suspend fun sendResponse(idValue: Any, resultValue: String) {
        sendMessage(
            JSONObject()
                .put("id", idValue)
                .put("result", resultValue)
        )
    }

    private suspend fun sendErrorResponse(idValue: Any, code: Int, message: String) {
        sendMessage(
            JSONObject()
                .put("id", idValue)
                .put(
                    "error",
                    JSONObject()
                        .put("code", code)
                        .put("message", message)
                )
        )
    }

    private suspend fun sendMessage(message: JSONObject) {
        val secureText = secureWireText(message.toString())
        sendRawText(secureText)
    }

    private suspend fun sendRawText(text: String) {
        val socket = webSocket ?: throw IllegalStateException("Not connected.")
        val sent = withContext(Dispatchers.IO) { socket.send(text) }
        if (!sent) {
            throw IOException("Failed to write to relay socket.")
        }
    }

    private fun secureWireText(plaintext: String): String {
        val session = secureSession ?: throw IllegalStateException("Secure session is not ready yet.")
        val payload = JSONObject()
            .put("bridgeOutboundSeq", JSONObject.NULL)
            .put("payloadText", plaintext)
        val nonce = secureNonce("iphone", session.nextOutboundCounter)
        val (ciphertext, tag) = aesGcmEncrypt(
            key = session.phoneToMacKey,
            nonce = nonce,
            plaintext = payload.toString().toByteArray(Charsets.UTF_8),
        )
        val envelope = JSONObject()
            .put("kind", "encryptedEnvelope")
            .put("v", secureProtocolVersion)
            .put("sessionId", session.sessionId)
            .put("keyEpoch", session.keyEpoch)
            .put("sender", "iphone")
            .put("counter", session.nextOutboundCounter)
            .put("ciphertext", encodeBase64(ciphertext))
            .put("tag", encodeBase64(tag))
        session.nextOutboundCounter += 1
        return envelope.toString()
    }

    private suspend fun handleSocketClosed(code: Int) {
        secureSession = null
        pendingHandshake = null
        clearPendingRequests()
        clearSecureWaiters(IllegalStateException("Socket closed"))

        if (code in setOf(4000, 4001, 4002, 4003)) {
            persistence.clearPairing()
            savedPairingPayload = null
            emitPairingAvailability()
            updatesFlow.emit(
                ClientUpdate.Connection(
                    status = ConnectionStatus.RECONNECT_REQUIRED,
                    detail = when (code) {
                        4002 -> "The host bridge session closed. Scan a new QR code."
                        4003 -> "This device was replaced by a newer connection. Pair again."
                        else -> "This relay pairing is no longer valid. Pair again."
                    }
                )
            )
            return
        }

        updatesFlow.emit(ClientUpdate.Connection(ConnectionStatus.DISCONNECTED, "Relay disconnected."))
    }

    private suspend fun bufferSecureControlMessage(kind: String, rawText: String) {
        if (kind == "secureError") {
            val error = JSONObject(rawText)
            val code = error.optString("code")
            val message = error.optString("message").ifBlank { "Secure handshake failed." }
            val status = when (code) {
                "update_required" -> ConnectionStatus.UPDATE_REQUIRED
                "pairing_expired", "phone_not_trusted", "phone_identity_changed", "phone_replacement_required" -> ConnectionStatus.RECONNECT_REQUIRED
                else -> ConnectionStatus.DISCONNECTED
            }
            updatesFlow.emit(ClientUpdate.Connection(status, message))
        }

        val waiter = requestMutex.withLock {
            val waiters = pendingSecureControlWaiters[kind]
            if (waiters.isNullOrEmpty()) {
                bufferedSecureControlMessages.getOrPut(kind) { ArrayDeque() }.add(rawText)
                null
            } else {
                waiters.removeAt(0).also {
                    if (waiters.isEmpty()) {
                        pendingSecureControlWaiters.remove(kind)
                    }
                }
            }
        }
        waiter?.complete(rawText)
    }

    private fun emitPairingAvailability() {
        val pairing = savedPairingPayload
        val currentFingerprint = pairing?.let {
            val trusted = trustedMacRegistry.records[it.macDeviceId]
            fingerprint(trusted?.macIdentityPublicKey ?: it.macIdentityPublicKey)
        }
        updatesFlow.tryEmit(ClientUpdate.PairingAvailability(pairing != null, currentFingerprint))
    }

    private fun parsePairingPayload(rawPayload: String): PairingPayload {
        val payload = PairingPayload.fromJson(JSONObject(rawPayload))
            ?: throw IllegalArgumentException("The QR payload is missing required pairing fields.")
        require(payload.version == pairingQrVersion) {
            "Unsupported pairing format. Update the Android client or the bridge."
        }
        require(payload.relay.isNotBlank()) { "The pairing payload is missing the relay URL." }
        require(payload.sessionId.isNotBlank()) { "The pairing payload is missing the session ID." }
        val expiryWithSkew = payload.expiresAt + (secureClockSkewToleranceSeconds * 1000)
        require(expiryWithSkew >= System.currentTimeMillis()) {
            "The pairing QR code has expired. Generate a new QR code from the bridge."
        }
        return payload
    }

    private fun extractThreadId(params: JSONObject?): String? {
        if (params == null) return null
        return params.stringOrNull("threadId", "thread_id", "conversationId", "conversation_id")
            ?: params.optJSONObject("thread")?.stringOrNull("id")
            ?: params.optJSONObject("turn")?.stringOrNull("threadId", "thread_id")
            ?: params.optJSONObject("item")?.stringOrNull("threadId", "thread_id")
            ?: params.objectOrNull("msg", "event")?.let(::extractThreadId)
    }

    private fun extractTurnId(params: JSONObject?): String? {
        if (params == null) return null
        return params.stringOrNull("turnId", "turn_id", "id")
            ?: params.optJSONObject("turn")?.stringOrNull("id", "turnId", "turn_id")
            ?: params.optJSONObject("item")?.stringOrNull("turnId", "turn_id")
            ?: params.objectOrNull("msg", "event")?.let(::extractTurnId)
    }

    private fun extractItemId(params: JSONObject?): String? {
        if (params == null) return null
        return params.stringOrNull("itemId", "item_id", "messageId", "message_id")
            ?: params.optJSONObject("item")?.stringOrNull("id", "itemId", "item_id", "messageId", "message_id")
            ?: params.objectOrNull("msg", "event")?.let(::extractItemId)
    }

    private fun extractAssistantDeltaText(params: JSONObject?): String? {
        if (params == null) return null
        return params.stringOrNull("delta")
            ?: params.objectOrNull("msg", "event")?.stringOrNull("delta")
    }

    private fun extractAssistantCompletedText(params: JSONObject?): String? {
        if (params == null) return null
        val item = params.objectOrNull("item")
            ?: params.objectOrNull("msg", "event")?.optJSONObject("item")
        if (item != null) {
            val type = normalizeItemType(item.optString("type"))
            val role = item.optString("role").lowercase(Locale.US)
            if (type == "agentmessage" || type == "assistantmessage" || (type == "message" && role != "user")) {
                return item.stringOrNull("text", "message")
                    ?: decodeThreadReadAssistantContent(item)
            }
        }
        return params.stringOrNull("message")
            ?: params.objectOrNull("msg", "event")?.stringOrNull("message")
    }

    private fun decodeThreadReadAssistantContent(item: JSONObject): String? {
        val content = item.optJSONArray("content") ?: return null
        val parts = mutableListOf<String>()
        for (index in 0 until content.length()) {
            val block = content.optJSONObject(index) ?: continue
            block.stringOrNull("text", "delta")?.let(parts::add)
        }
        return parts.joinToString("\n").trim().takeIf { it.isNotEmpty() }
    }

    private suspend fun popBufferedSecureError(): String? {
        val raw = requestMutex.withLock {
            val buffered = bufferedSecureControlMessages["secureError"]
            if (buffered.isNullOrEmpty()) {
                null
            } else {
                buffered.pollFirst()
            }
        } ?: return null
        return JSONObject(raw).optString("message")
    }

    private suspend fun clearSecureWaiters(error: Throwable) {
        val waiters = requestMutex.withLock {
            val values = pendingSecureControlWaiters.values.flatten()
            pendingSecureControlWaiters.clear()
            bufferedSecureControlMessages.clear()
            values
        }
        waiters.forEach { it.completeExceptionally(error) }
    }

    private fun clearPendingRequests() {
        val outstanding = pendingResponses.values.toList()
        pendingResponses.clear()
        outstanding.forEach { it.completeExceptionally(IllegalStateException("Disconnected")) }
    }

    data class RpcException(
        val code: Int,
        override val message: String,
        val data: JSONObject?,
    ) : RuntimeException(message)

    private data class PendingHandshake(
        val mode: String,
        val transcriptBytes: ByteArray,
        val phoneEphemeralPrivateKey: X25519PrivateKeyParameters,
        val phoneDeviceId: String,
    )

    private data class SecureSession(
        val sessionId: String,
        val keyEpoch: Int,
        val macDeviceId: String,
        val macIdentityPublicKey: String,
        val phoneToMacKey: ByteArray,
        val macToPhoneKey: ByteArray,
        var lastInboundBridgeOutboundSeq: Int,
        var lastInboundCounter: Int = -1,
        var nextOutboundCounter: Int = 0,
    )
}
