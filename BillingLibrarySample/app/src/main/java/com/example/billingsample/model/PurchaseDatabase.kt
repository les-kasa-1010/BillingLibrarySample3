package com.example.billingsample.model

import android.content.Context
import androidx.room.*

/**
 * DAOクラス
 */
@Dao
interface PurchaseDao {
    @Query("SELECT * FROM log_table")
    fun getAll(): List<PurchaseData> // 戻り値をFlow等にして変更を監視できるが、今回は要件的に不要なのでやっていない

    @Insert
    fun insert(data: PurchaseData)

    @Update
    fun update(data: PurchaseData)

    @Query("UPDATE log_table SET status = :status WHERE order_id = :orderId")
    fun updateStatus(orderId: String, status: String)

    @Delete
    fun delete(data: PurchaseData)

    @Query("DELETE FROM log_table")
    fun deleteAll()

    @Query("SELECT * FROM log_table WHERE order_id = :orderId")
    fun find(orderId: String): PurchaseData?
}

/**
 * データベースクラス
 * SQLiteの公式ライブラリであるRoom用のもの
 */
@Database(entities = [PurchaseData::class], version = 1)
abstract class PurchaseDatabase : RoomDatabase() {
    abstract fun purchaseDao(): PurchaseDao

    companion object {
        @Volatile
        private var INSTANCE: PurchaseDatabase? = null

        fun getDatabase(context: Context): PurchaseDatabase {
            return INSTANCE
                ?: synchronized(this) {
                    // Create database here
                    val instance = Room.databaseBuilder(
                        context.applicationContext,
                        PurchaseDatabase::class.java,
                        "log_database"
                    ).build()
                    INSTANCE = instance
                    instance
                }
        }
    }
}