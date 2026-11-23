package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class ShiftType(
    val code: String,
    val label: String,
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int
) {
    PAGI("PAGI", "Shift Pagi", 7, 0, 14, 0),
    SIANG("SIANG", "Shift Siang", 14, 0, 21, 0),
    MALAM("MALAM", "Shift Malam", 21, 0, 7, 0),
    NON_SHIFT("NON_SHIFT", "Reguler (Non-Shift)", 8, 0, 16, 0),
    LIBUR("LIBUR", "Jadwal Libur", 0, 0, 0, 0);

    fun timeRangeLabel(): String =
        if (this == LIBUR) "Libur"
        else "%02d:%02d - %02d:%02d".format(startHour, startMinute, endHour, endMinute)

    companion object {
        fun fromCode(code: String): ShiftType =
            entries.firstOrNull { it.code == code } ?: PAGI

        val workingShifts: List<ShiftType>
            get() = listOf(PAGI, SIANG, MALAM, NON_SHIFT)
    }
}

enum class WorkMode(val code: String, val label: String) {
    WFO_RS("WFO_RS", "Area Rumah Sakit"),
    DINAS_LUAR("DINAS_LUAR", "Dinas Luar / Homecare");

    companion object {
        fun fromCode(code: String): WorkMode =
            entries.firstOrNull { it.code == code } ?: WFO_RS
    }
}

enum class AttendanceStatus(val code: String, val label: String) {
    TEPAT_WAKTU("TEPAT_WAKTU", "Tepat Waktu"),
    TERLAMBAT("TERLAMBAT", "Terlambat"),
    PULANG_CEPAT("PULANG_CEPAT", "Pulang Cepat"),
    SELESAI("SELESAI", "Shift Selesai");

    companion object {
        fun fromCode(code: String): AttendanceStatus =
            entries.firstOrNull { it.code == code } ?: TEPAT_WAKTU
    }
}

enum class LeaveType(val code: String, val label: String) {
    CUTI("CUTI", "Cuti Tahunan"),
    SAKIT("SAKIT", "Izin Sakit"),
    IZIN("IZIN", "Izin Keperluan"),
    LEMBUR("LEMBUR", "Pengajuan Lembur"),
    TUKAR_SHIFT("TUKAR_SHIFT", "Tukar Shift");

    companion object {
        fun fromCode(code: String): LeaveType =
            entries.firstOrNull { it.code == code } ?: IZIN
    }
}

enum class LeaveStatus(val code: String, val label: String) {
    DIAJUKAN("DIAJUKAN", "Menunggu Persetujuan"),
    DISETUJUI("DISETUJUI", "Disetujui"),
    DITOLAK("DITOLAK", "Ditolak");

    companion object {
        fun fromCode(code: String): LeaveStatus =
            entries.firstOrNull { it.code == code } ?: DIAJUKAN
    }
}

@Entity(
    tableName = "employees",
    indices = [
        Index(value = ["nip"], unique = true),
        Index(value = ["email"], unique = true)
    ]
)
data class EmployeeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val nip: String,
    val fullName: String,
    val email: String,
    val passwordHash: String,
    val salt: String,
    val role: String,
    val department: String,
    val phone: String,
    val employmentStatus: String,
    val defaultShiftType: String,
    val avatarUri: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "attendances",
    indices = [Index(value = ["employeeId", "dateKey"])]
)
data class AttendanceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val employeeId: Long,
    val dateKey: String,
    val shiftType: String,
    val workMode: String,
    val checkInTime: Long,
    val checkOutTime: Long? = null,
    val status: String,
    val checkInLat: Double? = null,
    val checkInLng: Double? = null,
    val checkOutLat: Double? = null,
    val checkOutLng: Double? = null,
    val distanceMeters: Int? = null,
    val locationLabel: String,
    val notes: String = ""
)

@Entity(
    tableName = "shift_schedules",
    indices = [Index(value = ["employeeId", "dateKey"], unique = true)]
)
data class ShiftScheduleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val employeeId: Long,
    val dateKey: String,
    val shiftType: String,
    val unitName: String,
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int,
    val notes: String = ""
)

@Entity(
    tableName = "leave_requests",
    indices = [Index(value = ["employeeId", "createdAt"])]
)
data class LeaveRequestEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val employeeId: Long,
    val leaveType: String,
    val startDateKey: String,
    val endDateKey: String,
    val reason: String,
    val attachmentUri: String? = null,
    val status: String = LeaveStatus.DIAJUKAN.code,
    val createdAt: Long = System.currentTimeMillis()
)
