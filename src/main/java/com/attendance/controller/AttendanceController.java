package com.attendance.controller;

import com.attendance.model.*;
import com.attendance.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/api/attendance")
@CrossOrigin(origins = "*")
public class AttendanceController {

    @Autowired private ExcelService       excelService;
    @Autowired private EmployeeService    employeeService;
    @Autowired private GeoFenceService    geoFenceService;
    @Autowired private EmailReportService emailReportService;

    private final Map<String, Long> lastScanTime = new HashMap<>();
    private static final long SCAN_COOLDOWN_MS = 10_000;

    /** Mark attendance via QR code scan (with optional geo data) */
    @PostMapping("/mark")
    public ResponseEntity<ApiResponse> markAttendance(@RequestBody Map<String, String> payload) {
        String employeeId = payload.getOrDefault("employeeId", "").trim();

        if (employeeId.isEmpty()) {
            return bad("Invalid QR code – Employee ID is missing.");
        }

        Optional<Employee> empOpt = employeeService.findById(employeeId);
        if (empOpt.isEmpty()) {
            return bad("Employee not found: " + employeeId);
        }
        Employee emp = empOpt.get();

        // Rate-limit check
        long now = System.currentTimeMillis();
        Long last = lastScanTime.get(employeeId);
        if (last != null && (now - last) < SCAN_COOLDOWN_MS) {
            long remaining = (SCAN_COOLDOWN_MS - (now - last)) / 1000 + 1;
            return bad("Please wait " + remaining + " second(s) before scanning again.");
        }
        lastScanTime.put(employeeId, now);

        // ── Geo-fence check ──────────────────────────────────────────────────
        String locationStatus = "Unknown";
        Double lat = null, lon = null;

        String latStr = payload.get("latitude");
        String lonStr = payload.get("longitude");

        if (latStr != null && lonStr != null && !latStr.isBlank() && !lonStr.isBlank()) {
            try {
                lat = Double.parseDouble(latStr);
                lon = Double.parseDouble(lonStr);
                boolean within = geoFenceService.isWithinOffice(lat, lon);
                locationStatus = within ? "Within Office" : "Outside Office";

                if (!within && geoFenceService.isEnforced()) {
                    double dist = geoFenceService.distanceFromOffice(lat, lon);
                    return bad(String.format(
                        "Attendance denied: You are %.0fm away from the office (allowed radius: %.0fm). "
                        + "Please be at the office to mark attendance.",
                        dist, geoFenceService.getRadiusMeters()));
                }
            } catch (NumberFormatException e) {
                locationStatus = "Invalid GPS";
            }
        } else if (geoFenceService.isEnforced()) {
            // Enforcement on but no GPS provided
            return bad("Attendance denied: Location data is required. Please allow location access and try again.");
        }

        // ── Build record ─────────────────────────────────────────────────────
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy"));
        String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));

        AttendanceRecord record = new AttendanceRecord(
            emp.getEmployeeId(), emp.getName(), emp.getDepartment(), date, time, "Present");
        record.setLatitude(lat);
        record.setLongitude(lon);
        record.setLocationStatus(locationStatus);

        try {
            boolean saved = excelService.appendRecord(record);
            if (!saved) {
                return ResponseEntity.ok(new ApiResponse(false,
                    emp.getName() + " attendance already marked for today.", record));
            }
            String locMsg = "Unknown".equals(locationStatus) ? "" : " (" + locationStatus + ")";
            return ResponseEntity.ok(new ApiResponse(true,
                "Attendance marked for " + emp.getName() + "!" + locMsg, record));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                .body(new ApiResponse(false, "Error saving attendance: " + e.getMessage()));
        }
    }

    /** Get today's attendance */
    @GetMapping("/today")
    public ResponseEntity<?> getTodayAttendance() {
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy"));
        try {
            List<AttendanceRecord> records = excelService.getTodayRecords(today);
            Map<String, Object> result = new HashMap<>();
            result.put("date", today);
            result.put("totalPresent", records.size());
            result.put("records", records);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(new ApiResponse(false, e.getMessage()));
        }
    }

    /** Get all attendance records */
    @GetMapping("/all")
    public ResponseEntity<?> getAllAttendance() {
        try {
            return ResponseEntity.ok(excelService.getAllRecords());
        } catch (Exception e) {
            return ResponseEntity.status(500).body(new ApiResponse(false, e.getMessage()));
        }
    }

    /** Download Excel report */
    @GetMapping("/download")
    public ResponseEntity<byte[]> downloadReport() {
        try {
            byte[] bytes = excelService.getExcelBytes();
            String filename = "attendance_" +
                LocalDate.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy")) + ".xlsx";
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }

    /** Geo-fence config endpoint (for scanner page to know office location) */
    @GetMapping("/geo-config")
    public ResponseEntity<?> getGeoConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("officeLat",    geoFenceService.getOfficeLat());
        config.put("officeLon",    geoFenceService.getOfficeLon());
        config.put("radiusMeters", geoFenceService.getRadiusMeters());
        config.put("enforced",     geoFenceService.isEnforced());
        return ResponseEntity.ok(config);
    }

    /** Manually trigger daily email report */
    @PostMapping("/email/send-daily")
    public ResponseEntity<ApiResponse> triggerDailyEmail() {
        try {
            emailReportService.sendDailyReportNow();
            return ResponseEntity.ok(new ApiResponse(true, "Daily report email sent successfully!"));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                .body(new ApiResponse(false, "Failed to send email: " + e.getMessage()));
        }
    }

    /** Manually trigger weekly email report */
    @PostMapping("/email/send-weekly")
    public ResponseEntity<ApiResponse> triggerWeeklyEmail() {
        try {
            emailReportService.sendWeeklyReportNow();
            return ResponseEntity.ok(new ApiResponse(true, "Weekly report email sent successfully!"));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                .body(new ApiResponse(false, "Failed to send email: " + e.getMessage()));
        }
    }

    private ResponseEntity<ApiResponse> bad(String msg) {
        return ResponseEntity.badRequest().body(new ApiResponse(false, msg));
    }
}
