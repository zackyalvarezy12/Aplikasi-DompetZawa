package com.example.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.AccountTypes
import com.example.data.local.TxTypes

@Composable
fun SvgLogo(
    @DrawableRes resId: Int,
    contentDescription: String?,
    size: Dp = 36.dp,
    modifier: Modifier = Modifier
) {
    Image(
        painter = painterResource(id = resId),
        contentDescription = contentDescription,
        contentScale = ContentScale.Fit,
        modifier = modifier.size(size)
    )
}

@DrawableRes
fun accountLogoRes(accountType: String): Int = when (accountType) {
    AccountTypes.CASH -> R.drawable.ic_logo_acc_cash
    AccountTypes.BANK -> R.drawable.ic_logo_acc_bank
    AccountTypes.EWALLET -> R.drawable.ic_logo_acc_ewallet
    AccountTypes.CREDIT_CARD -> R.drawable.ic_logo_acc_card
    AccountTypes.DEPOSIT -> R.drawable.ic_logo_acc_deposit
    AccountTypes.INVESTMENT -> R.drawable.ic_logo_asset_stocks
    else -> R.drawable.ic_logo_acc_bank
}

@DrawableRes
fun assetLogoRes(assetClass: String): Int = when (assetClass) {
    "GOLD" -> R.drawable.ic_logo_asset_gold
    "PROPERTY" -> R.drawable.ic_logo_asset_property
    else -> R.drawable.ic_logo_asset_stocks
}

@DrawableRes
fun categoryLogoRes(iconKey: String?, txType: String): Int {
    if (txType == TxTypes.TRANSFER) return R.drawable.ic_logo_cat_transfer
    if (txType == TxTypes.SPLIT) return R.drawable.ic_logo_split_bill
    return when (iconKey) {
        "payments", "work" -> R.drawable.ic_logo_cat_salary
        "trending_up" -> R.drawable.ic_logo_asset_stocks
        "restaurant" -> R.drawable.ic_logo_cat_food
        "directions_car" -> R.drawable.ic_logo_cat_transport
        "receipt_long" -> R.drawable.ic_logo_cat_bills
        "home" -> R.drawable.ic_logo_asset_property
        "shopping_bag" -> R.drawable.ic_logo_cat_shopping
        "movie" -> R.drawable.ic_logo_cat_fun
        "medical_services" -> R.drawable.ic_logo_cat_health
        "swap_horiz" -> R.drawable.ic_logo_cat_transfer
        else -> if (txType == TxTypes.INCOME || txType == TxTypes.REFUND) {
            R.drawable.ic_logo_cat_salary
        } else {
            R.drawable.ic_logo_cat_bills
        }
    }
}
