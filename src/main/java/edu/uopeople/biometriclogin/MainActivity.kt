@file:Suppress("DEPRECATION")

package edu.uopeople.biometriclogin

import edu.uopeople.biometriclogin.Database.EmployeeBiometrics
import android.content.Context
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.room.Room
import edu.uopeople.biometriclogin.Database.AppDatabase
import edu.uopeople.biometriclogin.Database.AttendanceRecord
import edu.uopeople.biometriclogin.Database.Employee
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.coroutines.CoroutineContext

enum class LoginState {
    Signup,
    Login,
    Logged,
}
class MainActivity : FragmentActivity() {
    private val db by lazy {
        Room.databaseBuilder(applicationContext, AppDatabase::class.java, "employees")
            .fallbackToDestructiveMigration()
            .build()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            var loginState by remember { mutableStateOf(LoginState.Login) }
            var loggedInEmployee by remember { mutableStateOf<Employee?>(null) }
            var showErrorDialog by remember { mutableStateOf(false) }
            var errorMessage by remember { mutableStateOf("") }
            val coroutineScope = rememberCoroutineScope()

            if (showErrorDialog) {
                AlertDialog(
                    onDismissRequest = { showErrorDialog = false },
                    title = { Text("Error") },
                    text = { Text(errorMessage) },
                    confirmButton = {
                        TextButton(onClick = { showErrorDialog = false }) {
                            Text("OK")
                        }
                    }
                )
            }

            Scaffold(
                content = { _ ->
                    when (loginState) {
                        LoginState.Signup -> SignupScreen(
                            db = db,
                            onSignUpComplete = { loginState = LoginState.Login },
                            onError = { message ->
                                errorMessage = message
                                showErrorDialog = true
                            }
                        )

                        LoginState.Login -> SignInScreen(
                            onSignIn = { email, password ->
                                coroutineScope.launch {
                                    val user = db.employeeDAO().findByEmail(email)
                                    if (user != null && user.password == password) {
                                        loggedInEmployee = user
                                        loginState = LoginState.Logged
                                    } else {
                                        errorMessage = "Invalid credentials"
                                        showErrorDialog = true
                                    }
                                }
                            },
                            onSignUp = { loginState = LoginState.Signup },
                            onError = { message ->
                                errorMessage = message
                                showErrorDialog = true
                            }
                        )

                        LoginState.Logged -> HomeScreen(
                            onLogout = {
                                loggedInEmployee = null
                                loginState = LoginState.Login
                            },
                            context = this@MainActivity,
                            employeeId = loggedInEmployee!!.id,
                            db = db
                        )
                    }
                }
            )
        }
    }
}

@Composable
fun HomeScreen(
    onLogout: () -> Unit,
    context: FragmentActivity,
    employeeId: Int,
    db: AppDatabase
) {
    val coroutineScope = rememberCoroutineScope()
    var employee by remember { mutableStateOf<Employee?>(null) }
    var attendanceRecords by remember { mutableStateOf<List<AttendanceRecord>>(emptyList()) }
    var isBiometricRegistered by remember { mutableStateOf(false) }
    var showBiometricAlert by remember { mutableStateOf(false) }
    var showAttendanceList by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(key1 = employeeId) {
        employee = withContext(Dispatchers.IO) { db.employeeDAO().findById(employeeId) }
        isBiometricRegistered = withContext(Dispatchers.IO) {
            db.employeeBiometricsDao().findByEmployeeId(employeeId) != null
        }
        attendanceRecords = withContext(Dispatchers.IO) {
            db.attendanceRecordDao().findByEmployeeId(employeeId)
        }
    }

    val handleAttendanceAction: (Boolean) -> Unit = { isCheckIn ->
        coroutineScope.launch {
            val todayRecord = attendanceRecords.find {
                it.checkInTime!! == Date()
            }

            when {
                !isBiometricRegistered -> {
                    showBiometricAlert = true
                }
                isCheckIn && todayRecord?.checkInTime != null -> {
                    errorMessage = "You've already checked in today."
                }
                !isCheckIn  -> {
                    errorMessage = "You haven't checked in today or have already checked out."
                }
                else -> {
                    // Proceed with biometric authentication
                    authenticateAndProcessAttendance(context, db, employeeId, isCheckIn) { success, message ->
                        if (success) {
                            // Refresh attendance records
                            coroutineScope.launch {
                                attendanceRecords = db.attendanceRecordDao().findByEmployeeId(employeeId)
                            }
                            errorMessage = "Successfully ${if (isCheckIn) "checked in" else "checked out"}."
                        } else {
                            errorMessage = message
                        }
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Welcome, ${employee?.name ?: "Employee"}",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        Button(
            onClick = { handleAttendanceAction(true) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        ) {
            Text("Check In")
        }

        Button(
            onClick = { handleAttendanceAction(false) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        ) {
            Text("Check Out")
        }

        Button(
            onClick = { showAttendanceList = true },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        ) {
            Text("View Attendance")
        }

        errorMessage?.let {
            Text(
                text = it,
                color = if (it.startsWith("Successfully")) Color.Green else Color.Red,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Logout")
        }
    }

    if (showBiometricAlert) {
        BiometricRegistrationAlert(
            onDismiss = { showBiometricAlert = false },
            onRegisterBiometric = {
                registerBiometric(context, db, employeeId) { success ->
                    if (success) {
                        isBiometricRegistered = true
                        errorMessage = "Biometric registered successfully."
                    } else {
                        errorMessage = "Failed to register biometric."
                    }
                    showBiometricAlert = false
                }
            }
        )
    }

    if (showAttendanceList) {
        AttendanceListDialog(
            attendanceRecords = attendanceRecords,
            onDismiss = { showAttendanceList = false }
        )
    }
}

fun authenticateAndProcessAttendance(
    context: FragmentActivity,
    db: AppDatabase,
    employeeId: Int,
    isCheckIn: Boolean,
    callback: (Boolean, String) -> Unit
) {
    val biometricPrompt = createBiometricPrompt(context) { success ->
        if (success) {
            // Biometric authentication successful, now check location
            checkLocationAndProcessAttendance(context, db, employeeId, isCheckIn, callback)
        } else {
            callback(false, "Biometric authentication failed.")
        }
    }

    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle("Biometric Authentication")
        .setSubtitle("Verify your identity to ${if (isCheckIn) "check in" else "check out"}")
        .setNegativeButtonText("Cancel")
        .build()

    biometricPrompt.authenticate(promptInfo)
}

fun checkLocationAndProcessAttendance(
    context: Context,
    db: AppDatabase,
    employeeId: Int,
    isCheckIn: Boolean,
    callback: (Boolean, String) -> Unit
) {
    // TODO: Implement actual location checking logic
    val coroutineScope = CoroutineScope(Dispatchers.IO)

    val isAtOffice = true // Placeholder, replace with actual GPS check

    if (isAtOffice) {
        if (isCheckIn) {
            coroutineScope.launch {
                db.attendanceRecordDao().insert(AttendanceRecord(employeeId = employeeId, checkInTime = Date(), checkOutTime = null))

            }
        } else {
          coroutineScope.launch {
              val todayRecord = db.attendanceRecordDao().findTodayRecordForEmployee(employeeId, Date())
              todayRecord?.let {
                  it.checkOutTime = Date()
                  db.attendanceRecordDao().update(it)
              }
          }
        }
        callback(true, "Successfully ${if (isCheckIn) "checked in" else "checked out"}.")
    } else {
        callback(false, "You are not at the office location.")
    }
}

fun registerBiometric(
    context: FragmentActivity,
    db: AppDatabase,
    employeeId: Int,
    callback: (Boolean) -> Unit
) {
    val coroutineScope = CoroutineScope(Dispatchers.IO)

    val biometricPrompt = createBiometricPrompt(context) { success ->
        if (success) {
            // In a real app, you'd save the actual biometric data securely
            // Here we're just saving a placeholder value
            coroutineScope.launch {
                db.employeeBiometricsDao().insert(EmployeeBiometrics(employeeId = employeeId, biometricData = ByteArray(16), checkInTime = LocalDateTime.now(), date = LocalDate.now()))
            }
            callback(true)
        } else {
            callback(false)
        }
    }

    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle("Register Biometric")
        .setSubtitle("Register your biometric for future authentication")
        .setNegativeButtonText("Cancel")
        .build()

    biometricPrompt.authenticate(promptInfo)
}

fun createBiometricPrompt(context: FragmentActivity, callback: (Boolean) -> Unit): BiometricPrompt {
    val executor = ContextCompat.getMainExecutor(context)

    return BiometricPrompt(context, executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                callback(true)
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                callback(false)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                callback(false)
            }
        })
}

@Composable
fun AttendanceListDialog(
    attendanceRecords: List<AttendanceRecord>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Attendance Records") },
        text = {
            LazyColumn {
                items(attendanceRecords) { record ->
                    AttendanceRecordItem(record)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun AttendanceRecordItem(record: AttendanceRecord) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text("Date: ${record.checkInTime}")
        Text("Check-in: ${record.checkInTime}")
        record.checkOutTime?.let {
            Text("Check-out: ${it}")
        }
    }
}@Composable
fun BiometricRegistrationAlert(
    onDismiss: () -> Unit,
    onRegisterBiometric: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Biometric Registration Required") },
        text = { Text("Please register your biometric to proceed with attendance.") },
        confirmButton = {
            Button(onClick = onRegisterBiometric) {
                Text("Register Biometric")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Dismiss")
            }
        }
    )
}

fun captureBiometric(
    context: FragmentActivity,
    onSuccess: (ByteArray) -> Unit,
    onFailure: (String) -> Unit
) {
    val executor = ContextCompat.getMainExecutor(context)

    val biometricPrompt = BiometricPrompt(
        context,
        executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                val biometricData = result.cryptoObject?.cipher?.doFinal() ?: ByteArray(0)
                onSuccess(biometricData)
            }

            override fun onAuthenticationFailed() {
                onFailure("Authentication failed")
            }
        })

    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle("Biometric Authentication")
        .setSubtitle("Authenticate using your biometric credential")
        .setNegativeButtonText("Cancel")
        .build()

    biometricPrompt.authenticate(promptInfo)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignInScreen(
    onSignIn: (String, String) -> Unit,
    onSignUp: () -> Unit,
    onError: (String) -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isEmailValid by remember { mutableStateOf(true) }
    var isPasswordValid by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Sign In",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        OutlinedTextField(
            value = email,
            onValueChange = {
                email = it
                isEmailValid = it.isNotBlank() && android.util.Patterns.EMAIL_ADDRESS.matcher(it).matches()
            },
            label = { Text("Email") },
            isError = !isEmailValid,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        )
        if (!isEmailValid) {
            Text(
                text = "Please enter a valid email address",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        OutlinedTextField(
            value = password,
            onValueChange = {
                password = it
                isPasswordValid = it.length >= 6
            },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            isError = !isPasswordValid,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        )
        if (!isPasswordValid) {
            Text(
                text = "Password must be at least 6 characters long",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        Button(
            onClick = {
                if (isEmailValid && isPasswordValid) {
                    onSignIn(email, password)
                } else {
                    onError("Please correct the errors before signing in")
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Text("Sign In")
        }

        TextButton(
            onClick = onSignUp,
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Text("Don't have an account? Sign Up")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignupScreen(
    db: AppDatabase,
    onSignUpComplete: () -> Unit,
    onError: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var isNameValid by remember { mutableStateOf(true) }
    var isEmailValid by remember { mutableStateOf(true) }
    var isPasswordValid by remember { mutableStateOf(true) }
    var isConfirmPasswordValid by remember { mutableStateOf(true) }
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Sign Up",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        OutlinedTextField(
            value = name,
            onValueChange = {
                name = it
                isNameValid = it.isNotBlank()
            },
            label = { Text("Name") },
            isError = !isNameValid,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        )
        if (!isNameValid) {
            Text(
                text = "Name cannot be empty",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        OutlinedTextField(
            value = email,
            onValueChange = {
                email = it
                isEmailValid = it.isNotBlank() && android.util.Patterns.EMAIL_ADDRESS.matcher(it).matches()
            },
            label = { Text("Email") },
            isError = !isEmailValid,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        )
        if (!isEmailValid) {
            Text(
                text = "Please enter a valid email address",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        OutlinedTextField(
            value = password,
            onValueChange = {
                password = it
                isPasswordValid = it.length >= 6
            },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            isError = !isPasswordValid,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        )
        if (!isPasswordValid) {
            Text(
                text = "Password must be at least 6 characters long",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        OutlinedTextField(
            value = confirmPassword,
            onValueChange = {
                confirmPassword = it
                isConfirmPasswordValid = it == password
            },
            label = { Text("Confirm Password") },
            visualTransformation = PasswordVisualTransformation(),
            isError = !isConfirmPasswordValid,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        )
        if (!isConfirmPasswordValid) {
            Text(
                text = "Passwords do not match",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        Button(
            onClick = {
                if (isNameValid && isEmailValid && isPasswordValid && isConfirmPasswordValid) {
                    coroutineScope.launch {
                        try {
                            val employee = Employee(0, name.trim(), email, password)
                            db.employeeDAO().insert(employee)
                            onSignUpComplete()
                        } catch (e: Exception) {
                            onError("Error during sign up: ${e.message}")
                        }
                    }
                } else {
                    onError("Please correct the errors before signing up")
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Text("Sign Up")
        }

        TextButton(
            onClick = onSignUpComplete,
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Text("Already have an account? Sign In")
        }
    }
}