package com.attendance.model;

public class AttendanceRecord {
    private String employeeId;
    private String employeeName;
    private String department;
    private String date;
    private String time;
    private String status;
    // Geo fields
    private Double latitude;
    private Double longitude;
    private String locationStatus; // "Within Office", "Outside Office", "Unknown"

    public AttendanceRecord() {}

    public AttendanceRecord(String employeeId, String employeeName, String department,
                             String date, String time, String status) {
        this.employeeId = employeeId;
        this.employeeName = employeeName;
        this.department = department;
        this.date = date;
        this.time = time;
        this.status = status;
    }

    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }

    public String getEmployeeName() { return employeeName; }
    public void setEmployeeName(String employeeName) { this.employeeName = employeeName; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public String getTime() { return time; }
    public void setTime(String time) { this.time = time; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }

    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }

    public String getLocationStatus() { return locationStatus; }
    public void setLocationStatus(String locationStatus) { this.locationStatus = locationStatus; }
}
