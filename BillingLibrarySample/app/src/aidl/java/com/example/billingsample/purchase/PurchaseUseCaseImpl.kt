package com.example.billingsample.purchase

import android.app.Activity
import android.content.Intent
import android.util.Log
import com.example.billingsample.model.PurchaseData
import com.example.billingsample.purchase.util.IabHelper
import com.example.billingsample.purchase.util.IabHelper.IabAsyncInProgressException
import com.example.billingsample.purchase.util.Purchase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine


// AIDL 使用時の課金処理クラス
class PurchaseUseCaseImpl : PurchaseUseCase {
    companion object {
        const val TAG = "PurchaseUseCaseAIDL"
        const val BILLING_REQUEST_CODE = 300
    }

    private var helper: IabHelper? = null

    override val enabled: StateFlow<Boolean>
        get() = _enabled
    private val _enabled = MutableStateFlow(false)

    override suspend fun init(activity: Activity) {
        helper = IabHelper(activity, "") // FIXME
        helper?.startSetup { result ->
            Log.d(TAG, "Setup finished.")

            if (!result.isSuccess) {
                // Oh noes, there was a problem.
                Log.e(TAG, "Problem setting up in-app billing: $result")
                _enabled.value = false
                return@startSetup
            }

            Log.d(TAG, "Setup successful. ")
            _enabled.value = true
        }
    }

    override fun handlePurchaseResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        return helper?.handleActivityResult(requestCode, resultCode, data) == true
    }

    override suspend fun purchaseItem(activity: Activity, sku: String): PurchaseImpl? {
        // コールバックが返るまで待たせている
        return suspendCoroutine { continuation ->
            helper?.launchPurchaseFlow(
                activity, sku, BILLING_REQUEST_CODE, { result, info ->
                    Log.d(TAG, "Purchase finished: $result, purchase: $info")
                    if (result.isFailure) {
                        if (result.response == IabHelper.IABHELPER_USER_CANCELLED) {
                            Log.d(TAG, "User cancelled.")
                        } else {
                            Log.e(
                                TAG,
                                "launchPurchaseFlow error response : " + result.response
                            )
                        }
                        continuation.resume(null)
                    } else {
                        Log.d(TAG, "Purchase successful.")
                        Log.d(TAG, "payload = ${info.developerPayload}")
                        continuation.resume(info)
                    }
                }, "sample_developer_payload"
            )
            return@suspendCoroutine
        }
    }

    override suspend fun queryPurchaseData(): List<PurchaseImpl> {
        Log.d(TAG, "Querying inventory.")
        return suspendCoroutine { continuation ->
            try {
                helper?.queryInventoryAsync { result, inv ->
                    Log.d(TAG, "Query inventory finished.");

                    // Is it a failure?
                    if (result.isFailure) {
                        Log.e(TAG, "Failed to query inventory: $result")
                        continuation.resume(emptyList())
                    } else {
                        Log.d(TAG, "Query inventory was successful.");

                        // 未消費レシートを保持
                        continuation.resume(inv.allPurchases)
                    }
                }
            } catch (e: IabAsyncInProgressException) {
                Log.e(TAG, "Error querying inventory. Another async operation in progress.")
                continuation.resume(emptyList())
            }
            return@suspendCoroutine
        }
    }

    override fun isSupported() = (helper != null && _enabled.value)

    // 消費処理
    override suspend fun consumePurchase(purchase: PurchaseImpl): Boolean {
        Log.d(TAG, "Consume item: ${purchase.orderId}.")
        return suspendCoroutine { continuation ->
            helper?.consumeAsync(purchase) { _, result ->
                if (result.isFailure) {
                    continuation.resume(false)
                } else {
                    continuation.resume(true)
                }
            }
            return@suspendCoroutine
        }
    }

    override fun createPurchaseImpl(purchaseData: PurchaseData): PurchaseImpl =
        Purchase(IabHelper.ITEM_TYPE_INAPP, purchaseData.jsonString, purchaseData.signature)
}
