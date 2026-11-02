package com.zwallet.app.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "wallet_cards")
data class WalletCard(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val bank: String,
    val balance: Long,
    val color: Int = 0
)

@Entity(
    tableName = "wallet_transactions",
    foreignKeys = [ForeignKey(
        entity = WalletCard::class,
        parentColumns = ["id"],
        childColumns = ["cardId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("cardId")]
)
data class WalletTransaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cardId: Long,
    // INCOME or EXPENSE
    val type: String,
    val amount: Long,
    val note: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface WalletDao {
    @Query("SELECT * FROM wallet_cards ORDER BY id DESC")
    fun observeCards(): Flow<List<WalletCard>>

    @Query("SELECT * FROM wallet_transactions ORDER BY createdAt DESC")
    fun observeTransactions(): Flow<List<WalletTransaction>>

    @Query("SELECT * FROM wallet_transactions WHERE cardId = :cardId ORDER BY createdAt DESC")
    fun observeTransactionsForCard(cardId: Long): Flow<List<WalletTransaction>>

    @Insert
    suspend fun insertCard(card: WalletCard): Long

    @Update
    suspend fun updateCard(card: WalletCard)

    @Delete
    suspend fun deleteCard(card: WalletCard)

    @Insert
    suspend fun insertTransaction(transaction: WalletTransaction)

    @Query("UPDATE wallet_cards SET balance = balance + :delta WHERE id = :cardId")
    suspend fun changeBalance(cardId: Long, delta: Long)
}

@Database(
    entities = [WalletCard::class, WalletTransaction::class],
    version = 1,
    exportSchema = false
)
abstract class WalletDatabase : RoomDatabase() {
    abstract fun walletDao(): WalletDao

    companion object {
        @Volatile private var INSTANCE: WalletDatabase? = null

        fun get(context: Context): WalletDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    WalletDatabase::class.java,
                    "zwallet.db"
                ).build().also { INSTANCE = it }
            }
    }
}
