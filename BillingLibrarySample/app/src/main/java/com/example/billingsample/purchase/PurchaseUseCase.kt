package com.example.billingsample.purchase

import android.app.Activity
import android.content.Intent
import com.example.billingsample.model.PurchaseData
import kotlinx.coroutines.flow.StateFlow

/**
 * 購入処理をまとめたUseCaseクラス
 * 責任を持つのは課金/消費処理に関わる部分だけで、DBにステータスを保存したりAPIを呼んだりはViewModelで行う
 * AIDL/PBLで実装を分けるために抽象クラスとしている
 */
interface PurchaseUseCase {

    // 初期化が完了し課金可能かどうかを監視するためのStateFlow
    // ViewModelでこの値をLiveDataとして監視し、ActivityはそのLiveDataを監視して、初期化完了メッセージを出している
    val enabled: StateFlow<Boolean>

    // 初期化
    suspend fun init(activity: Activity)

    // 購入
    suspend fun purchaseItem(activity: Activity, sku: String): PurchaseImpl?

    // 購入結果のハンドリング(※Activity#onActivityResultで呼び出す用。AIDL版のみ中身は実装)
    fun handlePurchaseResult(requestCode: Int, resultCode: Int, data: Intent?) = false

    // 購入情報の取得(未消費レシート)
    suspend fun queryPurchaseData(): List<PurchaseImpl>

    // 購入できるかのチェック
    fun isSupported(): Boolean

    // レシートの消費処理
    suspend fun consumePurchase(purchase: PurchaseImpl): Boolean

    // Purchaseクラス作成ラッパー用
    fun createPurchaseImpl(purchaseData: PurchaseData): PurchaseImpl
}
