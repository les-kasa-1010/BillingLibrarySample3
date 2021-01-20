package com.example.billingsample.purchase

import com.android.billingclient.api.Purchase


/**
 *  Purchaseクラスを吸収するためのtype alias
 */

typealias PurchaseImpl = Purchase

fun PurchaseImpl.isPending(): Boolean {
    return purchaseState == Purchase.PurchaseState.PENDING
}
