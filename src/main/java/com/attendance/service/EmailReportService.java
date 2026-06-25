package com.attendance.service;

import com.attendance.model.AttendanceRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class EmailReportService {

    @Autowired private ResendEmailClient resendEmailClient;
    @Autowired private ExcelService      excelService;

    @Value("${attendance.email.to}")
    private String recipientEmail;

    @Value("${attendance.email.from-name}")
    private String fromName;

    // ─── Scheduled Triggers ───────────────────────────────────────────────────

    /** Daily report – default 6 PM every day */
    @Scheduled(cron = "${attendance.email.daily-cron}")
    public void sendDailyReport() {
        try {
            sendDailyReportNow();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** Weekly report – default every Monday 8 AM */
    @Scheduled(cron = "${attendance.email.weekly-cron}")
    public void sendWeeklyReport() {
        try {
            sendWeeklyReportNow();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ─── Public API (callable manually from controller) ───────────────────────
    // These propagate exceptions so the controller can return the real error
    // message to the caller (e.g. an invalid Resend API key or bad sender domain).

    public void sendDailyReportNow() throws Exception {
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy"));
        List<AttendanceRecord> records = excelService.getTodayRecords(today);
        sendReport("Daily Attendance Report – " + today,
                   buildDailyHtml(today, records),
                   records,
                   "Daily_Attendance_" + today + ".xlsx");
    }

    public void sendWeeklyReportNow() throws Exception {
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.minusDays(7);
        String label = weekStart.format(DateTimeFormatter.ofPattern("dd-MM-yyyy"))
                + " to " + today.format(DateTimeFormatter.ofPattern("dd-MM-yyyy"));
        List<AttendanceRecord> all = excelService.getAllRecords();
        List<AttendanceRecord> weekRecords = all.stream()
            .filter(r -> isWithinLastNDays(r.getDate(), 7))
            .collect(Collectors.toList());
        sendReport("Weekly Attendance Report – " + label,
                   buildWeeklyHtml(label, weekRecords),
                   weekRecords,
                   "Weekly_Attendance_" + today.format(DateTimeFormatter.ofPattern("dd-MM-yyyy")) + ".xlsx");
    }

    // ─── Core send helper ─────────────────────────────────────────────────────

    private void sendReport(String subject, String htmlBody,
                            List<AttendanceRecord> records, String attachmentName)
            throws IOException {

        // Get the Excel attachment bytes
        byte[] excelBytes = excelService.getExcelBytes();

        // Send via Resend's HTTPS API (works on any host/plan, unlike SMTP)
        resendEmailClient.sendEmail(fromName, recipientEmail, subject, htmlBody,
                                     attachmentName, excelBytes);

        System.out.println("[EmailReport] Sent: " + subject + " → " + recipientEmail);
    }

    // ─── HTML Builders ────────────────────────────────────────────────────────

    private String buildDailyHtml(String date, List<AttendanceRecord> records) {
        StringBuilder sb = new StringBuilder();
        sb.append(emailHeader("Daily Attendance Report"));
        sb.append("<p style='color:#555;font-size:15px;'>Report for <strong>").append(date).append("</strong></p>");
        sb.append("<table style='width:100%;border-collapse:collapse;margin-top:12px;font-size:14px;'>");
        sb.append("<thead><tr style='background:#1a73e8;color:#fff;'>");
        for (String h : new String[]{"#","Employee ID","Name","Department","Time","Location"}) {
            sb.append("<th style='padding:10px 12px;text-align:left;'>").append(h).append("</th>");
        }
        sb.append("</tr></thead><tbody>");
        if (records.isEmpty()) {
            sb.append("<tr><td colspan='6' style='padding:14px;text-align:center;color:#888;'>No attendance records found for today.</td></tr>");
        } else {
            int i = 1;
            for (AttendanceRecord r : records) {
                String bg = (i % 2 == 0) ? "#f8f9fa" : "#ffffff";
                sb.append("<tr style='background:").append(bg).append(";'>")
                  .append(td(String.valueOf(i++)))
                  .append(td(r.getEmployeeId()))
                  .append(td(r.getEmployeeName()))
                  .append(td(r.getDepartment()))
                  .append(td(r.getTime()))
                  .append(td(locationBadge(r.getLocationStatus())))
                  .append("</tr>");
            }
        }
        sb.append("</tbody></table>");
        sb.append(summaryBox("Total Present Today", String.valueOf(records.size())));
        sb.append(emailFooter());
        return sb.toString();
    }

    private String buildWeeklyHtml(String dateRange, List<AttendanceRecord> records) {
        // Group by date
        Map<String, Long> byDate = records.stream()
            .collect(Collectors.groupingBy(AttendanceRecord::getDate, Collectors.counting()));

        // Group by department
        Map<String, Long> byDept = records.stream()
            .collect(Collectors.groupingBy(AttendanceRecord::getDepartment, Collectors.counting()));

        StringBuilder sb = new StringBuilder();
        sb.append(emailHeader("Weekly Attendance Report"));
        sb.append("<p style='color:#555;font-size:15px;'>Period: <strong>").append(dateRange).append("</strong></p>");

        // Day-wise summary
        sb.append("<h3 style='color:#1a73e8;margin-top:24px;'>Day-wise Summary</h3>");
        sb.append("<table style='width:100%;border-collapse:collapse;font-size:14px;'>");
        sb.append("<thead><tr style='background:#1a73e8;color:#fff;'><th style='padding:9px 12px;text-align:left;'>Date</th><th style='padding:9px 12px;text-align:left;'>Present</th></tr></thead><tbody>");
        byDate.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(e -> sb.append("<tr><td style='padding:9px 12px;border-bottom:1px solid #eee;'>").append(e.getKey())
                            .append("</td><td style='padding:9px 12px;border-bottom:1px solid #eee;font-weight:bold;color:#1a73e8;'>").append(e.getValue()).append("</td></tr>"));
        sb.append("</tbody></table>");

        // Dept summary
        sb.append("<h3 style='color:#1a73e8;margin-top:24px;'>Department-wise Summary</h3>");
        sb.append("<table style='width:100%;border-collapse:collapse;font-size:14px;'>");
        sb.append("<thead><tr style='background:#1a73e8;color:#fff;'><th style='padding:9px 12px;text-align:left;'>Department</th><th style='padding:9px 12px;text-align:left;'>Total Check-ins</th></tr></thead><tbody>");
        byDept.entrySet().stream()
            .sorted(Map.Entry.<String,Long>comparingByValue().reversed())
            .forEach(e -> sb.append("<tr><td style='padding:9px 12px;border-bottom:1px solid #eee;'>").append(e.getKey())
                            .append("</td><td style='padding:9px 12px;border-bottom:1px solid #eee;font-weight:bold;color:#1a73e8;'>").append(e.getValue()).append("</td></tr>"));
        sb.append("</tbody></table>");

        sb.append(summaryBox("Total Records This Week", String.valueOf(records.size())));
        sb.append(emailFooter());
        return sb.toString();
    }

    // ─── HTML Helpers ─────────────────────────────────────────────────────────

    private String emailHeader(String title) {
        return "<div style='font-family:Segoe UI,Arial,sans-serif;max-width:700px;margin:0 auto;border:1px solid #e0e0e0;border-radius:8px;overflow:hidden;'>"
             + "<div style='background:#1a73e8;padding:28px 32px;'>"
             + "<h1 style='color:#fff;margin:0;font-size:22px;'>📋 " + title + "</h1>"
             + "<p style='color:#d0e4ff;margin:6px 0 0;font-size:13px;'>QR Attendance System – Automated Report</p>"
             + "</div><div style='padding:28px 32px;'>";
    }

    private String emailFooter() {
        return "</div><div style='background:#f5f5f5;padding:16px 32px;text-align:center;font-size:12px;color:#999;'>"
             + "This is an automated email from QR Attendance System. Please do not reply."
             + "</div></div>";
    }

    private String summaryBox(String label, String value) {
        return "<div style='margin-top:24px;background:#e8f0fe;border-radius:8px;padding:16px 20px;display:inline-block;'>"
             + "<span style='font-size:13px;color:#555;'>" + label + ": </span>"
             + "<strong style='font-size:20px;color:#1a73e8;'>" + value + "</strong></div>";
    }

    private String td(String content) {
        return "<td style='padding:9px 12px;border-bottom:1px solid #eee;'>" + content + "</td>";
    }

    private String locationBadge(String locationStatus) {
        if (locationStatus == null || locationStatus.isBlank()) return "<span style='color:#999;'>—</span>";
        String color = locationStatus.contains("Within") ? "#28a745" : "#dc3545";
        return "<span style='background:" + color + ";color:#fff;padding:2px 8px;border-radius:12px;font-size:12px;'>"
             + locationStatus + "</span>";
    }

    // ─── Date Filter ──────────────────────────────────────────────────────────

    private boolean isWithinLastNDays(String dateStr, int days) {
        try {
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd-MM-yyyy");
            LocalDate recordDate = LocalDate.parse(dateStr, fmt);
            LocalDate cutoff = LocalDate.now().minusDays(days);
            return !recordDate.isBefore(cutoff);
        } catch (Exception e) {
            return false;
        }
    }
}
