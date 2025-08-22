package com.app.mdlbapp.data.call

import android.util.Log
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

suspend fun notifyCallViaAppsScript(
    webHookUrl: String,
    calleeUid: String,
    callerUid: String,
    callerName: String,
    hookSecret: String? = null
) {
    // 1) Берём токены получателя
    val tokens = (Firebase.firestore.collection("users").document(calleeUid)
        .get().await().get("fcmTokens") as? List<*>)?.filterIsInstance<String>().orEmpty()
    if (tokens.isEmpty()) {
        Log.w("WEBHOOK","no tokens for $calleeUid")
        return
    }

    // 2) Собираем JSON безопасно
    val payload = JSONObject().apply {
        put("tokens", JSONArray(tokens))
        put("fromUid", callerUid)
        put("fromName", callerName)
        hookSecret?.takeIf { it.isNotBlank() }?.let { put("secret", it) }
    }.toString()

    val req = Request.Builder()
        .url(webHookUrl)
        .post(payload.toRequestBody("application/json".toMediaType()))
        .build()

    // 3) Один вызов, логируем статус и тело
    val client = OkHttpClient()
    val (code, body) = withContext(Dispatchers.IO) {
        client.newCall(req).execute().use { r ->
            r.code to (r.body?.string().orEmpty())
        }
    }
    Log.d("WEBHOOK", "url=$webHookUrl code=$code body=$body")

    if (code !in 200..299) {
        Log.e("WEBHOOK", "Webhook failed: $code $body")
        return // без throw, просто выходим
    }
}

suspend fun notifyCancelViaAppsScript(
    webHookUrl: String,
    calleeUid: String,
    callerUid: String,
    hookSecret: String? = null
) {
    val tokens = (Firebase.firestore.collection("users").document(calleeUid)
        .get().await().get("fcmTokens") as? List<*>)?.filterIsInstance<String>().orEmpty()
    if (tokens.isEmpty()) return

    val payload = JSONObject().apply {
        put("type", "call_cancel")      // ← отличаемся от call
        put("tokens", JSONArray(tokens))
        put("fromUid", callerUid)
        hookSecret?.takeIf { it.isNotBlank() }?.let { put("secret", it) }
    }.toString()

    val req = Request.Builder()
        .url(webHookUrl)
        .post(payload.toRequestBody("application/json".toMediaType()))
        .build()

    OkHttpClient().newCall(req).execute().use { /* лог по желанию */ }
}