package com.example.billingsample.viewmodel

import android.app.Activity
import android.content.Intent
import android.util.Log
import androidx.lifecycle.*
import com.example.billingsample.model.PurchaseData
import com.example.billingsample.model.PurchaseData.Companion.COMMITTING
import com.example.billingsample.model.PurchaseData.Companion.COMPLETE
import com.example.billingsample.model.PurchaseData.Companion.CONSUMING
import com.example.billingsample.model.PurchaseData.Companion.PENDING
import com.example.billingsample.model.PurchaseData.Companion.STORED
import com.example.billingsample.model.PurchaseLogRepository
import com.example.billingsample.purchase.PurchaseImpl
import com.example.billingsample.purchase.PurchaseUseCaseImpl
import com.example.billingsample.purchase.isPending
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 引数付きViewModelを作成するためには専用のFactoryの用意が必要になる
 * DIライブラリを使えばもう少し楽になるが、可読性のためDIライブラリは未使用
 */
class MainViewModelFactory(private val repository: PurchaseLogRepository) :
    ViewModelProvider.Factory {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        return MainViewModel(repository) as T
    }
}

/**
 * MainActivity向けViewModel
 *
 * ViewとModel間の橋渡し的な制御をおこなうもの
 */
class MainViewModel(private val repository: PurchaseLogRepository) : ViewModel() {

    // エラー発生タイプ
    enum class ErrorType {
        SUCCESS,
        NOT_CONSUMED,
        NOT_COMMITTED,
    }

    // いわゆるstatic変数
    companion object {
        const val TAG = "MainViewModel"
    }

    // UI binding -----------------------------------------------------------
    // ラジオボタン制御
    val errorType = MutableLiveData(ErrorType.SUCCESS)

    // テキスト表示内容
    val resultString: LiveData<String>  // 公開するデータはImmutableにするのがお作法
        get() {
            return _resultString
        }

    // 自分だけが書き換え可能にするため、Mutableな変数はprivateにする
    private val _resultString = MutableLiveData("")

    // Database -----------------------------------------------------------

    // レシートをDBに初回登録
    private fun storePurchaseData(
        orderId: String, jsonString: String, signature: String, status: String) {
        viewModelScope.launch {
            repository.insert(PurchaseData(0, orderId, jsonString, signature, status))
        }
    }

    // 購入情報を更新
    private fun updatePurchaseStatus(orderId: String, status: String) {
        viewModelScope.launch {
            repository.updateStatus(orderId, status)
        }
    }

    // 全履歴データ
    private suspend fun getAllReceipts() = repository.getLocalPurchaseList()

    // ローカルのみにある再送信レシートを取得
    private suspend fun getResendLocalReceipts() = repository.getResendLocalReceipts()

    // 表示する履歴データの取得
    // Listで表示も有りだがUI作るのが大変なので文字列をTextViewに貼るだけにしている
    // この関数では、非同期で処理をして処理結果をLiveDataに反映している
    fun getAllHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            val sb = StringBuilder()
            getAllReceipts().forEach {
                sb.append(it.orderId)
                    .append(" : ")
                    .append(it.status)
                    .append("\n")
            }
            // 非UIスレッドからは、LiveDataの更新にはpostValueを使う
            _resultString.postValue(sb.toString())
        }
    }

    // 全データ削除(DB保存されたもの)
    fun deleteAllData() {
        viewModelScope.launch {
            repository.deleteAll()
        }
    }

    // Playストアサービス関連処理 -----------------------------------------------------------

    private val purchaseUseCase = PurchaseUseCaseImpl()

    val isSetupDone = purchaseUseCase.enabled.asLiveData()

    // Playストアサービス初期化
    fun initBillingService(activity: Activity) {
        viewModelScope.launch(Dispatchers.Main) {
            purchaseUseCase.init(activity)
        }
    }

    fun handlePurchaseResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        return purchaseUseCase.handlePurchaseResult(requestCode, resultCode, data)
    }

    // 購入する
    fun purchase(activity: Activity, productId: String) {
        _resultString.value = ""
        if (!purchaseUseCase.isSupported()) {
            _resultString.value = "Not Supported!!\n"
            return
        }
        _resultString.value = "Buying item $productId\n"

        // 非同期で起動
        viewModelScope.launch(Dispatchers.Main) {
            val purchased = purchaseUseCase.purchaseItem(activity, productId)
            when {
                purchased == null -> {
                    appendPurchaseStatus("Cancel or error.")
                }
                else -> {
                    val status =
                        if (purchased.isPending()) {
                            appendPurchaseStatus("PENDING TRANSACTION !!")
                            PENDING
                        } else {
                            STORED
                        }
                    // 結果を保存
                    storePurchaseData(
                        purchased.orderId,
                        purchased.originalJson,
                        purchased.signature,
                        status
                    )
                    appendPurchaseStatus(status)

                    // コミットAPI呼び出し
                    if (!purchased.isPending()) {
                        commitPurchase(purchased)
                    }
                }
            }
        }
    }

    // 未消費レシート情報を取得する
    fun queryPurchaseData() {
        val sb = StringBuilder()
        sb.append("======= Play Store =======\n")

        // queryPurchaseが非同期なため、非同期で起動している
        viewModelScope.launch {
            // scopeの中は直列的に処理を書ける

            // まずストアにレシート情報をqueryする
            val list = purchaseUseCase.queryPurchaseData()

            Log.d(TAG, "query inventory done.")
            if (list.isEmpty()) {
                sb.append("No store receipts.\n")
            } else {
                list.forEach {
                    val item = repository.find(it.orderId)
                    val stateString =
                        if (item == null) {
                            // store 全履歴に出てこなくなるため敢えて保存
                            val status = if (it.isPending()) {
                                PENDING
                            } else {
                                STORED
                            }
                            storePurchaseData(it.orderId, it.originalJson, it.signature, status)
                            status
                        } else if (!it.isPending() && item.status == PENDING) {
                            // Pendingから変わったのでステータス変更
                            updatePurchaseStatus(it.orderId, STORED)
                            STORED
                        } else {
                            item.status
                        }
                    sb.append("${it.orderId} : $stateString")

                    if (it.developerPayload.isNotEmpty()) {
                        // AIDL版のレシートを発見
                        sb.append(" ** FOUND payload **")
                    }
                    sb.append("\n")
                }
            }

            // ローカル保存データを取得
            sb.append("======= LOCAL =======\n")

            // ローカルのみにある再送信レシートを取得
            val local = getResendLocalReceipts()
            if (local.isEmpty()) {
                sb.append("No local resend data.\n")
            } else {
                local.forEach {
                    sb.append("${it.orderId} : ${it.status}\n")
                }
            }

            _resultString.postValue(sb.toString())
        }
    }

    // レシートをコミット&消費する
    private fun commitPurchase(purchase: PurchaseImpl) {
        // 本来ここではAPI通信だが、このサンプルではエラー指定タイプによってステータス変更をし、
        // 必要なタイプのみ消費を行う
        val status =
            when (errorType.value) {  // whenやifは値を返せる
                ErrorType.SUCCESS -> COMPLETE
                ErrorType.NOT_COMMITTED -> COMMITTING
                ErrorType.NOT_CONSUMED -> CONSUMING
                else -> "NOT IMPLEMENTED"
            }

        // 未消費エラー以外は消費する
        if (errorType.value != ErrorType.NOT_CONSUMED) {
            viewModelScope.launch {
                val result = purchaseUseCase.consumePurchase(purchase)
                appendPurchaseStatus("Consume result: $result.")
                updatePurchaseStatus(purchase.orderId, status)
                appendPurchaseStatus(status)
            }
        } else {
            updatePurchaseStatus(purchase.orderId, status)
            appendPurchaseStatus(status)
        }
    }

    // テキストエリアにログ的に表示する文字列を追加している
    // MainActivityでLiveDataの変更を監視しているので、値が書き換わる度にobserve関数が呼ばれて表示が変わる仕組み
    private fun appendPurchaseStatus(str: String) {
        val sb = StringBuilder()
        sb.append(_resultString.value!!).append(str).append("\n")
        _resultString.value = sb.toString()
    }

    // 全ての未消費レシートを再送信する
    // Playストアのレシートは消費され、未送信ステータスのローカルデータはステータスのみが更新されます
    fun resendAll() {
        errorType.value = ErrorType.SUCCESS // 必ず成功させる
        val sb = StringBuilder()
        sb.append("======= Play Store =======\n")
        viewModelScope.launch {
            // まずストアにレシート情報をqueryする
            val list = purchaseUseCase.queryPurchaseData()

            Log.d(TAG, "query inventory done.")
            if (list.isEmpty()) {
                sb.append("No store receipts.\n")
            } else {
                list.forEach {
                    // 再送信フロー
                    commitPurchase(it)
                    // 表示用の文字列を作成する
                    sb.append("consumed and committed: ${it.orderId}\n")
                }
            }

            // ローカル保存データを取得
            sb.append("======= LOCAL =======\n")

            // ローカルのみにある再送信レシートを取得
            val local = getResendLocalReceipts()
                // 更に、Purchaseクラスに変換(こういった処理はリポジトリクラスに担当させるべきと言う意見も
                // あるが、データの加工はViewModelでという意見もあるので　こちらにしてある)
                .map {
                    purchaseUseCase.createPurchaseImpl(it)
                }
            if (local.isEmpty()) {
                sb.append("No local resend data.\n")
            } else {
                local.forEach {
                    // 再送信フロー
                    commitPurchase(it)
                    // 表示用の文字列を作成する
                    sb.append("committed: ${it.orderId}\n")
                }
            }

            _resultString.postValue(sb.toString())
        }
    }
}
