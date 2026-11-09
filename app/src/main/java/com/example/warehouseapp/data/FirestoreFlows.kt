package com.example.warehouseapp.data

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Живой список документов: экран обновляется сам, когда кто-то меняет данные. */
fun <T> Query.listFlow(map: (DocumentSnapshot) -> T): Flow<List<T>> = callbackFlow {
    val reg = addSnapshotListener { snap, err ->
        if (err != null) { close(err); return@addSnapshotListener }
        if (snap != null) trySend(snap.documents.map(map))
    }
    awaitClose { reg.remove() }
}

fun <T> DocumentReference.docFlow(map: (DocumentSnapshot) -> T): Flow<T?> = callbackFlow {
    val reg = addSnapshotListener { snap, err ->
        if (err != null) { close(err); return@addSnapshotListener }
        trySend(if (snap != null && snap.exists()) map(snap) else null)
    }
    awaitClose { reg.remove() }
}
