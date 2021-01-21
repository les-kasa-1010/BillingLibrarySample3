package com.example.billingsample.purchase

import android.app.Activity
import android.util.Log
import com.android.billingclient.api.*
import com.example.billingsample.model.PurchaseData
import com.example.billingsample.model.SKU_ITEM_100
import com.example.billingsample.model.SKU_ITEM_10000
import com.example.billingsample.model.SKU_STATIC_TEST
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Play Billing Library使用時の課金処理クラス
 */
class PurchaseUseCaseImpl : PurchaseUseCase {
    companion object {
        const val TAG = "PurchaseUseCasePBL"
        val INAPP_SKUS =
            listOf(SKU_ITEM_100, SKU_ITEM_10000, SKU_STATIC_TEST)
    }

    override val enabled: StateFlow<Boolean>
        get() = _enabled
    private val _enabled = MutableStateFlow(false)

    private val skuDetailsMap = mutableMapOf<String, SkuDetails>()

    private lateinit var billingClient: BillingClient

    // 進行中の購入を管理するためのMap
    // アプリ落とされたあとの、保留トランザクション対応は別に考えないとダメ
    private val pendingPurchaseFlows = HashMap<String, CompletableDeferred<Purchase?>>()


    // 購入情報の変化を受け取るリスナー
    private val purchasesUpdatedListener =
        PurchasesUpdatedListener { billingResult, purchases ->
            Log.d(TAG, "onPurchasesUpdated.")

            // 購入完了後の処理(AIDL時代のonActivityResult後の処理と同等。ただし、保留トランザクション対応のため、
            // この関数はアプリのonResumeなどでも呼ばれないと行けないらしい。
            // 購入結果が1件とは限らないため、リストで返るのが難点か)
            when (billingResult.responseCode) {
                BillingClient.BillingResponseCode.OK -> {
                    // will handle server verification, consumables, and updating the local cache
                    purchases?.apply {
                        for (purchase in purchases) {
                            when (purchase.purchaseState) {
                                Purchase.PurchaseState.PURCHASED -> {
                                    // static responseは署名データがないので署名チェックはパス
                                    if (SKU_STATIC_TEST == purchase.sku || isSignatureValid(purchase)) {
                                        Log.d(TAG, "Purchase successful.")
                                        Log.d(
                                            TAG,
                                            "purchase attrs = ${purchase.accountIdentifiers?.obfuscatedAccountId} : " +
                                                    "${purchase.accountIdentifiers?.obfuscatedProfileId} "
                                        )
                                        val deferred = pendingPurchaseFlows.remove(purchase.sku)
                                        deferred?.complete(purchase)
                                        // deferredがnullの場合は、以前の保留トランザクションが外部で完了したことになる
                                        // (コンビニで支払ったなど)
                                    }
                                }
                                Purchase.PurchaseState.PENDING -> {
                                    Log.d(TAG, "Purchase is pending.")
                                  val deferred = pendingPurchaseFlows.remove(purchase.sku)
                                    deferred?.complete(purchase)
                                }
                                else -> {
                                    Log.d(TAG, "Purchase status is UNSPECIFIED.")
                                }
                            }
                        }
                    }
                }
                BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                    // item already owned? call queryPurchasesAsync to verify and process all such items
                    Log.d(TAG, billingResult.debugMessage)
                }
                BillingClient.BillingResponseCode.SERVICE_DISCONNECTED -> {
                    connectToPlayBillingService()
                }
                else -> {
                    Log.i(TAG, billingResult.debugMessage)
                }
            }
            val iterator = pendingPurchaseFlows.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                entry.value.complete(null)
                iterator.remove()
            }
        }

    //////////////////////////////////////////////////////////////////////////////////
    // PurchaseUseCase implementation.

    override suspend fun init(activity: Activity) {
        billingClient = BillingClient.newBuilder(activity)
            .setListener(purchasesUpdatedListener)
            .enablePendingPurchases() // 有効化しないといけないらしい(クラッシュする)
            .build()

        connectToPlayBillingService()
    }

    // playストアとの接続
    private fun connectToPlayBillingService() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    // The BillingClient is ready. You can query purchases here.
                    Log.d(TAG, "Setup successful. ")

                    // 購入の際に詳細情報が必要となるため取得しておく
                    querySkuDetailsAsync(BillingClient.SkuType.INAPP, INAPP_SKUS)
                } else {
                    Log.e(TAG, "Problem setting up in-app billing: $billingResult")
                    _enabled.value = false
                }
            }

            override fun onBillingServiceDisconnected() {
                // Try to restart the connection on the next request to
                // Google Play by calling the startConnection() method.
                Log.e(TAG, "Problem setting up in-app billing: [onBillingServiceDisconnected]]")
                _enabled.value = false
            }
        })
    }

    /**
     * Presumably a set of SKUs has been defined on the Google Play Developer Console. This
     * method is for requesting a (improper) subset of those SKUs. Hence, the method accepts a list
     * of product IDs and returns the matching list of SkuDetails.
     *
     * The result is passed to [onSkuDetailsResponse]
     */
    private fun querySkuDetailsAsync(
        @BillingClient.SkuType skuType: String,
        skuList: List<String>
    ) {
        val params = SkuDetailsParams.newBuilder().setSkusList(skuList).setType(skuType).build()
        Log.d(TAG, "querySkuDetailsAsync for $skuType")
        billingClient.querySkuDetailsAsync(params) { billingResult, skuDetailsList ->
            when (billingResult.responseCode) {
                BillingClient.BillingResponseCode.OK -> {
                    if (skuDetailsList.orEmpty().isNotEmpty()) {
                        skuDetailsList?.forEach {
                            skuDetailsMap[it.sku] = it
                        }
                    }
                    _enabled.value = true
                }
                else -> {
                    Log.e(TAG, billingResult.debugMessage)
                }
            }
        }
    }

    override suspend fun purchaseItem(activity: Activity, sku: String): PurchaseImpl? {
        // 購入用のパラメータを作成
        val skuDetails = skuDetailsMap[sku]
        if( skuDetails==null ){
            Log.e(TAG, "Cannot find sku($sku) details from PlayStore. Check app items on Play console.")
            return null
        }
        val purchaseParams = BillingFlowParams.newBuilder()
            .setSkuDetails(skuDetails)
            .setObfuscatedAccountId("accountId") // FIXME 難読化が必要
            .setObfuscatedProfileId("profileId") // FIXME 難読化が必要
            .build()

        val currentPurchaseFlow = CompletableDeferred<Purchase?>()
            .also { pendingPurchaseFlows[sku] = it }

        billingClient.launchBillingFlow(activity, purchaseParams)
        return currentPurchaseFlow.await()
    }

    override suspend fun queryPurchaseData(): List<PurchaseImpl> {
        Log.d(TAG, "Querying inventory.")
        val validPurchases = mutableListOf<Purchase>()
        val result = billingClient.queryPurchases(BillingClient.SkuType.INAPP)
        // 購入済みのレシートのみを収集。
        // SDKでは定期購入や非消費型アイテムはないので、それらのコードは割愛
        result?.purchasesList?.forEach { purchase ->
            if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                if (purchase.developerPayload.isNotEmpty()) {
                    // AIDL時代のレシート発見
                    Log.d(
                        TAG,
                        "payload = ${purchase.developerPayload} "
                    )
                } else {
                    Log.d(
                        TAG,
                        "purchase attrs = ${purchase.accountIdentifiers?.obfuscatedAccountId} : " +
                                "${purchase.accountIdentifiers?.obfuscatedProfileId} "
                    )
                }
                validPurchases.add(purchase)
            } else if (purchase.purchaseState == Purchase.PurchaseState.PENDING) {
                Log.d(TAG, "Received a pending purchase of SKU: ${purchase.sku}")
                // handle pending purchases, e.g. confirm with users about the pending
                // purchases, prompt them to complete it, etc.
                validPurchases.add(purchase)
            }
        }
        return validPurchases
    }

    override fun isSupported() = enabled.value

    override suspend fun consumePurchase(purchase: PurchaseImpl): Boolean {
        Log.d(TAG, "Consume item: ${purchase.orderId}.")
        val params =
            ConsumeParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
        // コールバックが返るまで待たせている
        return suspendCoroutine { continuation ->
            billingClient.consumeAsync(params) { billingResult, purchaseToken ->
                when (billingResult.responseCode) {
                    BillingClient.BillingResponseCode.OK -> {
                        // Update the appropriate tables/databases to grant user the items
                        continuation.resume(true)
                    }
                    else -> {
                        Log.w(TAG, billingResult.debugMessage)
                        continuation.resume(false)
                    }
                }
            }
            return@suspendCoroutine
        }
    }

    override fun createPurchaseImpl(purchaseData: PurchaseData): PurchaseImpl =
        Purchase(purchaseData.jsonString, purchaseData.signature)

    /**
     * Ideally your implementation will comprise a secure server, rendering this check
     * unnecessary. @see [Security]
     */
    private fun isSignatureValid(purchase: Purchase): Boolean {
        return Security.verifyPurchase(
            Security.BASE_64_ENCODED_PUBLIC_KEY, purchase.originalJson, purchase.signature
        )
    }
}
