package com.surudefteri.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Entity(tableName = "sheep", indices = [Index(value = ["tag"], unique = true)])
data class Sheep(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Küpe numarası (sadece rakam ve harf, boşluksuz). */
    val tag: String,
    val name: String = "",
    /** "disi", "erkek" */
    val sex: String = SEX_FEMALE,
    /** Doğum yılı (yaklaşık da olabilir). */
    val birthYear: Int? = null,
    val motherTag: String = "",
    val notes: String = "",
    val photoPath: String? = null,
    /** "aktif", "satildi", "oldu" */
    val status: String = STATUS_ACTIVE,
    val createdAt: Long = System.currentTimeMillis(),
) {
    companion object {
        const val SEX_FEMALE = "disi"
        const val SEX_MALE = "erkek"
        const val STATUS_ACTIVE = "aktif"
        const val STATUS_SOLD = "satildi"
        const val STATUS_DEAD = "oldu"
    }
}

@Entity(tableName = "counts")
data class CountRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val time: Long = System.currentTimeMillis(),
    val counted: Int,
    val expected: Int,
    /** "canli", "video" */
    val method: String,
    val note: String = "",
)

@Dao
interface SuruDao {
    @Query("SELECT * FROM sheep ORDER BY CASE status WHEN 'aktif' THEN 0 ELSE 1 END, tag")
    fun allSheep(): Flow<List<Sheep>>

    @Query("SELECT COUNT(*) FROM sheep WHERE status = 'aktif'")
    fun activeCount(): Flow<Int>

    @Query("SELECT * FROM sheep WHERE id = :id")
    suspend fun sheep(id: Long): Sheep?

    @Query("SELECT * FROM sheep WHERE tag = :tag")
    suspend fun sheepByTag(tag: String): Sheep?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(sheep: Sheep): Long

    @Update
    suspend fun update(sheep: Sheep)

    @Delete
    suspend fun delete(sheep: Sheep)

    @Insert
    suspend fun insertCount(record: CountRecord): Long

    @Query("SELECT * FROM counts ORDER BY time DESC")
    fun counts(): Flow<List<CountRecord>>

    @Query("DELETE FROM counts WHERE id = :id")
    suspend fun deleteCount(id: Long)
}

@Database(entities = [Sheep::class, CountRecord::class], version = 1, exportSchema = false)
abstract class SuruDb : RoomDatabase() {
    abstract fun dao(): SuruDao

    companion object {
        fun build(context: Context): SuruDb =
            Room.databaseBuilder(context, SuruDb::class.java, "suru.db").build()
    }
}

/** Sayım yönü: koyunlar kameranın görüntüsünde hangi yöne yürüyor. */
enum class Direction(val label: String, val horizontal: Boolean, val forward: Int) {
    LEFT_TO_RIGHT("Soldan sağa →", true, +1),
    RIGHT_TO_LEFT("Sağdan sola ←", true, -1),
    TOP_TO_BOTTOM("Yukarıdan aşağı ↓", false, +1),
    BOTTOM_TO_TOP("Aşağıdan yukarı ↑", false, -1),
}

class Prefs(context: Context) {
    private val prefs = context.getSharedPreferences("suru", Context.MODE_PRIVATE)

    private val _expected = MutableStateFlow(prefs.getInt("expected", 0))
    /** Kartlarda koyun yoksa beklenen sürü sayısı (ör. 132). 0 = kart sayısını kullan. */
    val expected: StateFlow<Int> = _expected.asStateFlow()

    private val _direction = MutableStateFlow(
        runCatching { Direction.valueOf(prefs.getString("direction", null) ?: "") }.getOrDefault(Direction.LEFT_TO_RIGHT)
    )
    val direction: StateFlow<Direction> = _direction.asStateFlow()

    private val _includeSimilar = MutableStateFlow(prefs.getBoolean("similar", true))
    /** Kuzuları/koyunları model bazen "inek" sanabiliyor; onları da say. */
    val includeSimilar: StateFlow<Boolean> = _includeSimilar.asStateFlow()

    fun setExpected(value: Int) {
        prefs.edit().putInt("expected", value).apply()
        _expected.value = value
    }

    fun setDirection(value: Direction) {
        prefs.edit().putString("direction", value.name).apply()
        _direction.value = value
    }

    fun setIncludeSimilar(value: Boolean) {
        prefs.edit().putBoolean("similar", value).apply()
        _includeSimilar.value = value
    }
}
