package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.local.AttendanceStatus
import com.example.data.local.LeaveStatus
import com.example.data.local.ShiftType
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusAmberContainer
import com.example.ui.theme.StatusCrimson
import com.example.ui.theme.StatusCrimsonContainer
import com.example.ui.theme.StatusEmerald
import com.example.ui.theme.StatusEmeraldContainer
import com.example.ui.theme.StatusIndigo
import com.example.ui.theme.StatusIndigoContainer

@Composable
fun EmployeeAvatar(
    fullName: String,
    avatarUri: String?,
    size: Dp = 52.dp,
    modifier: Modifier = Modifier
) {
    val initials = fullName
        .split(" ")
        .filter { it.isNotBlank() && !it.endsWith(".") }
        .take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .joinToString("")
        .ifEmpty { "RS" }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.secondary
                    )
                )
            )
            .border(2.dp, MaterialTheme.colorScheme.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (!avatarUri.isNullOrBlank()) {
            AsyncImage(
                model = avatarUri,
                contentDescription = stringResource(R.string.cd_employee_avatar),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(
                text = initials,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}

@Composable
fun StatusPill(
    text: String,
    containerColor: Color,
    contentColor: Color,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = containerColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun AttendanceStatusBadge(statusCode: String, modifier: Modifier = Modifier) {
    val status = AttendanceStatus.fromCode(statusCode)
    val (bg, fg, icon) = when (status) {
        AttendanceStatus.TEPAT_WAKTU -> Triple(StatusEmeraldContainer, StatusEmerald, Icons.Default.CheckCircle)
        AttendanceStatus.SELESAI -> Triple(StatusIndigoContainer, StatusIndigo, Icons.Default.Verified)
        AttendanceStatus.TERLAMBAT -> Triple(StatusAmberContainer, StatusAmber, Icons.Default.Schedule)
        AttendanceStatus.PULANG_CEPAT -> Triple(StatusCrimsonContainer, StatusCrimson, Icons.Default.Error)
    }
    StatusPill(text = status.label, containerColor = bg, contentColor = fg, icon = icon, modifier = modifier)
}

@Composable
fun LeaveStatusBadge(statusCode: String, modifier: Modifier = Modifier) {
    val status = LeaveStatus.fromCode(statusCode)
    val (bg, fg, icon) = when (status) {
        LeaveStatus.DIAJUKAN -> Triple(StatusAmberContainer, StatusAmber, Icons.Default.Schedule)
        LeaveStatus.DISETUJUI -> Triple(StatusEmeraldContainer, StatusEmerald, Icons.Default.CheckCircle)
        LeaveStatus.DITOLAK -> Triple(StatusCrimsonContainer, StatusCrimson, Icons.Default.Error)
    }
    StatusPill(text = status.label, containerColor = bg, contentColor = fg, icon = icon, modifier = modifier)
}

@Composable
fun shiftAccentColor(shiftType: ShiftType): Pair<Color, Color> {
    return when (shiftType) {
        ShiftType.PAGI -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        ShiftType.SIANG -> StatusAmberContainer to StatusAmber
        ShiftType.MALAM -> StatusIndigoContainer to StatusIndigo
        ShiftType.NON_SHIFT -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        ShiftType.LIBUR -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
}

@Composable
fun SectionHeader(
    title: String,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        if (actionText != null && onActionClick != null) {
            androidx.compose.material3.TextButton(onClick = onActionClick) {
                Text(
                    text = actionText,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
