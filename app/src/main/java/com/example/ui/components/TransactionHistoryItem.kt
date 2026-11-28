package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.local.CategoryEntity
import com.example.data.local.SyncStates
import com.example.data.local.TransactionEntity
import com.example.data.local.TxTypes
import com.example.domain.CurrencyFormatter
import com.example.ui.theme.*

data class CategoryVisualSpec(
    val icon: ImageVector,
    val baseColor: Color
)

fun resolveCategoryVisual(
    category: CategoryEntity?,
    txType: String
): CategoryVisualSpec {
    if (txType == TxTypes.TRANSFER) {
        return CategoryVisualSpec(Icons.Rounded.SwapHoriz, TransferBlue)
    }
    if (txType == TxTypes.SPLIT) {
        return CategoryVisualSpec(Icons.Rounded.CallSplit, VioletAccent)
    }

    val key = category?.iconKey.orEmpty().lowercase()
    val name = category?.name.orEmpty().lowercase()
    val catColor = category?.colorHex?.takeIf { it != 0L }?.let { Color(it) }

    val icon = when {
        key == "restaurant" || name.contains("makan") || name.contains("minum") || name.contains("kopi") -> Icons.Rounded.Restaurant
        key == "directions_car" || name.contains("transport") || name.contains("bbm") || name.contains("ojek") -> Icons.Rounded.DirectionsCar
        key == "receipt_long" || name.contains("tagihan") || name.contains("utilitas") || name.contains("listrik") || name.contains("internet") -> Icons.Rounded.ReceiptLong
        key == "home" || name.contains("hunian") || name.contains("sewa") || name.contains("rumah") || name.contains("kos") -> Icons.Rounded.Home
        key == "shopping_bag" || name.contains("belanja") || name.contains("kebutuhan") || name.contains("shop") -> Icons.Rounded.ShoppingBag
        key == "movie" || name.contains("hiburan") || name.contains("langganan") || name.contains("game") -> Icons.Rounded.Movie
        key == "medical_services" || name.contains("kesehatan") || name.contains("obat") || name.contains("dokter") -> Icons.Rounded.MedicalServices
        key == "school" || name.contains("pendidikan") || name.contains("kursus") -> Icons.Rounded.School
        key == "flight" || name.contains("travel") || name.contains("liburan") -> Icons.Rounded.Flight
        key == "pets" || name.contains("hewan") -> Icons.Rounded.Pets
        key == "payments" || key == "work" -> Icons.Rounded.Payments
        key == "trending_up" -> Icons.Rounded.TrendingUp
        txType == TxTypes.INCOME || txType == TxTypes.REFUND -> Icons.Rounded.Savings
        else -> Icons.Rounded.ShoppingCartCheckout
    }

    val defaultColor = if (txType == TxTypes.INCOME || txType == TxTypes.REFUND) IncomeGreen else ExpenseRed
    return CategoryVisualSpec(
        icon = icon,
        baseColor = catColor ?: defaultColor
    )
}

@Composable
fun CategoryExpenseIconBadge(
    category: CategoryEntity?,
    txType: String,
    contentDescription: String?,
    size: Dp = 46.dp,
    modifier: Modifier = Modifier
) {
    val activePreset = LocalAppThemePreset.current
    val activePhase = LocalActiveTimePastelPhase.current
    val isDark = activePhase?.isDark ?: activePreset.isDark

    val spec = remember(category?.id, category?.iconKey, category?.name, category?.colorHex, txType) {
        resolveCategoryVisual(category, txType)
    }
    val iconTint = remember(spec.baseColor, isDark) {
        if (isDark) lerp(spec.baseColor, Color.White, 0.34f) else spec.baseColor
    }
    val containerBg = remember(spec.baseColor, isDark) {
        spec.baseColor.copy(alpha = if (isDark) 0.22f else 0.13f)
    }
    val borderColor = remember(spec.baseColor, isDark) {
        spec.baseColor.copy(alpha = if (isDark) 0.40f else 0.24f)
    }

    Surface(
        modifier = modifier.size(size),
        shape = RoundedCornerShape(14.dp),
        color = containerBg,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = spec.icon,
                contentDescription = contentDescription,
                tint = iconTint,
                modifier = Modifier.size(size * 0.52f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeableTransactionHistoryItem(
    tx: TransactionEntity,
    category: CategoryEntity?,
    accountName: String,
    toAccountName: String?,
    stealthMode: Boolean,
    canEdit: Boolean,
    onDelete: (TransactionEntity) -> Unit,
    modifier: Modifier = Modifier,
    onClick: ((TransactionEntity) -> Unit)? = null,
    showAuthorSubtitle: Boolean = false,
    extraContent: (@Composable ColumnScope.() -> Unit)? = null
) {
    val currentCanEdit by rememberUpdatedState(canEdit)
    val currentOnDelete by rememberUpdatedState(onDelete)
    val currentTx by rememberUpdatedState(tx)

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { target ->
            target == SwipeToDismissBoxValue.EndToStart && currentCanEdit
        },
        positionalThreshold = { totalDistance -> totalDistance * 0.35f }
    )

    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart && currentCanEdit) {
            currentOnDelete(currentTx)
        }
    }

    val isIncome = tx.type == TxTypes.INCOME || tx.type == TxTypes.REFUND
    val isTransfer = tx.type == TxTypes.TRANSFER
    val isExpense = tx.type == TxTypes.EXPENSE || tx.type == TxTypes.SPLIT
    val amtColor = when {
        isIncome -> IncomeGreen
        isTransfer -> TransferBlue
        else -> ExpenseRed
    }
    val categoryLabel = category?.name ?: when (tx.type) {
        TxTypes.EXPENSE -> "Pengeluaran"
        TxTypes.INCOME -> "Pemasukan"
        TxTypes.TRANSFER -> "Pindah Saldo"
        TxTypes.SPLIT -> "Patungan / Rinci"
        else -> CurrencyFormatter.txTypeLabel(tx.type)
    }
    val accLabel = if (isTransfer && toAccountName != null) {
        "$accountName → $toAccountName"
    } else {
        accountName
    }

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = canEdit,
        modifier = modifier.testTag("swipe_tx_${tx.id}"),
        backgroundContent = {
            val isTriggered = dismissState.targetValue == SwipeToDismissBoxValue.EndToStart
            val bgColor by animateColorAsState(
                targetValue = if (isTriggered) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.errorContainer
                },
                label = "swipe_delete_bg"
            )
            val fgColor = if (isTriggered) {
                MaterialTheme.colorScheme.onError
            } else {
                MaterialTheme.colorScheme.onErrorContainer
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(MaterialTheme.shapes.medium)
                    .background(bgColor)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (isTriggered) "Lepas untuk hapus" else "Hapus",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = fgColor
                    )
                    Icon(
                        imageVector = Icons.Rounded.DeleteSweep,
                        contentDescription = "Hapus transaksi",
                        tint = fgColor
                    )
                }
            }
        }
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            shape = MaterialTheme.shapes.medium,
            modifier = if (canEdit && onClick != null) {
                Modifier
                    .clip(MaterialTheme.shapes.medium)
                    .clickable { onClick(tx) }
            } else Modifier
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(13.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CategoryExpenseIconBadge(
                        category = category,
                        txType = tx.type,
                        contentDescription = categoryLabel,
                        size = 46.dp
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = tx.title,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (tx.syncStatus == SyncStates.PENDING) {
                                Surface(
                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "Di HP",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "$categoryLabel • $accLabel",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (showAuthorSubtitle) {
                                "Dicatat oleh ${tx.createdByName} • ${CurrencyFormatter.formatRelativeDateTime(tx.dateMillis)}"
                            } else {
                                "Oleh ${tx.createdByName} • ${CurrencyFormatter.formatRelativeDateTime(tx.dateMillis)}"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = when {
                                isIncome -> "+${CurrencyFormatter.formatIdr(tx.amount, stealthMode)}"
                                isTransfer -> CurrencyFormatter.formatIdr(tx.amount, stealthMode)
                                else -> "-${CurrencyFormatter.formatIdr(tx.amount, stealthMode)}"
                            },
                            style = MoneyMediumStyle,
                            color = amtColor,
                            fontWeight = FontWeight.Bold
                        )
                        if (isExpense) {
                            Text(
                                text = categoryLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (tx.adminFee > 0L) {
                            Text(
                                text = "Admin: ${CurrencyFormatter.formatIdr(tx.adminFee, stealthMode)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                if (extraContent != null) {
                    extraContent()
                }
            }
        }
    }
}
