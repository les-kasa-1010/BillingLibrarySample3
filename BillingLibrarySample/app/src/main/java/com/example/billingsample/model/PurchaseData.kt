package com.example.billingsample.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 購入情報データクラス
 * SQLiteの公式ライブラリであるRoomを使っている
 */
@Entity(tableName = "log_table") // Room向けのTable名指定
data class PurchaseData(

    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id :Int,
    @ColumnInfo(name = "order_id") val orderId: String,
    @ColumnInfo(name = "json") val jsonString: String,
    @ColumnInfo(name = "signature") val signature: String,
    @ColumnInfo(name = "status") val status: String
) {
    // いわゆるstatic変数たち
    companion object {
        const val STORED = "購入情報送信中" // 課金完了直後
        const val CONSUMING = "購入完了（同期中）" // コミットAPI終了、消費前
        const val COMMITTING = "照合待ち" // コミットAPIエラー終了、消費済み
        const val COMPLETE = "購入完了" // 消費、コミット正常完了
    }
}

// アプリで使う課金アイテムID
// 任意に書き変えて下さい。
const val SKU_ITEM_100 = "com.example.item.100"
const val SKU_ITEM_10000 = "com.example.item.10000"
const val SKU_STATIC_TEST = "android.test.purchased"
