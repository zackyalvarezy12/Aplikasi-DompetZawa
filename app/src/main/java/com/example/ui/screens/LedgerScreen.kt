package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.SyncStates
import com.example.data.local.TransactionEntity
import com.example.data.local.TxTypes
import com.example.domain.CurrencyFormatter
import com.example.ui.MainUiState
import com.example.ui.components.ReceiptPreviewDialog
import com.example.ui.components.SvgLogo
import com.example.ui.components.SwipeableTransactionHistoryItem
import com.example.ui.theme.*

private val TYPE_FILTER_CHIPS = listOf(
    "ALL" to "Semua",
    TxTypes.EXPENSE to "Keluar",
    TxTypes.INCOME to "Masuk",
    TxTypes.TRANSFER to "Transfer",
    TxTypes.SPLIT to "Split"
)

@Composable
fun LedgerScreen(
    state: MainUiState,
    onQuickInputSubmit: (String) -> Boolean,
    onOpenNewTransaction: () -> Unit,
    onDeleteTransaction: (TransactionEntity) -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    val stealth = state.settings.isStealthMode
    val listState = rememberLazyListState()
    var searchQuery by remember { mutableStateOf("") }
    var selectedTypeFilter by remember { mutableStateOf("ALL") }
    var selectedMemberFilter by remember { mutableStateOf("ALL") }
    var selectedAccountFilter by remember { mutableStateOf("ALL") }
    var previewReceiptTx by remember { mutableStateOf<TransactionEntity?>(null) }

    val sourceTransactions = state.pagedTransactions
    val filteredTx = remember(
        sourceTransactions,
        searchQuery,
        selectedTypeFilter,
        selectedMemberFilter,
        selectedAccountFilter
    ) {
        val q = searchQuery.trim().lowercase()
        sourceTransactions.filter { tx ->
            val matchType = selectedTypeFilter == "ALL" || tx.type == selectedTypeFilter
            val matchMember = selectedMemberFilter == "ALL" || tx.createdByMemberId == selectedMemberFilter
            val matchAcc = selectedAccountFilter == "ALL" ||
                tx.accountId == selectedAccountFilter ||
                tx.toAccountId == selectedAccountFilter
            val matchSearch = q.isEmpty() ||
                tx.title.lowercase().contains(q) ||
                tx.tags.lowercase().contains(q) ||
                tx.note.lowercase().contains(q)
            matchType && matchMember && matchAcc && matchSearch
        }
    }

    val shouldLoadMore by remember(state.hasMoreTransactions) {
        derivedStateOf {
            if (!state.hasMoreTransactions) false
            else {
                val layoutInfo = listState.layoutInfo
                val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                val totalItems = layoutInfo.totalItemsCount
                totalItems > 0 && lastVisible >= totalItems - 4
            }
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            onLoadMore()
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Isolated Smart Quick-Input Header (typing does not recompose the ledger list)
        item(key = "ledger_quick_card", contentType = "quick_input") {
            LedgerQuickInputCard(
                canEdit = state.canEdit,
                onQuickInputSubmit = onQuickInputSubmit,
                onOpenNewTransaction = onOpenNewTransaction
            )
        }

        // Search & Filters
        item(key = "ledger_search", contentType = "search") {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                placeholder = { Text("Cari judul, catatan, atau #tag...") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("ledger_search_input")
            )
        }

        item(key = "type_filter_row", contentType = "filter_row") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(
                    items = TYPE_FILTER_CHIPS,
                    key = { it.first },
                    contentType = { "filter_chip" }
                ) { (key, label) ->
                    FilterChip(
                        selected = selectedTypeFilter == key,
                        onClick = { selectedTypeFilter = key },
                        label = { Text(label) }
                    )
                }
            }
        }

        if (state.isSharedWorkspace && state.members.size > 1) {
            item(key = "member_filter_row", contentType = "filter_row") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item(key = "member_all", contentType = "filter_chip") {
                        FilterChip(
                            selected = selectedMemberFilter == "ALL",
                            onClick = { selectedMemberFilter = "ALL" },
                            label = { Text("Semua Anggota") }
                        )
                    }
                    items(
                        items = state.members,
                        key = { it.id },
                        contentType = { "filter_chip" }
                    ) { member ->
                        FilterChip(
                            selected = selectedMemberFilter == member.id,
                            onClick = { selectedMemberFilter = member.id },
                            label = { Text(member.name) }
                        )
                    }
                }
            }
        }

        item(key = "account_filter_row", contentType = "filter_row") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item(key = "acc_all", contentType = "filter_chip") {
                    FilterChip(
                        selected = selectedAccountFilter == "ALL",
                        onClick = { selectedAccountFilter = "ALL" },
                        label = { Text("Semua Akun") }
                    )
                }
                items(
                    items = state.accounts,
                    key = { it.id },
                    contentType = { "filter_chip" }
                ) { acc ->
                    FilterChip(
                        selected = selectedAccountFilter == acc.id,
                        onClick = { selectedAccountFilter = acc.id },
                        label = { Text(acc.name) }
                    )
                }
            }
        }

        if (filteredTx.isEmpty()) {
            item(key = "empty_ledger", contentType = "empty") {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SvgLogo(
                            resId = R.drawable.ic_logo_empty_state,
                            contentDescription = null,
                            size = 64.dp
                        )
                        Text(
                            "Tidak ada transaksi yang cocok dengan filter.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(
                items = filteredTx,
                key = { it.id },
                contentType = { "ledger_tx" }
            ) { tx ->
                val cat = state.categoryMap[tx.categoryId]
                val acc = state.accountMap[tx.accountId]
                val toAcc = tx.toAccountId?.let { state.accountMap[it] }
                val splitItems = state.splitItemsByTxId[tx.id].orEmpty()

                SwipeableTransactionHistoryItem(
                    tx = tx,
                    category = cat,
                    accountName = acc?.name ?: "-",
                    toAccountName = toAcc?.name,
                    stealthMode = stealth,
                    canEdit = state.canEdit,
                    onDelete = onDeleteTransaction,
                    showAuthorSubtitle = true,
                    modifier = Modifier.animateItem(),
                    extraContent = if (splitItems.isNotEmpty() || tx.tags.isNotBlank() || tx.receiptImagePath != null || state.canEdit) {
                        {
                            if (splitItems.isNotEmpty()) {
                                Spacer(Modifier.height(6.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    contentColor = MaterialTheme.colorScheme.onSurface,
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        splitItems.forEach { item ->
                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text("• ${item.description} (${state.categoryMap[item.categoryId]?.name ?: ""})", style = MaterialTheme.typography.bodySmall)
                                                Text(CurrencyFormatter.formatIdr(item.amount, stealth), style = MoneySmallStyle)
                                            }
                                        }
                                    }
                                }
                            }

                            if (tx.tags.isNotBlank() || tx.receiptImagePath != null || state.canEdit) {
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        if (tx.tags.isNotBlank()) {
                                            Text(tx.tags, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                        }
                                        if (tx.receiptImagePath != null) {
                                            AssistChip(
                                                onClick = { previewReceiptTx = tx },
                                                label = { Text("Lihat Struk") },
                                                leadingIcon = { Icon(Icons.Outlined.ReceiptLong, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                            )
                                        }
                                    }
                                    if (state.canEdit) {
                                        IconButton(
                                            onClick = { onDeleteTransaction(tx) },
                                            modifier = Modifier.size(48.dp)
                                        ) {
                                            Icon(Icons.Filled.DeleteOutline, contentDescription = "Hapus transaksi", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    } else null
                )
            }

            if (state.hasMoreTransactions) {
                item(key = "load_more_footer", contentType = "footer") {
                    OutlinedButton(
                        onClick = onLoadMore,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ledger_load_more_button")
                    ) {
                        Text("Muat Lebih Banyak (${state.pagedTransactions.size}/${state.totalTransactionCount})")
                    }
                }
            }
        }
    }

    previewReceiptTx?.receiptImagePath?.let { path ->
        ReceiptPreviewDialog(
            imagePath = path,
            title = previewReceiptTx?.title ?: "Struk",
            onDismiss = { previewReceiptTx = null }
        )
    }
}

@Composable
private fun LedgerQuickInputCard(
    canEdit: Boolean,
    onQuickInputSubmit: (String) -> Boolean,
    onOpenNewTransaction: () -> Unit
) {
    var quickText by remember { mutableStateOf("") }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    SvgLogo(resId = R.drawable.ic_logo_report_ledger, contentDescription = null, size = 32.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Buku Besar", style = MaterialTheme.typography.titleMedium)
                }
                FilledTonalButton(
                    onClick = onOpenNewTransaction,
                    enabled = canEdit,
                    modifier = Modifier.testTag("ledger_add_tx_button")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Form Lengkap")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = quickText,
                    onValueChange = { quickText = it },
                    placeholder = { Text("Beli kopi 28rb pakai GoPay #nongkrong") },
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag("ledger_quick_input")
                )
                Button(
                    onClick = {
                        if (onQuickInputSubmit(quickText)) quickText = ""
                    },
                    enabled = quickText.isNotBlank() && canEdit,
                    modifier = Modifier.height(52.dp)
                ) {
                    Text("Input")
                }
            }
        }
    }
}
