package com.attendance.model;

public class AttendanceRecord {
    private String employeeId;
    private String employeeName;
    private String department;
    private String date;
    private String inTime;      // renamed from "time" → "inTime"
    private String outTime;     // NEW
    private String totalHours;  // NEW  e.g. "8h 25m"
    private String status;      // Present | Work From Home | On Leave | Absent
    private String leaveReason; // NEW – filled when status = "On Leave"
    // Geo
    private Double latitude;
    private Double longitude;
    private String locationStatus;

    public AttendanceRecord() {}

    // ── getters / setters ────────────────────────────────────────────────────

    public String getEmployeeId()           { return employeeId; }
    public void setEmployeeId(String v)     { this.employeeId = v; }

    public String getEmployeeName()         { return employeeName; }
    public void setEmployeeName(String v)   { this.employeeName = v; }

    public String getDepartment()           { return department; }
    public void setDepartment(String v)     { this.department = v; }

    public String getDate()                 { return date; }
    public void setDate(String v)           { this.date = v; }

    public String getInTime()               { return inTime; }
    public void setInTime(String v)         { this.inTime = v; }

    /** Legacy alias used by code that still reads "time" from the JSON */
    public String getTime()                 { return inTime; }
    public void setTime(String v)           { this.inTime = v; }

    public String getOutTime()              { return outTime; }
    public void setOutTime(String v)        { this.outTime = v; }

    public String getTotalHours()           { return totalHours; }
    public void setTotalHours(String v)     { this.totalHours = v; }

    public String getStatus()               { return status; }
    public void setStatus(String v)         { this.status = v; }

    public String getLeaveReason()          { return leaveReason; }
    public void setLeaveReason(String v)    { this.leaveReason = v; }

    public Double getLatitude()             { return latitude; }
    public void setLatitude(Double v)       { this.latitude = v; }

    public Double getLongitude()            { return longitude; }
    public void setLongitude(Double v)      { this.longitude = v; }

    public String getLocationStatus()       { return locationStatus; }
    public void setLocationStatus(String v) { this.locationStatus = v; }
}
