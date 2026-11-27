package com.example.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.data.local.AccountEntity
import com.example.data.local.CategoryEntity
import com.example.data.local.TxTypes
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionEntryDialog(
    accounts: List<AccountEntity>,
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onSave: (
        type: String,
        amount: Long,
        accountId: String,
        toAccountId: String?,
        adminFee: Long,
        categoryId: String,
        title: String,
        note: String,
        tags: String,
        dateMillis: Long,
        receiptUri: Uri?,
        splitItems: List<Triple<String, String, Long>>
    ) -> Unit
) {
    var txType by remember { mutableStateOf(TxTypes.EXPENSE) }
    var title by remember { mutableStateOf("") }
    var amountStr by remember { mutableStateOf("") }
    var adminFeeStr by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("") }
    var selectedAccountId by remember(accounts) { mutableStateOf(accounts.firstOrNull()?.id ?: "") }
    var selectedToAccountId by remember(accounts) { mutableStateOf(accounts.getOrNull(1)?.id ?: accounts.firstOrNull()?.id ?: "") }

    val filteredCategories = remember(txType, categories) {
        when (txType) {
            TxTypes.INCOME, TxTypes.REFUND -> categories.filter { it.type == TxTypes.INCOME }
            TxTypes.TRANSFER -> categories.filter { it.type == TxTypes.TRANSFER }
            else -> categories.filter { it.type == TxTypes.EXPENSE }
        }.ifEmpty { categories }
    }
    var selectedCategoryId by remember(filteredCategories) {
        mutableStateOf(filteredCategories.firstOrNull()?.id ?: "")
    }

    var receiptUri by remember { mutableStateOf<Uri?>(null) }
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) receiptUri = uri
    }

    // Split Transaction line items
    data class SplitDraft(val catId: String, val desc: String, val amount: String)
    var splitDrafts by remember {
        mutableStateOf(
            listOf(
                SplitDraft(filteredCategories.firstOrNull()?.id ?: "", "Item 1", ""),
                SplitDraft(filteredCategories.lastOrNull()?.id ?: "", "Item 2", "")
            )
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .widthIn(max = 560.dp)
                .fillMaxHeight(0.90f),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Catat Transaksi Baru", style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Tutup")
                    }
                }

                Spacer(Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    val types = listOf(
                        TxTypes.EXPENSE to "Keluar",
                        TxTypes.INCOME to "Masuk",
                        TxTypes.TRANSFER to "Transfer",
                        TxTypes.SPLIT to "Split"
                    )
                    types.forEachIndexed { idx, (typeKey, label) ->
                        SegmentedButton(
                            selected = txType == typeKey,
                            onClick = { txType = typeKey },
                            shape = SegmentedButtonDefaults.itemShape(idx, types.size)
                        ) {
                            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = { Text("Judul / Deskripsi") },
                            placeholder = { Text("mis. Belanja Bulanan / Gaji") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("tx_title_input")
                        )
                    }

                    if (txType != TxTypes.SPLIT) {
                        item {
                            OutlinedTextField(
                                value = amountStr,
                                onValueChange = { amountStr = it.filter(Char::isDigit) },
                                label = { Text("Nominal (Rp)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("tx_amount_input")
                            )
                        }
                    }

                    item {
                        Text(
                            text = if (txType == TxTypes.TRANSFER) "Dari Akun" else "Pilih Akun",
                            style = MaterialTheme.typography.labelLarge
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            accounts.take(4).forEach { acc ->
                                FilterChip(
                                    selected = selectedAccountId == acc.id,
                                    onClick = { selectedAccountId = acc.id },
                                    label = { Text(acc.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                )
                            }
                        }
                        if (accounts.size > 4) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                accounts.drop(4).forEach { acc ->
                                    FilterChip(
                                        selected = selectedAccountId == acc.id,
                                        onClick = { selectedAccountId = acc.id },
                                        label = { Text(acc.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                    )
                                }
                            }
                        }
                    }

                    if (txType == TxTypes.TRANSFER) {
                        item {
                            Text("Ke Akun Tujuan", style = MaterialTheme.typography.labelLarge)
                            Spacer(Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                accounts.take(4).forEach { acc ->
                                    FilterChip(
                                        selected = selectedToAccountId == acc.id,
                                        onClick = { selectedToAccountId = acc.id },
                                        label = { Text(acc.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                    )
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            OutlinedTextField(
                                value = adminFeeStr,
                                onValueChange = { adminFeeStr = it.filter(Char::isDigit) },
                                label = { Text("Biaya Admin Transfer (Rp, opsional)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    } else if (txType != TxTypes.SPLIT) {
                        item {
                            Text("Kategori", style = MaterialTheme.typography.labelLarge)
                            Spacer(Modifier.height(4.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                filteredCategories.chunked(2).forEach { rowCats ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                        rowCats.forEach { cat ->
                                            FilterChip(
                                                selected = selectedCategoryId == cat.id,
                                                onClick = { selectedCategoryId = cat.id },
                                                label = { Text(cat.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                        if (rowCats.size == 1) Spacer(Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }

                    if (txType == TxTypes.SPLIT) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Rincian Split Item", style = MaterialTheme.typography.titleSmall)
                                TextButton(onClick = {
                                    splitDrafts = splitDrafts + SplitDraft(filteredCategories.firstOrNull()?.id ?: "", "Item ${splitDrafts.size + 1}", "")
                                }) {
                                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Text("Tambah Item")
                                }
                            }
                        }
                        itemsIndexed(splitDrafts) { idx, draft ->
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                )
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        OutlinedTextField(
                                            value = draft.desc,
                                            onValueChange = { valStr ->
                                                splitDrafts = splitDrafts.toMutableList().also { it[idx] = draft.copy(desc = valStr) }
                                            },
                                            label = { Text("Item #${idx + 1}") },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        OutlinedTextField(
                                            value = draft.amount,
                                            onValueChange = { valStr ->
                                                splitDrafts = splitDrafts.toMutableList().also { it[idx] = draft.copy(amount = valStr.filter(Char::isDigit)) }
                                            },
                                            label = { Text("Nominal") },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            singleLine = true,
                                            modifier = Modifier.width(120.dp)
                                        )
                                        if (splitDrafts.size > 2) {
                                            IconButton(onClick = {
                                                splitDrafts = splitDrafts.toMutableList().also { it.removeAt(idx) }
                                            }) {
                                                Icon(Icons.Filled.Delete, contentDescription = "Hapus item")
                                            }
                                        }
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        filteredCategories.take(3).forEach { cat ->
                                            FilterChip(
                                                selected = draft.catId == cat.id,
                                                onClick = {
                                                    splitDrafts = splitDrafts.toMutableList().also { it[idx] = draft.copy(catId = cat.id) }
                                                },
                                                label = { Text(cat.name.split(" ").first(), style = MaterialTheme.typography.labelMedium) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item {
                        OutlinedTextField(
                            value = tags,
                            onValueChange = { tags = it },
                            label = { Text("Tag (#kopi, #keluarga)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    item {
                        OutlinedTextField(
                            value = note,
                            onValueChange = { note = it },
                            label = { Text("Catatan") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    item {
                        OutlinedButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier.fillMaxWidth().testTag("attach_receipt_button")
                        ) {
                            Icon(Icons.Outlined.ReceiptLong, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (receiptUri != null) "Struk Terlampir (Ganti Foto)" else "Lampirkan Foto Struk (Offline)")
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = {
                        val splitParsed = if (txType == TxTypes.SPLIT) {
                            splitDrafts.mapNotNull {
                                val a = it.amount.toLongOrNull() ?: 0L
                                if (a > 0L) Triple(it.catId, it.desc.ifBlank { "Item" }, a) else null
                            }
                        } else emptyList()
                        val amount = if (txType == TxTypes.SPLIT) splitParsed.sumOf { it.third } else (amountStr.toLongOrNull() ?: 0L)
                        if (amount > 0L && selectedAccountId.isNotBlank()) {
                            onSave(
                                txType,
                                amount,
                                selectedAccountId,
                                selectedToAccountId,
                                adminFeeStr.toLongOrNull() ?: 0L,
                                selectedCategoryId,
                                title,
                                note,
                                tags,
                                System.currentTimeMillis(),
                                receiptUri,
                                splitParsed
                            )
                            onDismiss()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp).testTag("save_transaction_button")
                ) {
                    Text("Simpan Transaksi")
                }
            }
        }
    }
}

@Composable
fun ReceiptPreviewDialog(
    imagePath: String,
    title: String,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Struk: $title", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Tutup")
                    }
                }
                Spacer(Modifier.height(8.dp))
                val receiptBitmap by produceState<ImageBitmap?>(initialValue = null, imagePath) {
                    value = withContext(Dispatchers.IO) {
                        val file = File(imagePath)
                        if (file.exists()) {
                            BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
                        } else null
                    }
                }
                val bmp = receiptBitmap
                if (bmp != null) {
                    Image(
                        bitmap = bmp,
                        contentDescription = "Foto struk offline",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp)
                    )
                } else {
                    Text("Memuat struk...", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
