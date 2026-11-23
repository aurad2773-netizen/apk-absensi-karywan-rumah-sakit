package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.HospitalDatabase
import com.example.data.local.LeaveType
import com.example.data.local.ShiftType
import com.example.data.local.WorkMode
import com.example.data.preferences.HospitalLocationConfig
import com.example.data.preferences.SessionPreferences
import com.example.data.repository.HospitalRepository
import com.example.util.PasswordHasher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private lateinit var context: Context
    private lateinit var db: HospitalDatabase
    private lateinit var repository: HospitalRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, HospitalDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = HospitalRepository(
            employeeDao = db.employeeDao(),
            attendanceDao = db.attendanceDao(),
            shiftScheduleDao = db.shiftScheduleDao(),
            leaveRequestDao = db.leaveRequestDao(),
            sessionPreferences = SessionPreferences(context)
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `app name matches MedPresensi`() {
        val appName = context.getString(R.string.app_name)
        assertEquals("MedPresensi", appName)
    }

    @Test
    fun `password hashing verifies correctly`() {
        val salt = PasswordHasher.generateSalt()
        val hash = PasswordHasher.hashPassword("rahasia123", salt)
        assertTrue(PasswordHasher.verifyPassword("rahasia123", salt, hash))
        assertFalse(PasswordHasher.verifyPassword("salah123", salt, hash))
    }

    @Test
    fun `register login checkIn checkOut and leave flow succeeds`() = runTest {
        val regResult = repository.register(
            nip = "RS-202699",
            fullName = "dr. Andi Wijaya",
            email = "andi.wijaya@rsmedika.id",
            password = "password123",
            role = "Dokter Umum",
            department = "IGD",
            phone = "0812999888",
            employmentStatus = "Pegawai Tetap",
            defaultShift = ShiftType.PAGI
        )
        assertTrue(regResult.isSuccess)
        val employee = regResult.getOrThrow()

        val loginResult = repository.login("RS-202699", "password123")
        assertTrue(loginResult.isSuccess)
        assertEquals(employee.id, loginResult.getOrThrow().id)

        val checkInResult = repository.checkIn(
            employeeId = employee.id,
            shiftType = ShiftType.PAGI,
            workMode = WorkMode.WFO_RS,
            gpsReading = null,
            hospitalConfig = HospitalLocationConfig(),
            notes = "Mulai jaga pagi"
        )
        assertTrue(checkInResult.isSuccess)

        val checkOutResult = repository.checkOut(
            employeeId = employee.id,
            gpsReading = null,
            notes = "Selesai operan"
        )
        assertTrue(checkOutResult.isSuccess)
        assertNotNull(checkOutResult.getOrThrow().checkOutTime)

        val leaveResult = repository.submitLeaveRequest(
            employeeId = employee.id,
            leaveType = LeaveType.CUTI,
            startDateKey = "2026-12-01",
            endDateKey = "2026-12-02",
            reason = "Acara keluarga",
            attachmentUri = null
        )
        assertTrue(leaveResult.isSuccess)
        val leaves = repository.observeLeaveRequests(employee.id).first()
        assertEquals(1, leaves.size)
    }
}
