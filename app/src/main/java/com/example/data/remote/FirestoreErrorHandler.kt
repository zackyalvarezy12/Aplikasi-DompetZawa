package com.example.data.remote

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import org.json.JSONObject

enum class OperationType {
    CREATE,
    UPDATE,
    DELETE,
    LIST,
    GET,
    WRITE
}

data class FirestoreErrorInfo(
    val error: String,
    val operationType: OperationType,
    val path: String?,
    val userId: String?,
    val code: String
) {
    fun toJsonString(): String = JSONObject().apply {
        put("error", error)
        put("operationType", operationType.name.lowercase())
        put("path", path ?: "")
        put("userId", userId ?: "")
        put("code", code)
    }.toString()
}

fun handleFirestoreError(
    e: Throwable,
    operationType: OperationType,
    path: String?
): Exception {
    if (e is CancellationException) throw e
    val code = (e as? FirebaseFirestoreException)?.code?.name ?: e::class.java.simpleName
    val errInfo = FirestoreErrorInfo(
        error = e.message ?: "Unknown Firestore error",
        operationType = operationType,
        path = path,
        userId = runCatching { Firebase.auth.currentUser?.uid }.getOrNull(),
        code = code
    )
    val json = errInfo.toJsonString()
    Log.e("FirestoreError", json, e)
    return RuntimeException("Firestore Error: $json", e)
}
