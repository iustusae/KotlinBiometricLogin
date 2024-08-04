package edu.uopeople.biometriclogin.Database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class Employee(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "emp_name") val name: String,
    @ColumnInfo(name = "emp_email") val email: String,
    @ColumnInfo(name = "emp_pwrd") val password: String
)

