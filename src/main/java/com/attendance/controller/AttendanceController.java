package com.attendance.controller;

import com.attendance.model.*;
import com.attendance.service.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/attendance")
@CrossOrigin(origins = "*")
public class AttendanceController {

    @Autowired private ExcelService       excelService;
    @Autowired private EmployeeService    employeeService;
    @Autowired private GeoFenceService    geoFenceService;
    @Autowired private EmailReportService emailReportService;

    // ── Per-employee scan cooldown (prevents double-tap) ──────────────────────
    private final Map<String, Long> lastScanTime = new ConcurrentHashMap<>();
    private static final long SCAN_COOLDOWN_MS = 10_000;

    // ── IP-based daily block: ip → "dd-MM-yyyy" of last IN scan ──────────────
    private final Map<String, String> ipDailyBlock = new ConcurrentHashMap<>();

    // ─────────────────────────────────────────────────────────────────────────
    // MARK IN
    // ─────────────────────────────────────────────────────────────────────────

    @PostMapping("/mark")
    public ResponseEntity<ApiResponse> markAttendance(
            @RequestBody Map<String, String> payload,
            HttpServletRequest request) {

        String employeeId = payload.getOrDefault("employeeId", "").trim();
        if (employeeId.isEmpty()) return bad("Invalid QR code – Employee ID is missing.");

        Optional<Employee> empOpt = employeeService.findById(employeeId);
        if (empOpt.isEmpty()) return bad("Employee not found: " + employeeId);
        Employee emp = empOpt.get();

        // ── IP block: one IN per IP per day ───────────────────────────────────
        String clientIp  = extractIp(request);
        String today     = LocalDate.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy"));
        String blockedOn = ipDailyBlock.get(clientIp);
        if (today.equals(blockedOn)) {
            return bad("Attendance already marked from this device/IP today. " +
                       "One check-in per device per day is allowed.");
        }

        // ── Scan cooldown ─────────────────────────────────────────────────────
        long now = System.currentTimeMillis();
        Long last = lastScanTime.get(employeeId);
        if (last != null && (now - last) < SCAN_COOLDOWN_MS) {
            long remaining = (SCAN_COOLDOWN_MS - (now - last)) / 1000 + 1;
            return bad("Please wait " + remaining + " second(s) before scanning again.");
        }
        lastScanTime.put(employeeId, now);

        // ── Geo-fence ─────────────────────────────────────────────────────────
        // Geo-fence is only enforced for "Present" status.
        // Work From Home and On Leave do not require office proximity.
        // ── Status / leave reason ─────────────────────────────────────────────
        String status      = payload.getOrDefault("status", "Present").trim();
        String leaveReason = payload.getOrDefault("leaveReason", "").trim();
        if (!"Present".equals(status) && !"Work From Home".equals(status) && !"On Leave".equals(status)) {
            status = "Present";
        }
        if ("On Leave".equals(status) && leaveReason.isEmpty()) {
            return bad("Please provide a leave reason.");
        }

        String locationStatus = "Unknown";
        Double lat = null, lon = null;
        String latStr = payload.get("latitude"), lonStr = payload.get("longitude");
        boolean geoRequired = "Present".equals(status) && geoFenceService.isEnforced();

        if (latStr != null && lonStr != null && !latStr.isBlank() && !lonStr.isBlank()) {
            try {
                lat = Double.parseDouble(latStr);
                lon = Double.parseDouble(lonStr);
                boolean within = geoFenceService.isWithinOffice(lat, lon);
                locationStatus = within ? "Within Office" : "Outside Office";
                if (!within && geoRequired) {
                    double dist = geoFenceService.distanceFromOffice(lat, lon);
                    return bad(String.format(
                        "Attendance denied: You are %.0fm away from the office (allowed: %.0fm).",
                        dist, geoFenceService.getRadiusMeters()));
                }
            } catch (NumberFormatException e) { locationStatus = "Invalid GPS"; }
        } else if (geoRequired) {
            return bad("Attendance denied: Location data is required. Please allow location access.");
        }

        // ── Build & save record ───────────────────────────────────────────────
        String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        AttendanceRecord record = new AttendanceRecord();
        record.setEmployeeId(emp.getEmployeeId());
        record.setEmployeeName(emp.getName());
        record.setDepartment(emp.getDepartment());
        record.setDate(today);
        record.setInTime(time);
        record.setStatus(status);
        record.setLeaveReason(leaveReason);
        record.setLatitude(lat);
        record.setLongitude(lon);
        record.setLocationStatus(locationStatus);

        try {
            boolean saved = excelService.appendRecord(record);
            if (!saved) {
                return ResponseEntity.ok(new ApiResponse(false,
                    emp.getName() + " – attendance already marked for today.", record));
            }
            // Lock this IP for today
            ipDailyBlock.put(clientIp, today);
            String locMsg = "Unknown".equals(locationStatus) ? "" : " (" + locationStatus + ")";
            return ResponseEntity.ok(new ApiResponse(true,
                "✅ Check-in recorded for " + emp.getName() + "!" + locMsg, record));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                .body(new ApiResponse(false, "Error saving attendance: " + e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MARK OUT
    // ─────────────────────────────────────────────────────────────────────────

    @PostMapping("/checkout")
    public ResponseEntity<ApiResponse> markCheckout(@RequestBody Map<String, String> payload) {
        String employeeId = payload.getOrDefault("employeeId", "").trim();
        if (employeeId.isEmpty()) return bad("Employee ID is missing.");

        Optional<Employee> empOpt = employeeService.findById(employeeId);
        if (empOpt.isEmpty()) return bad("Employee not found: " + employeeId);
        Employee emp = empOpt.get();

        String today   = LocalDate.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy"));
        String outTime = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));

        try {
            String totalHours = excelService.markOutTime(employeeId, today, outTime);
            if (totalHours == null) {
                return bad(emp.getName() + " has not checked in today. Please mark IN first.");
            }
            Map<String, String> data = new HashMap<>();
            data.put("employeeId",   employeeId);
            data.put("employeeName", emp.getName());
            data.put("outTime",      outTime);
            data.put("totalHours",   totalHours);
            return ResponseEntity.ok(new ApiResponse(true,
                "✅ Check-out recorded for " + emp.getName() + "! Total: " + totalHours, data));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                .body(new ApiResponse(false, "Error saving check-out: " + e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // READ
    // ─────────────────────────────────────────────────────────────────────────

    @GetMapping("/today")
    public ResponseEntity<?> getTodayAttendance() {
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy"));
        try {
            List<AttendanceRecord> records = excelService.getTodayRecords(today);
            Map<String, Object> result = new HashMap<>();
            result.put("date", today);
            result.put("totalPresent",
                records.stream().filter(r -> "Present".equals(r.getStatus())).count());
            result.put("records", records);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(new ApiResponse(false, e.getMessage()));
        }
    }

    @GetMapping("/all")
    public ResponseEntity<?> getAllAttendance() {
        try { return ResponseEntity.ok(excelService.getAllRecords()); }
        catch (Exception e) {
            return ResponseEntity.status(500).body(new ApiResponse(false, e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // UPDATE / DELETE (admin)
    // ─────────────────────────────────────────────────────────────────────────

    @PutMapping("/update")
    public ResponseEntity<ApiResponse> updateAttendance(@RequestBody Map<String, String> payload) {
        String employeeId   = payload.getOrDefault("employeeId",   "").trim();
        String originalDate = payload.getOrDefault("originalDate", "").trim();
        if (employeeId.isEmpty() || originalDate.isEmpty())
            return bad("Employee ID and original date are required.");

        Optional<Employee> empOpt = employeeService.findById(employeeId);
        AttendanceRecord updated = new AttendanceRecord();
        updated.setEmployeeId(employeeId);
        updated.setEmployeeName(payload.getOrDefault("employeeName",
            empOpt.map(Employee::getName).orElse("")));
        updated.setDepartment(payload.getOrDefault("department",
            empOpt.map(Employee::getDepartment).orElse("")));
        updated.setDate(payload.getOrDefault("date", originalDate));
        updated.setInTime(payload.getOrDefault("inTime", ""));
        updated.setOutTime(payload.getOrDefault("outTime", ""));
        updated.setTotalHours(payload.getOrDefault("totalHours", ""));
        updated.setStatus(payload.getOrDefault("status", "Present"));
        updated.setLeaveReason(payload.getOrDefault("leaveReason", ""));

        try {
            boolean ok = excelService.updateRecord(employeeId, originalDate, updated);
            if (!ok) return ResponseEntity.status(404)
                .body(new ApiResponse(false, "No matching record found."));
            return ResponseEntity.ok(new ApiResponse(true, "Record updated successfully.", updated));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                .body(new ApiResponse(false, "Error: " + e.getMessage()));
        }
    }

    @DeleteMapping("/delete")
    public ResponseEntity<ApiResponse> deleteAttendance(@RequestParam String employeeId,
                                                         @RequestParam String date) {
        try {
            boolean ok = excelService.deleteRecord(employeeId.trim(), date.trim());
            if (!ok) return ResponseEntity.status(404)
                .body(new ApiResponse(false, "No matching record found."));
            return ResponseEntity.ok(new ApiResponse(true, "Record deleted successfully."));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                .body(new ApiResponse(false, "Error: " + e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DOWNLOAD / GEO-CONFIG / EMAIL
    // ─────────────────────────────────────────────────────────────────────────

    @GetMapping("/download")
    public ResponseEntity<byte[]> downloadReport() {
        try {
            byte[] bytes = excelService.getExcelBytes();
            String fn = "attendance_" +
                LocalDate.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy")) + ".xlsx";
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fn + "\"")
                .contentType(MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
        } catch (Exception e) { return ResponseEntity.status(500).build(); }
    }

    @GetMapping("/geo-config")
    public ResponseEntity<?> getGeoConfig() {
        Map<String, Object> cfg = new HashMap<>();
        cfg.put("officeLat",    geoFenceService.getOfficeLat());
        cfg.put("officeLon",    geoFenceService.getOfficeLon());
        cfg.put("radiusMeters", geoFenceService.getRadiusMeters());
        cfg.put("enforced",     geoFenceService.isEnforced());
        return ResponseEntity.ok(cfg);
    }

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

    // ─────────────────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    private String extractIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) return forwarded.split(",")[0].trim();
        return request.getRemoteAddr();
    }

    private ResponseEntity<ApiResponse> bad(String msg) {
        return ResponseEntity.badRequest().body(new ApiResponse(false, msg));
    }
}
