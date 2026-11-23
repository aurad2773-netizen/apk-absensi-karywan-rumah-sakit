package com.example.data.repository

import com.example.data.local.AttendanceDao
import com.example.data.local.AttendanceEntity
import com.example.data.local.AttendanceStatus
import com.example.data.local.EmployeeDao
import com.example.data.local.EmployeeEntity
import com.example.data.local.LeaveRequestDao
import com.example.data.local.LeaveRequestEntity
import com.example.data.local.LeaveStatus
import com.example.data.local.LeaveType
import com.example.data.local.ShiftScheduleDao
import com.example.data.local.ShiftScheduleEntity
import com.example.data.local.ShiftType
import com.example.data.local.WorkMode
import com.example.data.preferences.HospitalLocationConfig
import com.example.data.preferences.SessionPreferences
import com.example.util.DateTimeUtils
import com.example.util.GpsReading
import com.example.util.PasswordHasher
import kotlinx.coroutines.flow.Flow
import java.util.Calendar

class HospitalRepository(
    private val employeeDao: EmployeeDao,
    private val attendanceDao: AttendanceDao,
    private val shiftScheduleDao: ShiftScheduleDao,
    private val leaveRequestDao: LeaveRequestDao,
    private val sessionPreferences: SessionPreferences
) {
    val loggedInEmployeeId: Flow<Long?> = sessionPreferences.loggedInEmployeeId
    val hospitalConfig: Flow<HospitalLocationConfig> = sessionPreferences.hospitalConfig

    fun observeEmployee(employeeId: Long): Flow<EmployeeEntity?> =
        employeeDao.observeEmployeeById(employeeId)

    fun observeAttendanceHistory(employeeId: Long): Flow<List<AttendanceEntity>> =
        attendanceDao.observeAttendanceByEmployee(employeeId)

    fun observeTodayAttendance(employeeId: Long): Flow<AttendanceEntity?> =
        attendanceDao.observeTodayAttendance(employeeId, DateTimeUtils.todayDateKey())

    fun observeShiftSchedules(employeeId: Long): Flow<List<ShiftScheduleEntity>> =
        shiftScheduleDao.observeSchedulesByEmployee(employeeId)

    fun observeLeaveRequests(employeeId: Long): Flow<List<LeaveRequestEntity>> =
        leaveRequestDao.observeLeaveRequestsByEmployee(employeeId)

    suspend fun ensureDemoSeeded() {
        if (employeeDao.countEmployees() > 0) return

        val demoSalt = PasswordHasher.generateSalt()
        val demoHash = PasswordHasher.hashPassword("demo123", demoSalt)
        val nurseId = employeeDao.insertEmployee(
            EmployeeEntity(
                nip = "RS-202401",
                fullName = "Ns. Ratna Sari, S.Kep",
                email = "ratna.sari@rsmedika.id",
                passwordHash = demoHash,
                salt = demoSalt,
                role = "Perawat Pelaksana IGD",
                department = "Instalasi Gawat Darurat (IGD)",
                phone = "081234567890",
                employmentStatus = "Pegawai Tetap",
                defaultShiftType = ShiftType.PAGI.code
            )
        )

        // Seed 14-day schedule for demo nurse
        val demoShifts = listOf(
            ShiftType.PAGI, ShiftType.PAGI, ShiftType.SIANG, ShiftType.SIANG,
            ShiftType.MALAM, ShiftType.LIBUR, ShiftType.PAGI, ShiftType.PAGI,
            ShiftType.SIANG, ShiftType.MALAM, ShiftType.LIBUR, ShiftType.PAGI,
            ShiftType.PAGI, ShiftType.SIANG
        )
        val scheduleEntities = demoShifts.mapIndexed { index, shift ->
            val offset = index - 3 // starts 3 days ago
            ShiftScheduleEntity(
                employeeId = nurseId,
                dateKey = DateTimeUtils.dateKeyForOffset(offset),
                shiftType = shift.code,
                unitName = "Instalasi Gawat Darurat (IGD)",
                startHour = shift.startHour,
                startMinute = shift.startMinute,
                endHour = shift.endHour,
                endMinute = shift.endMinute,
                notes = if (shift == ShiftType.LIBUR) "Hari istirahat mingguan" else "Jaga unit IGD"
            )
        }
        shiftScheduleDao.insertAllIgnore(scheduleEntities)

        // Seed 3 historical attendance records (days -3, -2, -1)
        val pastRecords = listOf(
            Triple(-3, ShiftType.PAGI, AttendanceStatus.TEPAT_WAKTU),
            Triple(-2, ShiftType.PAGI, AttendanceStatus.TERLAMBAT),
            Triple(-1, ShiftType.SIANG, AttendanceStatus.SELESAI)
        )
        pastRecords.forEach { (offset, shift, status) ->
            val checkInCal = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, offset)
                set(Calendar.HOUR_OF_DAY, shift.startHour)
                set(Calendar.MINUTE, if (status == AttendanceStatus.TERLAMBAT) 22 else 0)
                set(Calendar.SECOND, 0)
            }
            val checkOutCal = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, offset)
                set(Calendar.HOUR_OF_DAY, shift.endHour)
                set(Calendar.MINUTE, 5)
                set(Calendar.SECOND, 0)
            }
            attendanceDao.insertAttendance(
                AttendanceEntity(
                    employeeId = nurseId,
                    dateKey = DateTimeUtils.dateKeyForOffset(offset),
                    shiftType = shift.code,
                    workMode = WorkMode.WFO_RS.code,
                    checkInTime = checkInCal.timeInMillis,
                    checkOutTime = checkOutCal.timeInMillis,
                    status = status.code,
                    checkInLat = -6.1944,
                    checkInLng = 106.8229,
                    checkOutLat = -6.1944,
                    checkOutLng = 106.8229,
                    distanceMeters = 35,
                    locationLabel = "RSUD Medika Nusantara (35 m)",
                    notes = if (status == AttendanceStatus.TERLAMBAT) "Serah terima pasien antar unit" else "Tugas jaga lancar"
                )
            )
        }

        // Seed 1 sample leave request
        leaveRequestDao.insertLeaveRequest(
            LeaveRequestEntity(
                employeeId = nurseId,
                leaveType = LeaveType.CUTI.code,
                startDateKey = DateTimeUtils.dateKeyForOffset(7),
                endDateKey = DateTimeUtils.dateKeyForOffset(8),
                reason = "Keperluan keluarga di luar kota",
                status = LeaveStatus.DISETUJUI.code
            )
        )
    }

    suspend fun login(identifier: String, password: String): Result<EmployeeEntity> {
        val cleanId = identifier.trim()
        if (cleanId.isEmpty() || password.isEmpty()) {
            return Result.failure(IllegalArgumentException("NIP/Email dan kata sandi wajib diisi."))
        }
        val employee = employeeDao.findByNipOrEmail(cleanId)
            ?: return Result.failure(IllegalArgumentException("Akun karyawan tidak ditemukan."))

        if (!PasswordHasher.verifyPassword(password, employee.salt, employee.passwordHash)) {
            return Result.failure(IllegalArgumentException("Kata sandi tidak sesuai."))
        }

        sessionPreferences.setLoggedInEmployeeId(employee.id)
        return Result.success(employee)
    }

    suspend fun register(
        nip: String,
        fullName: String,
        email: String,
        password: String,
        role: String,
        department: String,
        phone: String,
        employmentStatus: String,
        defaultShift: ShiftType
    ): Result<EmployeeEntity> {
        val cleanNip = nip.trim().uppercase()
        val cleanName = fullName.trim()
        val cleanEmail = email.trim().lowercase()

        if (cleanNip.length < 4) {
            return Result.failure(IllegalArgumentException("NIP minimal 4 karakter."))
        }
        if (cleanName.length < 3) {
            return Result.failure(IllegalArgumentException("Nama lengkap minimal 3 karakter."))
        }
        if (!cleanEmail.contains("@") || !cleanEmail.contains(".")) {
            return Result.failure(IllegalArgumentException("Format email tidak valid."))
        }
        if (password.length < 6) {
            return Result.failure(IllegalArgumentException("Kata sandi minimal 6 karakter."))
        }
        if (employeeDao.findByNipOrEmail(cleanNip) != null) {
            return Result.failure(IllegalArgumentException("NIP $cleanNip sudah terdaftar."))
        }
        if (employeeDao.findByNipOrEmail(cleanEmail) != null) {
            return Result.failure(IllegalArgumentException("Email $cleanEmail sudah terdaftar."))
        }

        val salt = PasswordHasher.generateSalt()
        val hash = PasswordHasher.hashPassword(password, salt)
        val newEmployee = EmployeeEntity(
            nip = cleanNip,
            fullName = cleanName,
            email = cleanEmail,
            passwordHash = hash,
            salt = salt,
            role = role.trim().ifEmpty { "Staf Medis" },
            department = department.trim().ifEmpty { "Instalasi Rawat Inap" },
            phone = phone.trim(),
            employmentStatus = employmentStatus.trim().ifEmpty { "Pegawai Tetap" },
            defaultShiftType = defaultShift.code
        )
        val id = employeeDao.insertEmployee(newEmployee)
        val saved = newEmployee.copy(id = id)

        // Generate 7-day shift roster for newly registered employee
        val defaultRoster = (0..6).map { offset ->
            val shift = if (offset == 5) ShiftType.LIBUR else defaultShift
            ShiftScheduleEntity(
                employeeId = id,
                dateKey = DateTimeUtils.dateKeyForOffset(offset),
                shiftType = shift.code,
                unitName = saved.department,
                startHour = shift.startHour,
                startMinute = shift.startMinute,
                endHour = shift.endHour,
                endMinute = shift.endMinute,
                notes = if (shift == ShiftType.LIBUR) "Jadwal libur mingguan" else "Jadwal aktif"
            )
        }
        shiftScheduleDao.insertAllIgnore(defaultRoster)

        sessionPreferences.setLoggedInEmployeeId(id)
        return Result.success(saved)
    }

    suspend fun logout() {
        sessionPreferences.setLoggedInEmployeeId(null)
    }

    suspend fun checkIn(
        employeeId: Long,
        shiftType: ShiftType,
        workMode: WorkMode,
        gpsReading: GpsReading?,
        hospitalConfig: HospitalLocationConfig,
        notes: String
    ): Result<AttendanceEntity> {
        val todayKey = DateTimeUtils.todayDateKey()
        val existing = attendanceDao.getTodayAttendance(employeeId, todayKey)
        if (existing != null) {
            return Result.failure(IllegalStateException("Anda sudah melakukan absen masuk hari ini."))
        }

        if (workMode == WorkMode.WFO_RS && gpsReading != null &&
            gpsReading.distanceFromHospitalMeters > hospitalConfig.radiusMeters.toInt()
        ) {
            return Result.failure(
                IllegalStateException(
                    "Lokasi di luar radius ${hospitalConfig.hospitalName} (${gpsReading.distanceFromHospitalMeters} m > batas ${hospitalConfig.radiusMeters.toInt()} m). Gunakan mode Dinas Luar atau kalibrasi titik RS di Profil."
                )
            )
        }

        val now = System.currentTimeMillis()
        val status = evaluateCheckInStatus(now, shiftType)
        val locationLabel = when {
            workMode == WorkMode.DINAS_LUAR && gpsReading != null ->
                "Dinas Luar (${gpsReading.distanceFromHospitalMeters} m dari RS)"
            workMode == WorkMode.DINAS_LUAR ->
                "Dinas Luar / Penugasan Lapangan"
            gpsReading != null ->
                "${hospitalConfig.hospitalName} (${gpsReading.distanceFromHospitalMeters} m)"
            else ->
                "${hospitalConfig.hospitalName} (Verifikasi Manual)"
        }

        val record = AttendanceEntity(
            employeeId = employeeId,
            dateKey = todayKey,
            shiftType = shiftType.code,
            workMode = workMode.code,
            checkInTime = now,
            status = status.code,
            checkInLat = gpsReading?.latitude,
            checkInLng = gpsReading?.longitude,
            distanceMeters = gpsReading?.distanceFromHospitalMeters,
            locationLabel = locationLabel,
            notes = notes.trim()
        )
        val id = attendanceDao.insertAttendance(record)
        return Result.success(record.copy(id = id))
    }

    suspend fun checkOut(
        employeeId: Long,
        gpsReading: GpsReading?,
        notes: String
    ): Result<AttendanceEntity> {
        val todayKey = DateTimeUtils.todayDateKey()
        val existing = attendanceDao.getTodayAttendance(employeeId, todayKey)
            ?: return Result.failure(IllegalStateException("Belum ada data absen masuk hari ini."))
        if (existing.checkOutTime != null) {
            return Result.failure(IllegalStateException("Anda sudah melakukan absen pulang hari ini."))
        }

        val now = System.currentTimeMillis()
        val shift = ShiftType.fromCode(existing.shiftType)
        val finalStatus = evaluateCheckOutStatus(now, shift, existing.status)
        val mergedNotes = listOf(existing.notes, notes.trim())
            .filter { it.isNotBlank() }
            .joinToString(" • ")

        val updated = existing.copy(
            checkOutTime = now,
            checkOutLat = gpsReading?.latitude,
            checkOutLng = gpsReading?.longitude,
            status = finalStatus.code,
            notes = mergedNotes
        )
        attendanceDao.updateAttendance(updated)
        return Result.success(updated)
    }

    suspend fun upsertShiftSchedule(
        employeeId: Long,
        dateKey: String,
        shiftType: ShiftType,
        unitName: String,
        notes: String
    ) {
        val existing = shiftScheduleDao.getScheduleForDate(employeeId, dateKey)
        val entity = ShiftScheduleEntity(
            id = existing?.id ?: 0L,
            employeeId = employeeId,
            dateKey = dateKey,
            shiftType = shiftType.code,
            unitName = unitName.trim().ifEmpty { "Unit Pelayanan" },
            startHour = shiftType.startHour,
            startMinute = shiftType.startMinute,
            endHour = shiftType.endHour,
            endMinute = shiftType.endMinute,
            notes = notes.trim()
        )
        shiftScheduleDao.upsertSchedule(entity)
    }

    suspend fun submitLeaveRequest(
        employeeId: Long,
        leaveType: LeaveType,
        startDateKey: String,
        endDateKey: String,
        reason: String,
        attachmentUri: String?
    ): Result<Unit> {
        if (reason.trim().length < 5) {
            return Result.failure(IllegalArgumentException("Alasan pengajuan minimal 5 karakter."))
        }
        leaveRequestDao.insertLeaveRequest(
            LeaveRequestEntity(
                employeeId = employeeId,
                leaveType = leaveType.code,
                startDateKey = startDateKey,
                endDateKey = endDateKey,
                reason = reason.trim(),
                attachmentUri = attachmentUri,
                status = LeaveStatus.DIAJUKAN.code
            )
        )
        return Result.success(Unit)
    }

    suspend fun deleteLeaveRequest(requestId: Long) {
        leaveRequestDao.deleteLeaveRequest(requestId)
    }

    suspend fun updateEmployeeProfile(
        employee: EmployeeEntity,
        fullName: String,
        role: String,
        department: String,
        phone: String,
        defaultShift: ShiftType,
        avatarUri: String?
    ): Result<Unit> {
        if (fullName.trim().length < 3) {
            return Result.failure(IllegalArgumentException("Nama lengkap minimal 3 karakter."))
        }
        employeeDao.updateEmployee(
            employee.copy(
                fullName = fullName.trim(),
                role = role.trim().ifEmpty { employee.role },
                department = department.trim().ifEmpty { employee.department },
                phone = phone.trim(),
                defaultShiftType = defaultShift.code,
                avatarUri = avatarUri
            )
        )
        return Result.success(Unit)
    }

    suspend fun updateHospitalConfig(
        hospitalName: String,
        latitude: Double,
        longitude: Double,
        radiusMeters: Float
    ) {
        sessionPreferences.updateHospitalLocation(hospitalName, latitude, longitude, radiusMeters)
    }

    private fun evaluateCheckInStatus(nowMillis: Long, shift: ShiftType): AttendanceStatus {
        if (shift == ShiftType.LIBUR) return AttendanceStatus.TEPAT_WAKTU
        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val shiftStartCal = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, shift.startHour)
            set(Calendar.MINUTE, shift.startMinute)
            set(Calendar.SECOND, 0)
            add(Calendar.MINUTE, 15) // 15-minute grace tolerance
        }
        // Only flag late if checking in after grace period within the same shift window
        val currentHour = cal.get(Calendar.HOUR_OF_DAY)
        val isLate = when (shift) {
            ShiftType.MALAM -> currentHour >= 21 && cal.timeInMillis > shiftStartCal.timeInMillis
            else -> cal.timeInMillis > shiftStartCal.timeInMillis
        }
        return if (isLate) AttendanceStatus.TERLAMBAT else AttendanceStatus.TEPAT_WAKTU
    }

    private fun evaluateCheckOutStatus(
        nowMillis: Long,
        shift: ShiftType,
        currentStatusCode: String
    ): AttendanceStatus {
        if (currentStatusCode == AttendanceStatus.TERLAMBAT.code) {
            return AttendanceStatus.TERLAMBAT
        }
        if (shift == ShiftType.LIBUR || shift == ShiftType.MALAM) {
            return AttendanceStatus.SELESAI
        }
        val shiftEndCal = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, shift.endHour)
            set(Calendar.MINUTE, shift.endMinute)
            set(Calendar.SECOND, 0)
        }
        return if (nowMillis < shiftEndCal.timeInMillis - 15 * 60_000L) {
            AttendanceStatus.PULANG_CEPAT
        } else {
            AttendanceStatus.SELESAI
        }
    }
}
