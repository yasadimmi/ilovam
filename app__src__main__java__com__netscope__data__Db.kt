package com.netscope.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "wifi_scans", indices = [Index("bssid"), Index("ts")])
data class WifiScanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ts: Long, val ssid: String, val bssid: String, val rssi: Int,
    val freq: Int, val channel: Int, val band: String, val security: String, val vendor: String
)

@Entity(tableName = "ble_scans", indices = [Index("ts")])
data class BleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ts: Long, val name: String, val address: String, val rssi: Int, val kind: String
)

@Entity(tableName = "speed_tests", indices = [Index("ts")])
data class SpeedEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ts: Long, val downMbps: Double, val upMbps: Double,
    val pingMs: Double, val jitterMs: Double, val endpoint: String
)

@Entity(tableName = "uptime", indices = [Index("ts")])
data class UptimeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ts: Long, val up: Boolean, val latencyMs: Int, val gateway: String
)

@Entity(tableName = "heatmaps")
data class HeatMapEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String, val imagePath: String?, val bssid: String, val ssid: String, val createdAt: Long
)

@Entity(tableName = "heat_points", indices = [Index("mapId")])
data class HeatPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mapId: Long, val x: Float, val y: Float, val rssi: Int, val ts: Long
)

@Dao
interface WifiDao {
    @Insert suspend fun insertAll(items: List<WifiScanEntity>)
    @Query("SELECT * FROM wifi_scans WHERE ts >= :from ORDER BY ts DESC") fun since(from: Long): Flow<List<WifiScanEntity>>
    @Query("DELETE FROM wifi_scans") suspend fun clear()
}

@Dao
interface BleDao {
    @Insert suspend fun insertAll(items: List<BleEntity>)
    @Query("SELECT * FROM ble_scans WHERE ts >= :from ORDER BY ts DESC") fun since(from: Long): Flow<List<BleEntity>>
    @Query("DELETE FROM ble_scans") suspend fun clear()
}

@Dao
interface SpeedDao {
    @Insert suspend fun insert(item: SpeedEntity)
    @Insert suspend fun insertAll(items: List<SpeedEntity>)
    @Query("SELECT * FROM speed_tests WHERE ts >= :from ORDER BY ts DESC") fun since(from: Long): Flow<List<SpeedEntity>>
    @Query("DELETE FROM speed_tests") suspend fun clear()
}

@Dao
interface UptimeDao {
    @Insert suspend fun insert(item: UptimeEntity)
    @Query("SELECT * FROM uptime WHERE ts >= :from ORDER BY ts ASC") fun since(from: Long): Flow<List<UptimeEntity>>
    @Query("DELETE FROM uptime WHERE ts < :before") suspend fun deleteBefore(before: Long)
    @Query("DELETE FROM uptime") suspend fun clear()
}

@Dao
interface HeatDao {
    @Query("SELECT * FROM heatmaps ORDER BY createdAt DESC") fun maps(): Flow<List<HeatMapEntity>>
    @Insert suspend fun insertMap(m: HeatMapEntity): Long
    @Query("DELETE FROM heatmaps WHERE id = :id") suspend fun deleteMap(id: Long)
    @Query("SELECT * FROM heat_points WHERE mapId = :mapId ORDER BY ts ASC") fun points(mapId: Long): Flow<List<HeatPointEntity>>
    @Insert suspend fun insertPoint(p: HeatPointEntity)
    @Query("DELETE FROM heat_points WHERE id = :id") suspend fun deletePoint(id: Long)
    @Query("DELETE FROM heat_points WHERE mapId = :mapId") suspend fun deletePointsOf(mapId: Long)
}

@Database(
    entities = [WifiScanEntity::class, BleEntity::class, SpeedEntity::class, UptimeEntity::class,
        HeatMapEntity::class, HeatPointEntity::class],
    version = 1, exportSchema = false
)
abstract class AppDb : RoomDatabase() {
    abstract fun wifi(): WifiDao
    abstract fun ble(): BleDao
    abstract fun speed(): SpeedDao
    abstract fun uptime(): UptimeDao
    abstract fun heat(): HeatDao

    companion object {
        @Volatile private var inst: AppDb? = null
        fun get(ctx: Context): AppDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(ctx.applicationContext, AppDb::class.java, "netscope.db")
                .build().also { inst = it }
        }
    }
}
