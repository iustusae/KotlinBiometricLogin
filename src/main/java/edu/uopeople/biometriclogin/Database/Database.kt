package edu.uopeople.biometriclogin.Database

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.TypeConverters


@Dao
interface EmployeeDAO{
    @Query("SELECT * FROM employee")
    suspend fun getAll(): List<Employee>

    @Query("SELECT * FROM employee WHERE id IN (:employeeIds)")
    suspend fun loadAllByIds(employeeIds: IntArray): List<Employee>

    @Query("SELECT * FROM employee where emp_name LIKE (:name)")
    suspend fun findByName(name: String): Employee

    @Query("SELECT * FROM employee where emp_email LIKE (:email) LIMIT 1")
    suspend fun findByEmail(email: String): Employee?

    @Query("Select * FROM employee where id is (:id)")
    suspend fun findById(id: Int): Employee?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(vararg employees: Employee)

    @Insert
    suspend fun insert(employee: Employee)

    @Delete
    suspend fun delete(employee: Employee)

}


@Database(entities = [Employee::class, EmployeeBiometrics::class, AttendanceRecord::class], version = 4, exportSchema = false)
@TypeConverters(Converters::class, DateConverters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun employeeDAO(): EmployeeDAO
    abstract fun employeeBiometricsDao(): EmployeeBiometricsDao
    abstract fun attendanceRecordDao(): AttendanceRecordDao
}