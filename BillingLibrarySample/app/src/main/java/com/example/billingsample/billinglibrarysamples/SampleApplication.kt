package com.example.billingsample.billinglibrarysamples

import android.app.Application
import com.example.billingsample.model.PurchaseDatabase
import com.example.billingsample.model.PurchaseLogRepository

/**
 * サンプルアプリケーションクラス
 */
class SampleApplication : Application() {
    private val database by lazy { PurchaseDatabase.getDatabase(this) }
    val repository by lazy { PurchaseLogRepository(database.purchaseDao()) }
}
