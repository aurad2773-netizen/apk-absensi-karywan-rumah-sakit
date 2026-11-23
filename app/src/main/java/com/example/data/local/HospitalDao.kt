package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface EmployeeDao {
    @Query("SELECT * FROM employees WHERE id = :id LIMIT 1")
    fun observeEmployeeById(id: Long): Flow<EmployeeEntity?>

    @Query("SELECT * FROM employees WHERE id = :id LIMIT 1")
    suspend fun getEmployeeById(id: Long): EmployeeEntity?

    @Query("SELECT * FROM employees WHERE LOWER(nip) = LOWER(:identifier) OR LOWER(email) = LOWER(:identifier) LIMIT 1")
    suspend fun findByNipOrEmail(identifier: String): EmployeeEntity?

    @Query("SELECT COUNT(*) FROM employees")
    suspend fun countEmployees(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEmployee(employee: EmployeeEntity): Long

    @Update
    suspend fun updateEmployee(employee: EmployeeEntity)
}

@Dao
interface AttendanceDao {
    @Query("SELECT * FROM attendances WHERE employeeId = :employeeId ORDER BY checkInTime DESC")
    fun observeAttendanceByEmployee(employeeId: Long): Flow<List<AttendanceEntity>>

    @Query("SELECT * FROM attendances WHERE employeeId = :employeeId AND dateKey = :dateKey ORDER BY checkInTime DESC LIMIT 1")
    fun observeTodayAttendance(employeeId: Long, dateKey: String): Flow<AttendanceEntity?>

    @Query("SELECT * FROM attendances WHERE employeeId = :employeeId AND dateKey = :dateKey ORDER BY checkInTime DESC LIMIT 1")
    suspend fun getTodayAttendance(employeeId: Long, dateKey: String): AttendanceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttendance(attendance: AttendanceEntity): Long

    @Update
    suspend fun updateAttendance(attendance: AttendanceEntity)
}

@Dao
interface ShiftScheduleDao {
    @Query("SELECT * FROM shift_schedules WHERE employeeId = :employeeId ORDER BY dateKey ASC")
    fun observeSchedulesByEmployee(employeeId: Long): Flow<List<ShiftScheduleEntity>>

    @Query("SELECT * FROM shift_schedules WHERE employeeId = :employeeId AND dateKey = :dateKey LIMIT 1")
    suspend fun getScheduleForDate(employeeId: Long, dateKey: String): ShiftScheduleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSchedule(schedule: ShiftScheduleEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnore(schedules: List<ShiftScheduleEntity>)
}

@Dao
interface LeaveRequestDao {
    @Query("SELECT * FROM leave_requests WHERE employeeId = :employeeId ORDER BY createdAt DESC")
    fun observeLeaveRequestsByEmployee(employeeId: Long): Flow<List<LeaveRequestEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLeaveRequest(request: LeaveRequestEntity): Long

    @Query("UPDATE leave_requests SET status = :status WHERE id = :requestId")
    suspend fun updateLeaveStatus(requestId: Long, status: String)

    @Query("DELETE FROM leave_requests WHERE id = :requestId")
    suspend fun deleteLeaveRequest(requestId: Long)
}
