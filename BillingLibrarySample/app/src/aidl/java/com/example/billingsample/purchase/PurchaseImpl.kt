package com.example.billingsample.purchase

import com.example.billingsample.purchase.util.Purchase

/**
 *  Purchaseクラスを吸収するためのtype alias
 */

typealias PurchaseImpl = Purchase

fun PurchaseImpl.isPending() = false
