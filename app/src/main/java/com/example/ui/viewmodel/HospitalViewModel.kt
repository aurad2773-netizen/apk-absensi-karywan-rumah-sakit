package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.AttendanceEntity
import com.example.data.local.EmployeeEntity
import com.example.data.local.LeaveRequestEntity
import com.example.data.local.LeaveType
import com.example.data.local.ShiftScheduleEntity
import com.example.data.local.ShiftType
import com.example.data.local.WorkMode
import com.example.data.preferences.HospitalLocationConfig
import com.example.data.repository.HospitalRepository
import com.example.util.DateTimeUtils
import com.example.util.GpsReading
import com.example.util.HospitalLocationHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MonthlySummary(
    val totalPresent: Int = 0,
    val onTimeCount: Int = 0,
    val lateCount: Int = 0,
    val totalWorkHours: Int = 0
)

data class HospitalDashboardState(
    val employee: EmployeeEntity? = null,
    val todayAttendance: AttendanceEntity? = null,
    val attendanceHistory: List<AttendanceEntity> = emptyList(),
    val shiftSchedules: List<ShiftScheduleEntity> = emptyList(),
    val leaveRequests: List<LeaveRequestEntity> = emptyList(),
    val hospitalConfig: HospitalLocationConfig = HospitalLocationConfig(),
    val monthlySummary: MonthlySummary = MonthlySummary(),
    val todayShift: ShiftType = ShiftType.PAGI
)

data class ActionFeedbackState(
    val isRefreshingGps: Boolean = false,
    val gpsReading: GpsReading? = null,
    val gpsStatusText: String = "Ketuk Perbarui GPS untuk memverifikasi posisi",
    val isSubmitting: Boolean = false,
    val snackbarMessage: String? = null
)

class HospitalViewModel(
    private val employeeId: Long,
    private val repository: HospitalRepository
) : ViewModel() {

    private val _clockMillis = MutableStateFlow(System.currentTimeMillis())
    val clockMillis: StateFlow<Long> = _clockMillis.asStateFlow()

    private val _feedback = MutableStateFlow(ActionFeedbackState())
    val feedback: StateFlow<ActionFeedbackState> = _feedback.asStateFlow()

    val dashboardState: StateFlow<HospitalDashboardState> = combine(
        repository.observeEmployee(employeeId),
        repository.observeTodayAttendance(employeeId),
        repository.observeAttendanceHistory(employeeId),
        repository.observeShiftSchedules(employeeId),
        repository.observeLeaveRequests(employeeId),
        repository.hospitalConfig
    ) { values ->
        val employee = values[0] as EmployeeEntity?
        val todayAttendance = values[1] as AttendanceEntity?
        @Suppress("UNCHECKED_CAST")
        val history = values[2] as List<AttendanceEntity>
        @Suppress("UNCHECKED_CAST")
        val schedules = values[3] as List<ShiftScheduleEntity>
        @Suppress("UNCHECKED_CAST")
        val leaves = values[4] as List<LeaveRequestEntity>
        val config = values[5] as HospitalLocationConfig

        val monthPrefix = DateTimeUtils.currentMonthPrefix()
        val monthlyRecords = history.filter { it.dateKey.startsWith(monthPrefix) }
        val onTime = monthlyRecords.count { it.status == "TEPAT_WAKTU" || it.status == "SELESAI" }
        val late = monthlyRecords.count { it.status == "TERLAMBAT" }
        val totalMinutes = monthlyRecords.sumOf { rec ->
            val end = rec.checkOutTime ?: rec.checkInTime
            ((end - rec.checkInTime).coerceAtLeast(0L) / 60_000L).toInt()
        }

        val todayKey = DateTimeUtils.todayDateKey()
        val scheduledToday = schedules.firstOrNull { it.dateKey == todayKey }
        val todayShift = scheduledToday?.let { ShiftType.fromCode(it.shiftType) }
            ?: employee?.let { ShiftType.fromCode(it.defaultShiftType) }
            ?: ShiftType.PAGI

        HospitalDashboardState(
            employee = employee,
            todayAttendance = todayAttendance,
            attendanceHistory = history,
            shiftSchedules = schedules,
            leaveRequests = leaves,
            hospitalConfig = config,
            monthlySummary = MonthlySummary(
                totalPresent = monthlyRecords.size,
                onTimeCount = onTime,
                lateCount = late,
                totalWorkHours = totalMinutes / 60
            ),
            todayShift = todayShift
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = HospitalDashboardState()
    )

    init {
        viewModelScope.launch {
            while (true) {
                _clockMillis.value = System.currentTimeMillis()
                delay(1_000L)
            }
        }
    }

    fun clearSnackbar() {
        _feedback.update { it.copy(snackbarMessage = null) }
    }

    fun refreshGps(context: Context) {
        viewModelScope.launch {
            val config = dashboardState.value.hospitalConfig
            if (!HospitalLocationHelper.hasLocationPermission(context)) {
                _feedback.update {
                    it.copy(
                        isRefreshingGps = false,
                        gpsReading = null,
                        gpsStatusText = "Izin lokasi belum diberikan. Aktifkan lokasi untuk verifikasi radius RS."
                    )
                }
                return@launch
            }
            _feedback.update {
                it.copy(isRefreshingGps = true, gpsStatusText = "Mencari sinyal GPS...")
            }
            val reading = HospitalLocationHelper.fetchCurrentLocation(
                context = context,
                hospitalLat = config.latitude,
                hospitalLng = config.longitude
            )
            if (reading != null) {
                val inside = reading.distanceFromHospitalMeters <= config.radiusMeters.toInt()
                val statusLabel = if (inside) {
                    "Di dalam area ${config.hospitalName} (${reading.distanceFromHospitalMeters} m)"
                } else {
                    "Di luar radius RS (${reading.distanceFromHospitalMeters} m dari titik RS)"
                }
                _feedback.update {
                    it.copy(
                        isRefreshingGps = false,
                        gpsReading = reading,
                        gpsStatusText = statusLabel
                    )
                }
            } else {
                _feedback.update {
                    it.copy(
                        isRefreshingGps = false,
                        gpsReading = null,
                        gpsStatusText = "GPS tidak merespons. Anda tetap bisa absen dengan mode verifikasi manual."
                    )
                }
            }
        }
    }

    fun performCheckIn(
        shiftType: ShiftType,
        workMode: WorkMode,
        notes: String
    ) {
        viewModelScope.launch {
            _feedback.update { it.copy(isSubmitting = true) }
            val result = repository.checkIn(
                employeeId = employeeId,
                shiftType = shiftType,
                workMode = workMode,
                gpsReading = _feedback.value.gpsReading,
                hospitalConfig = dashboardState.value.hospitalConfig,
                notes = notes
            )
            result.fold(
                onSuccess = {
                    _feedback.update {
                        it.copy(
                            isSubmitting = false,
                            snackbarMessage = "Absen masuk berhasil dicatat!"
                        )
                    }
                },
                onFailure = { err ->
                    _feedback.update {
                        it.copy(
                            isSubmitting = false,
                            snackbarMessage = err.message ?: "Gagal mencatat absen masuk."
                        )
                    }
                }
            )
        }
    }

    fun performCheckOut(notes: String) {
        viewModelScope.launch {
            _feedback.update { it.copy(isSubmitting = true) }
            val result = repository.checkOut(
                employeeId = employeeId,
                gpsReading = _feedback.value.gpsReading,
                notes = notes
            )
            result.fold(
                onSuccess = {
                    _feedback.update {
                        it.copy(
                            isSubmitting = false,
                            snackbarMessage = "Absen pulang berhasil dicatat. Terima kasih atas dedikasi Anda!"
                        )
                    }
                },
                onFailure = { err ->
                    _feedback.update {
                        it.copy(
                            isSubmitting = false,
                            snackbarMessage = err.message ?: "Gagal mencatat absen pulang."
                        )
                    }
                }
            )
        }
    }

    fun saveShiftSchedule(
        dateKey: String,
        shiftType: ShiftType,
        unitName: String,
        notes: String
    ) {
        viewModelScope.launch {
            repository.upsertShiftSchedule(employeeId, dateKey, shiftType, unitName, notes)
            _feedback.update { it.copy(snackbarMessage = "Jadwal shift berhasil diperbarui.") }
        }
    }

    fun submitLeaveRequest(
        leaveType: LeaveType,
        startDateKey: String,
        endDateKey: String,
        reason: String,
        attachmentUri: String?,
        onDone: () -> Unit
    ) {
        viewModelScope.launch {
            val result = repository.submitLeaveRequest(
                employeeId = employeeId,
                leaveType = leaveType,
                startDateKey = startDateKey,
                endDateKey = endDateKey,
                reason = reason,
                attachmentUri = attachmentUri
            )
            result.fold(
                onSuccess = {
                    _feedback.update { it.copy(snackbarMessage = "Pengajuan ${leaveType.label} berhasil dikirim.") }
                    onDone()
                },
                onFailure = { err ->
                    _feedback.update { it.copy(snackbarMessage = err.message ?: "Gagal mengirim pengajuan.") }
                }
            )
        }
    }

    fun deleteLeaveRequest(requestId: Long) {
        viewModelScope.launch {
            repository.deleteLeaveRequest(requestId)
            _feedback.update { it.copy(snackbarMessage = "Pengajuan dibatalkan.") }
        }
    }

    fun updateProfile(
        fullName: String,
        role: String,
        department: String,
        phone: String,
        defaultShift: ShiftType,
        avatarUri: String?
    ) {
        viewModelScope.launch {
            val current = dashboardState.value.employee ?: return@launch
            val result = repository.updateEmployeeProfile(
                employee = current,
                fullName = fullName,
                role = role,
                department = department,
                phone = phone,
                defaultShift = defaultShift,
                avatarUri = avatarUri
            )
            result.fold(
                onSuccess = {
                    _feedback.update { it.copy(snackbarMessage = "Profil karyawan berhasil disimpan.") }
                },
                onFailure = { err ->
                    _feedback.update { it.copy(snackbarMessage = err.message ?: "Gagal menyimpan profil.") }
                }
            )
        }
    }

    fun calibrateHospitalWithCurrentGps(
        context: Context,
        hospitalName: String,
        radiusMeters: Float
    ) {
        viewModelScope.launch {
            if (!HospitalLocationHelper.hasLocationPermission(context)) {
                _feedback.update { it.copy(snackbarMessage = "Berikan izin lokasi terlebih dahulu untuk kalibrasi GPS.") }
                return@launch
            }
            _feedback.update { it.copy(isRefreshingGps = true) }
            val current = HospitalLocationHelper.fetchCurrentLocation(
                context = context,
                hospitalLat = dashboardState.value.hospitalConfig.latitude,
                hospitalLng = dashboardState.value.hospitalConfig.longitude
            )
            if (current != null) {
                repository.updateHospitalConfig(
                    hospitalName = hospitalName,
                    latitude = current.latitude,
                    longitude = current.longitude,
                    radiusMeters = radiusMeters
                )
                refreshGps(context)
                _feedback.update {
                    it.copy(
                        isRefreshingGps = false,
                        snackbarMessage = "Titik RS dikalibrasi ke lokasi GPS Anda saat ini."
                    )
                }
            } else {
                _feedback.update {
                    it.copy(
                        isRefreshingGps = false,
                        snackbarMessage = "Tidak dapat memperoleh koordinat GPS saat ini."
                    )
                }
            }
        }
    }

    fun saveHospitalSettings(hospitalName: String, radiusMeters: Float) {
        viewModelScope.launch {
            val current = dashboardState.value.hospitalConfig
            repository.updateHospitalConfig(
                hospitalName = hospitalName,
                latitude = current.latitude,
                longitude = current.longitude,
                radiusMeters = radiusMeters
            )
            _feedback.update { it.copy(snackbarMessage = "Pengaturan lokasi rumah sakit disimpan.") }
        }
    }

    class Factory(
        private val employeeId: Long,
        private val repository: HospitalRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HospitalViewModel(employeeId, repository) as T
        }
    }
}
