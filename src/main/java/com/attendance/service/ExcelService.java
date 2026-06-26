package com.attendance.service;

import com.attendance.model.AttendanceRecord;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class ExcelService {

    @Value("${attendance.excel.path}")
    private String excelFilePath;

    private static final String SHEET_NAME = "Attendance";

    // Column indices  (order matters – Excel reads by position)
    private static final int COL_EMP_ID    = 0;
    private static final int COL_NAME      = 1;
    private static final int COL_DEPT      = 2;
    private static final int COL_DATE      = 3;
    private static final int COL_IN_TIME   = 4;
    private static final int COL_OUT_TIME  = 5;
    private static final int COL_TOTAL_HRS = 6;
    private static final int COL_STATUS    = 7;
    private static final int COL_REASON    = 8;
    private static final int COL_LAT       = 9;
    private static final int COL_LON       = 10;
    private static final int COL_LOC_STATUS= 11;

    private static final String[] HEADERS = {
        "Employee ID", "Employee Name", "Department", "Date",
        "In Time", "Out Time", "Total Hours",
        "Status", "Leave Reason",
        "Latitude", "Longitude", "Location Status"
    };

    // ── Initialise ────────────────────────────────────────────────────────────

    public void initializeExcel() throws IOException {
        File file = new File(excelFilePath);
        if (!file.exists()) {
            try (Workbook wb = new XSSFWorkbook()) {
                writeHeaderRow(wb, wb.createSheet(SHEET_NAME));
                try (FileOutputStream fos = new FileOutputStream(file)) { wb.write(fos); }
            }
        }
    }

    // ── Append (IN scan) ─────────────────────────────────────────────────────

    public boolean appendRecord(AttendanceRecord rec) throws IOException {
        initializeExcel();
        File file = new File(excelFilePath);
        Workbook wb;
        try (FileInputStream fis = new FileInputStream(file)) { wb = new XSSFWorkbook(fis); }

        Sheet sheet = wb.getSheet(SHEET_NAME);
        if (sheet == null) { sheet = wb.createSheet(SHEET_NAME); writeHeaderRow(wb, sheet); }

        // Duplicate check: same employee + same date → already clocked IN
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;
            if (rec.getEmployeeId().equals(getCellValue(row, COL_EMP_ID))
                    && rec.getDate().equals(getCellValue(row, COL_DATE))) {
                wb.close();
                return false;
            }
        }

        Row row = sheet.createRow(sheet.getLastRowNum() + 1);
        CellStyle ds = createDataStyle(wb);
        writeRow(row, rec, ds);
        autosize(sheet);
        try (FileOutputStream fos = new FileOutputStream(file)) { wb.write(fos); }
        wb.close();
        return true;
    }

    // ── Out-time stamp ────────────────────────────────────────────────────────

    public synchronized String markOutTime(String employeeId, String date, String outTime) throws IOException {
        File file = new File(excelFilePath);
        if (!file.exists()) return null;

        Workbook wb;
        try (FileInputStream fis = new FileInputStream(file)) { wb = new XSSFWorkbook(fis); }
        Sheet sheet = wb.getSheet(SHEET_NAME);
        if (sheet == null) { wb.close(); return null; }

        String totalHours = null;
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;
            if (!employeeId.equals(getCellValue(row, COL_EMP_ID))) continue;
            if (!date.equals(getCellValue(row, COL_DATE))) continue;

            CellStyle ds = createDataStyle(wb);
            setCell(row, COL_OUT_TIME, outTime, ds);

            String inTime = getCellValue(row, COL_IN_TIME);
            totalHours = calcTotalHours(inTime, outTime);
            setCell(row, COL_TOTAL_HRS, totalHours, ds);
            break;
        }

        if (totalHours != null) {
            try (FileOutputStream fos = new FileOutputStream(file)) { wb.write(fos); }
        }
        wb.close();
        return totalHours;
    }

    // ── Update (admin edit) ───────────────────────────────────────────────────

    public synchronized boolean updateRecord(String employeeId, String originalDate,
                                             AttendanceRecord updated) throws IOException {
        File file = new File(excelFilePath);
        if (!file.exists()) return false;
        Workbook wb;
        try (FileInputStream fis = new FileInputStream(file)) { wb = new XSSFWorkbook(fis); }
        Sheet sheet = wb.getSheet(SHEET_NAME);
        if (sheet == null) { wb.close(); return false; }

        boolean found = false;
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;
            if (employeeId.equals(getCellValue(row, COL_EMP_ID))
                    && originalDate.equals(getCellValue(row, COL_DATE))) {
                CellStyle ds = createDataStyle(wb);
                writeRow(row, updated, ds);
                found = true;
                break;
            }
        }
        if (found) { try (FileOutputStream fos = new FileOutputStream(file)) { wb.write(fos); } }
        wb.close();
        return found;
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    public synchronized boolean deleteRecord(String employeeId, String date) throws IOException {
        File file = new File(excelFilePath);
        if (!file.exists()) return false;
        Workbook wb;
        try (FileInputStream fis = new FileInputStream(file)) { wb = new XSSFWorkbook(fis); }
        Sheet sheet = wb.getSheet(SHEET_NAME);
        if (sheet == null) { wb.close(); return false; }

        int rowToDelete = -1;
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;
            if (employeeId.equals(getCellValue(row, COL_EMP_ID))
                    && date.equals(getCellValue(row, COL_DATE))) {
                rowToDelete = i; break;
            }
        }
        if (rowToDelete == -1) { wb.close(); return false; }

        int lastRowNum = sheet.getLastRowNum();
        if (rowToDelete < lastRowNum) sheet.shiftRows(rowToDelete + 1, lastRowNum, -1);
        else { Row last = sheet.getRow(rowToDelete); if (last != null) sheet.removeRow(last); }

        try (FileOutputStream fos = new FileOutputStream(file)) { wb.write(fos); }
        wb.close();
        return true;
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    public List<AttendanceRecord> getAllRecords() throws IOException {
        List<AttendanceRecord> list = new ArrayList<>();
        File file = new File(excelFilePath);
        if (!file.exists()) return list;
        try (FileInputStream fis = new FileInputStream(file); Workbook wb = new XSSFWorkbook(fis)) {
            Sheet sheet = wb.getSheet(SHEET_NAME);
            if (sheet == null) return list;
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                list.add(rowToRecord(row));
            }
        }
        return list;
    }

    public List<AttendanceRecord> getTodayRecords(String today) throws IOException {
        List<AttendanceRecord> result = new ArrayList<>();
        for (AttendanceRecord r : getAllRecords()) {
            if (today.equals(r.getDate())) result.add(r);
        }
        return result;
    }

    public byte[] getExcelBytes() throws IOException {
        File file = new File(excelFilePath);
        if (!file.exists()) initializeExcel();
        try (FileInputStream fis = new FileInputStream(file)) { return fis.readAllBytes(); }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void writeRow(Row row, AttendanceRecord rec, CellStyle ds) {
        setCell(row, COL_EMP_ID,     rec.getEmployeeId(), ds);
        setCell(row, COL_NAME,       rec.getEmployeeName(), ds);
        setCell(row, COL_DEPT,       rec.getDepartment(), ds);
        setCell(row, COL_DATE,       rec.getDate(), ds);
        setCell(row, COL_IN_TIME,    rec.getInTime(), ds);
        setCell(row, COL_OUT_TIME,   rec.getOutTime() != null ? rec.getOutTime() : "", ds);
        setCell(row, COL_TOTAL_HRS,  rec.getTotalHours() != null ? rec.getTotalHours() : "", ds);
        setCell(row, COL_STATUS,     rec.getStatus(), ds);
        setCell(row, COL_REASON,     rec.getLeaveReason() != null ? rec.getLeaveReason() : "", ds);
        setCell(row, COL_LAT,        rec.getLatitude()  != null ? rec.getLatitude().toString()  : "", ds);
        setCell(row, COL_LON,        rec.getLongitude() != null ? rec.getLongitude().toString() : "", ds);
        setCell(row, COL_LOC_STATUS, rec.getLocationStatus() != null ? rec.getLocationStatus() : "Unknown", ds);
    }

    private AttendanceRecord rowToRecord(Row row) {
        AttendanceRecord r = new AttendanceRecord();
        r.setEmployeeId(getCellValue(row, COL_EMP_ID));
        r.setEmployeeName(getCellValue(row, COL_NAME));
        r.setDepartment(getCellValue(row, COL_DEPT));
        r.setDate(getCellValue(row, COL_DATE));
        r.setInTime(getCellValue(row, COL_IN_TIME));
        r.setOutTime(getCellValue(row, COL_OUT_TIME));
        r.setTotalHours(getCellValue(row, COL_TOTAL_HRS));
        r.setStatus(getCellValue(row, COL_STATUS));
        r.setLeaveReason(getCellValue(row, COL_REASON));
        String latStr = getCellValue(row, COL_LAT);
        String lonStr = getCellValue(row, COL_LON);
        if (!latStr.isEmpty()) try { r.setLatitude(Double.parseDouble(latStr)); } catch (NumberFormatException ignored) {}
        if (!lonStr.isEmpty()) try { r.setLongitude(Double.parseDouble(lonStr)); } catch (NumberFormatException ignored) {}
        r.setLocationStatus(getCellValue(row, COL_LOC_STATUS));
        return r;
    }

    private String calcTotalHours(String inTime, String outTime) {
        try {
            LocalTime in  = LocalTime.parse(inTime.length() == 5 ? inTime + ":00" : inTime);
            LocalTime out = LocalTime.parse(outTime.length() == 5 ? outTime + ":00" : outTime);
            Duration dur  = Duration.between(in, out);
            if (dur.isNegative()) dur = dur.plusHours(24); // overnight edge-case
            long h = dur.toHours();
            long m = dur.toMinutesPart();
            return h + "h " + m + "m";
        } catch (Exception e) {
            return "";
        }
    }

    private void writeHeaderRow(Workbook wb, Sheet sheet) {
        Row headerRow = sheet.createRow(0);
        CellStyle hs = createHeaderStyle(wb);
        for (int i = 0; i < HEADERS.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(HEADERS[i]);
            cell.setCellStyle(hs);
            sheet.setColumnWidth(i, 5500);
        }
    }

    private void autosize(Sheet sheet) {
        for (int i = 0; i < HEADERS.length; i++) sheet.autoSizeColumn(i);
    }

    private void setCell(Row row, int col, String value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value != null ? value : "");
        cell.setCellStyle(style);
    }

    private CellStyle createHeaderStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont(); f.setBold(true); f.setColor(IndexedColors.WHITE.getIndex());
        s.setFont(f);
        s.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setAlignment(HorizontalAlignment.CENTER);
        s.setBorderBottom(BorderStyle.THIN); s.setBorderTop(BorderStyle.THIN);
        s.setBorderLeft(BorderStyle.THIN);   s.setBorderRight(BorderStyle.THIN);
        return s;
    }

    private CellStyle createDataStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        s.setBorderBottom(BorderStyle.THIN); s.setBorderTop(BorderStyle.THIN);
        s.setBorderLeft(BorderStyle.THIN);   s.setBorderRight(BorderStyle.THIN);
        s.setAlignment(HorizontalAlignment.CENTER);
        return s;
    }

    private String getCellValue(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING  -> cell.getStringCellValue().trim();
            case NUMERIC -> String.valueOf(cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> "";
        };
    }
}
