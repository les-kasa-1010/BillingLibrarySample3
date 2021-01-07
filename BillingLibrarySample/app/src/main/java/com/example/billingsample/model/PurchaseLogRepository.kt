package com.example.billingsample.model

import com.example.billingsample.model.PurchaseData.Companion.COMMITTING
import com.example.billingsample.model.PurchaseData.Companion.CONSUMING
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 購入ログ保存を管理するリポジトリクラス
 * DB (Room) のデータのみを管理。
 */
class PurchaseLogRepository(private val dao: PurchaseDao) {

    // 新規挿入
    // 事情により上書き更新は許可していない
    suspend fun insert(data: PurchaseData) = withContext(Dispatchers.IO) {
        dao.insert(data)
    }

    // ステータス文字列のみ更新
    suspend fun updateStatus(orderId: String, stateString: String) = withContext(Dispatchers.IO) {
        dao.updateStatus(orderId, stateString)
    }

    // ローカル購入リストを取得(SQLiteに保存しておくやつ)
    suspend fun getLocalPurchaseList() = withContext(Dispatchers.IO) {
        dao.getAll()
    }

    // 全削除
    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        dao.deleteAll()
    }

    // ローカルのみにある再送信レシートを取得
    // 数が多ければDBから取る方が早いが、Kotlinのフィルタリングで代用
    // 対象としてピックアップするようにしている
    // ストアの遅延レシートとローカルの遅延レシート、両方に挙がっていることになるが、表示上の重複削除は面倒なのでやっていない
    suspend fun getResendLocalReceipts(): List<PurchaseData> = withContext(Dispatchers.IO) {
        return@withContext getLocalPurchaseList().filter { it.status == COMMITTING || it.status == CONSUMING }
    }

    // 1件検索
    fun find(orderId: String) = dao.find(orderId)
}
