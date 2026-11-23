package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.AttendanceEntity
import com.example.data.local.AttendanceStatus
import com.example.data.local.ShiftScheduleEntity
import com.example.data.local.ShiftType
import com.example.data.local.WorkMode
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.components.shiftAccentColor
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.util.DateTimeUtils

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScheduleScreen(
    schedules: List<ShiftScheduleEntity>,
    defaultDepartment: String,
    defaultShift: ShiftType,
    onSaveShift: (dateKey: String, shiftType: ShiftType, unitName: String, notes: String) -> Unit
) {
    val dateWindow = remember { (-2..11).map { DateTimeUtils.dateKeyForOffset(it) } }
    val todayKey = remember { DateTimeUtils.todayDateKey() }
    var selectedDateKey by remember { mutableStateOf(todayKey) }
    var editingDateKey by remember { mutableStateOf<String?>(null) }

    val scheduleMap = remember(schedules) { schedules.associateBy { it.dateKey } }
    val selectedSchedule = scheduleMap[selectedDateKey]
    val selectedShiftType = selectedSchedule?.let { ShiftType.fromCode(it.shiftType) } ?: defaultShift

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SectionHeader(title = "Jadwal Shift Medis")
                Text(
                    text = "Ketuk tanggal untuk melihat atau mengatur jadwal jaga unit rumah sakit",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 14-Day Interactive Calendar Strip
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(dateWindow, key = { it }) { dateKey ->
                        val (dayName, dayNum) = DateTimeUtils.formatDayChip(dateKey)
                        val isSelected = dateKey == selectedDateKey
                        val isToday = dateKey == todayKey
                        val dayShift = scheduleMap[dateKey]?.let { ShiftType.fromCode(it.shiftType) } ?: defaultShift
                        val (pillBg, pillFg) = shiftAccentColor(dayShift)

                        Surface(
                            modifier = Modifier
                                .width(64.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { selectedDateKey = dateKey }
                                .testTag("schedule_day_$dateKey"),
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(
                                width = if (isToday && !isSelected) 1.5.dp else 1.dp,
                                color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 12.dp, horizontal = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = dayName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = dayNum,
                                    fontFamily = JetBrainsMonoFamily,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurface
                                )
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) MaterialTheme.colorScheme.onPrimary else pillFg)
                                )
                                Text(
                                    text = when (dayShift) {
                                        ShiftType.PAGI -> "Pagi"
                                        ShiftType.SIANG -> "Siang"
                                        ShiftType.MALAM -> "Malam"
                                        ShiftType.NON_SHIFT -> "Reg"
                                        ShiftType.LIBUR -> "Libur"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else pillFg,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            // Selected Day Shift Detail Card
            item {
                val (badgeBg, badgeFg) = shiftAccentColor(selectedShiftType)
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CalendarMonth,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = DateTimeUtils.formatShortDateFromKey(selectedDateKey),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            StatusPill(
                                text = selectedShiftType.label,
                                containerColor = badgeBg,
                                contentColor = badgeFg
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Jam Dinas: ${selectedShiftType.timeRangeLabel()}",
                                fontFamily = JetBrainsMonoFamily,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.LocalHospital,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Unit: ${selectedSchedule?.unitName ?: defaultDepartment}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        if (!selectedSchedule?.notes.isNullOrBlank()) {
                            Text(
                                text = "Keterangan: ${selectedSchedule?.notes}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        OutlinedButton(
                            onClick = { editingDateKey = selectedDateKey },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("edit_shift_button")
                        ) {
                            Icon(Icons.Default.EditCalendar, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Ubah Jadwal Shift Tanggal Ini")
                        }
                    }
                }
            }

            // Legend + Full Schedule List
            item {
                SectionHeader(title = "Daftar Roster Shift")
            }

            items(dateWindow, key = { "row_$it" }) { dateKey ->
                val item = scheduleMap[dateKey]
                val shift = item?.let { ShiftType.fromCode(it.shiftType) } ?: defaultShift
                val (bg, fg) = shiftAccentColor(shift)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            selectedDateKey = dateKey
                            editingDateKey = dateKey
                        },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = DateTimeUtils.formatShortDateFromKey(dateKey) +
                                    if (dateKey == todayKey) " (Hari Ini)" else "",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${item?.unitName ?: defaultDepartment} • ${shift.timeRangeLabel()}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        StatusPill(
                            text = shift.label,
                            containerColor = bg,
                            contentColor = fg
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }
        }
    }

    // Shift Edit Dialog
    editingDateKey?.let { targetDate ->
        val existing = scheduleMap[targetDate]
        var pickedShift by remember(targetDate) {
            mutableStateOf(existing?.let { ShiftType.fromCode(it.shiftType) } ?: defaultShift)
        }
        var unitText by remember(targetDate) {
            mutableStateOf(existing?.unitName ?: defaultDepartment)
        }
        var noteText by remember(targetDate) {
            mutableStateOf(existing?.notes ?: "")
        }

        AlertDialog(
            onDismissRequest = { editingDateKey = null },
            title = { Text("Atur Shift ${DateTimeUtils.formatShortDateFromKey(targetDate)}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ShiftType.entries.forEach { shift ->
                            FilterChip(
                                selected = pickedShift == shift,
                                onClick = { pickedShift = shift },
                                label = { Text(shift.label) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = unitText,
                        onValueChange = { unitText = it },
                        label = { Text("Unit / Ruangan") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = it },
                        label = { Text("Catatan Jaga") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSaveShift(targetDate, pickedShift, unitText, noteText)
                        editingDateKey = null
                    }
                ) {
                    Text("Simpan")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingDateKey = null }) {
                    Text("Batal")
                }
            }
        )
    }
}

private enum class HistoryFilter(val label: String) {
    SEMUA("Semua"),
    TEPAT_WAKTU("Tepat Waktu"),
    TERLAMBAT("Terlambat"),
    DINAS_LUAR("Dinas Luar")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HistoryScreen(
    history: List<AttendanceEntity>
) {
    var activeFilter by remember { mutableStateOf(HistoryFilter.SEMUA) }

    val filtered = remember(history, activeFilter) {
        when (activeFilter) {
            HistoryFilter.SEMUA -> history
            HistoryFilter.TEPAT_WAKTU -> history.filter {
                it.status == AttendanceStatus.TEPAT_WAKTU.code || it.status == AttendanceStatus.SELESAI.code
            }
            HistoryFilter.TERLAMBAT -> history.filter { it.status == AttendanceStatus.TERLAMBAT.code }
            HistoryFilter.DINAS_LUAR -> history.filter { it.workMode == WorkMode.DINAS_LUAR.code }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SectionHeader(title = "Riwayat Presensi Karyawan")
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    HistoryFilter.entries.forEach { filter ->
                        FilterChip(
                            selected = activeFilter == filter,
                            onClick = { activeFilter = filter },
                            label = { Text(filter.label) },
                            modifier = Modifier.testTag("history_filter_${filter.name.lowercase()}")
                        )
                    }
                }
            }

            if (filtered.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.img_empty_attendance),
                                contentDescription = stringResource(R.string.cd_empty_attendance),
                                modifier = Modifier
                                    .height(150.dp)
                                    .clip(RoundedCornerShape(16.dp)),
                                contentScale = ContentScale.Fit
                            )
                            Text(
                                text = "Belum Ada Catatan Presensi",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Data absen masuk dan pulang Anda akan muncul secara otomatis di sini.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(filtered, key = { it.id }) { record ->
                    AttendanceHistoryCard(record = record)
                }
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }
        }
    }
}
