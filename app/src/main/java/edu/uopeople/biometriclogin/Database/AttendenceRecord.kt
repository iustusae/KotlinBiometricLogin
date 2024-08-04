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
import androidx.room.Update
import java.util.Date

@Entity(
    foreignKeys = [ForeignKey(
        entity = Employee::class,
        parentColumns = ["id"],
        childColumns = ["emp_id"],
        onDelete = ForeignKey.CASCADE
    )],
    tableName = "AttendanceRecord",
    indices = [Index(value = ["emp_id"])]
)

data class AttendanceRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "emp_id") val employeeId: Int,
    @ColumnInfo(name = "check_in_time") val checkInTime: Date?,
    @ColumnInfo(name = "check_out_time") var checkOutTime: Date?
)


@Dao
interface AttendanceRecordDao {
    @Insert
    suspend fun insert(attendanceRecord: AttendanceRecord)

    @Update
    suspend fun update(attendanceRecord: AttendanceRecord)

    @Query("SELECT * FROM AttendanceRecord WHERE emp_id = :employeeId AND date(check_in_time) = date('now')")
    suspend fun findTodayRecord(employeeId: Int): AttendanceRecord?

    @Query("SELECT * FROM AttendanceRecord WHERE emp_id = :employeeId ORDER BY check_in_time ASC")
    suspend fun findByEmployeeId(employeeId: Int): List<AttendanceRecord>

    @Query("SELECT * FROM AttendanceRecord WHERE emp_id = :employeeId AND date(check_in_time) = date(:date)")
    suspend fun findByEmployeeIdAndDate(employeeId: Int, date: Date): AttendanceRecord?

    @Query("SELECT * FROM AttendanceRecord WHERE emp_id = :employeeId AND DATE(check_in_time) = DATE(:currentTime)")
    suspend fun findTodayRecordForEmployee(employeeId: Int, currentTime: Date): AttendanceRecord?


}

class Converters {
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }
}