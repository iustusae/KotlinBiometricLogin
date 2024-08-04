package edu.uopeople.biometriclogin.Database
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.TypeConverter
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

@Entity(
    foreignKeys = [ForeignKey(
        entity = Employee::class,
        parentColumns = arrayOf("id"),
        childColumns = arrayOf("emp_id"),
        onDelete = ForeignKey.CASCADE
    )],
    tableName = "EmployeeBiometrics",
    indices = [Index(value = ["emp_id"])]
)
data class EmployeeBiometrics(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "emp_id") val employeeId: Int,
    @ColumnInfo(name = "biometric_data") val biometricData: ByteArray,
    @ColumnInfo(name = "date") val date: LocalDate,
    @ColumnInfo(name = "check_in_time") val checkInTime: LocalDateTime,
    @ColumnInfo(name = "check_out_time") val checkOutTime: LocalDateTime? = null
)


@Dao
interface EmployeeBiometricsDao {
    @Insert
    suspend fun insert(employeeBiometrics: EmployeeBiometrics)

    @Query("SELECT * FROM EmployeeBiometrics WHERE emp_id = :employeeId LIMIT 1")
    suspend fun findByEmployeeId(employeeId: Int): EmployeeBiometrics?

    @Query("SELECT * FROM EmployeeBiometrics WHERE emp_id = :employeeId AND date = :date LIMIT 1")
    suspend fun findByEmployeeIdAndDate(employeeId: Int, date: LocalDate): EmployeeBiometrics?

    @Query("SELECT * FROM EmployeeBiometrics")
    suspend fun getAll(): List<EmployeeBiometrics>

    @Query("UPDATE EmployeeBiometrics SET check_out_time = :checkOutTime WHERE id = :id")
    suspend fun updateCheckOutTime(id: Int, checkOutTime: LocalDateTime)
}

class DateConverters {
    @TypeConverter
    fun fromTimestamp(value: Long?): LocalDateTime? {
        return value?.let { LocalDateTime.ofEpochSecond(it, 0, ZoneOffset.UTC) }
    }

    @TypeConverter
    fun dateToTimestamp(date: LocalDateTime?): Long? {
        return date?.toEpochSecond(ZoneOffset.UTC)
    }

    @TypeConverter
    fun fromLocalDate(value: Long?): LocalDate? {
        return value?.let { LocalDate.ofEpochDay(it) }
    }

    @TypeConverter
    fun localDateToLong(date: LocalDate?): Long? {
        return date?.toEpochDay()
    }
}