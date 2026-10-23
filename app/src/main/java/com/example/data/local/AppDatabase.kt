package com.example.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "watchlist_items")
data class WatchlistEntity(
    @PrimaryKey val instId: String,
    val addedAtMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "saved_ai_signals")
data class SavedAiSignalEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val instId: String,
    val signalDirection: String,
    val signalVerdictKu: String,
    val confidencePercent: Int,
    val spotPriceAtSignal: Double,
    val entryZoneMin: Double,
    val entryZoneMax: Double,
    val takeProfit1: Double,
    val takeProfit2: Double,
    val takeProfit3: Double,
    val stopLoss: Double,
    val riskRewardRatio: Double,
    val forecast24hPrice: Double,
    val recommendedLeverage: String,
    val summaryKu: String,
    val createdAtMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "price_alerts")
data class PriceAlertEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val instId: String,
    val targetPrice: Double,
    val isAboveTarget: Boolean, // true = alert when price >= targetPrice
    val noteKu: String,
    val isTriggered: Boolean = false,
    val createdAtMs: Long = System.currentTimeMillis()
)

@Dao
interface OkxTradingDao {
    @Query("SELECT * FROM watchlist_items ORDER BY addedAtMs DESC")
    fun observeWatchlist(): Flow<List<WatchlistEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWatchlist(item: WatchlistEntity)

    @Query("DELETE FROM watchlist_items WHERE instId = :instId")
    suspend fun removeWatchlist(instId: String)

    @Query("SELECT * FROM saved_ai_signals ORDER BY createdAtMs DESC")
    fun observeSavedSignals(): Flow<List<SavedAiSignalEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSavedSignal(signal: SavedAiSignalEntity)

    @Query("DELETE FROM saved_ai_signals WHERE id = :id")
    suspend fun deleteSavedSignal(id: Int)

    @Query("SELECT * FROM price_alerts ORDER BY createdAtMs DESC")
    fun observePriceAlerts(): Flow<List<PriceAlertEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPriceAlert(alert: PriceAlertEntity)

    @Query("UPDATE price_alerts SET isTriggered = 1 WHERE id = :id")
    suspend fun markAlertTriggered(id: Int)

    @Query("DELETE FROM price_alerts WHERE id = :id")
    suspend fun deletePriceAlert(id: Int)
}

@Database(
    entities = [
        WatchlistEntity::class,
        SavedAiSignalEntity::class,
        PriceAlertEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun okxTradingDao(): OkxTradingDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "okx_kurd_ai_pro.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
