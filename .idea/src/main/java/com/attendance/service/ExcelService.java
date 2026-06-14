package com.attendance.service;

import com.attendance.model.AttendanceRecord;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

@Service
public class ExcelService {

    @Value("${attendance.excel.path}")
    private String excelFilePath;

    private static final String SHEET_NAME = "Attendance";
    private static final String[] HEADERS = {
        "Employee ID", "Employee Name", "Department", "Date", "Time", "Status",
        "Latitude", "Longitude", "Location Status"
    };

    public void initializeExcel() throws IOException {
        File file = new File(excelFilePath);
        if (!file.exists()) {
            try (Workbook workbook = new XSSFWorkbook()) {
                Sheet sheet = workbook.createSheet(SHEET_NAME);
                writeHeaderRow(workbook, sheet);
                try (FileOutputStream fos = new FileOutputStream(file)) {
                    workbook.write(fos);
                }
            }
        }
    }

    public boolean appendRecord(AttendanceRecord record) throws IOException {
        initializeExcel();

        File file = new File(excelFilePath);
        Workbook workbook;
        try (FileInputStream fis = new FileInputStream(file)) {
            workbook = new XSSFWorkbook(fis);
        }

        Sheet sheet = workbook.getSheet(SHEET_NAME);
        if (sheet == null) {
            sheet = workbook.createSheet(SHEET_NAME);
            writeHeaderRow(workbook, sheet);
        }

        // Duplicate check: same Employee ID + same Date
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;
            String existingId   = getCellValue(row, 0);
            String existingDate = getCellValue(row, 3);
            if (record.getEmployeeId().equals(existingId) && record.getDate().equals(existingDate)) {
                workbook.close();
                return false;
            }
        }

        int nextRow = sheet.getLastRowNum() + 1;
        Row row = sheet.createRow(nextRow);

        CellStyle dataStyle = createDataStyle(workbook);
        setCell(row, 0, record.getEmployeeId(), dataStyle);
        setCell(row, 1, record.getEmployeeName(), dataStyle);
        setCell(row, 2, record.getDepartment(), dataStyle);
        setCell(row, 3, record.getDate(), dataStyle);
        setCell(row, 4, record.getTime(), dataStyle);
        setCell(row, 5, record.getStatus(), dataStyle);
        setCell(row, 6, record.getLatitude()  != null ? record.getLatitude().toString()  : "", dataStyle);
        setCell(row, 7, record.getLongitude() != null ? record.getLongitude().toString() : "", dataStyle);
        setCell(row, 8, record.getLocationStatus() != null ? record.getLocationStatus() : "Unknown", dataStyle);

        for (int i = 0; i < HEADERS.length; i++) sheet.autoSizeColumn(i);

        try (FileOutputStream fos = new FileOutputStream(file)) {
            workbook.write(fos);
        }
        workbook.close();
        return true;
    }

    public List<AttendanceRecord> getAllRecords() throws IOException {
        List<AttendanceRecord> records = new ArrayList<>();
        File file = new File(excelFilePath);
        if (!file.exists()) return records;

        try (FileInputStream fis = new FileInputStream(file);
             Workbook workbook = new XSSFWorkbook(fis)) {
            Sheet sheet = workbook.getSheet(SHEET_NAME);
            if (sheet == null) return records;
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                AttendanceRecord rec = new AttendanceRecord(
                    getCellValue(row, 0), getCellValue(row, 1),
                    getCellValue(row, 2), getCellValue(row, 3),
                    getCellValue(row, 4), getCellValue(row, 5)
                );
                String latStr = getCellValue(row, 6);
                String lonStr = getCellValue(row, 7);
                if (!latStr.isEmpty()) try { rec.setLatitude(Double.parseDouble(latStr)); } catch (NumberFormatException ignored) {}
                if (!lonStr.isEmpty()) try { rec.setLongitude(Double.parseDouble(lonStr)); } catch (NumberFormatException ignored) {}
                rec.setLocationStatus(getCellValue(row, 8));
                records.add(rec);
            }
        }
        return records;
    }

    public List<AttendanceRecord> getTodayRecords(String today) throws IOException {
        List<AttendanceRecord> all = getAllRecords();
        List<AttendanceRecord> todayList = new ArrayList<>();
        for (AttendanceRecord r : all) {
            if (today.equals(r.getDate())) todayList.add(r);
        }
        return todayList;
    }

    public byte[] getExcelBytes() throws IOException {
        File file = new File(excelFilePath);
        if (!file.exists()) initializeExcel();
        try (FileInputStream fis = new FileInputStream(file)) {
            return fis.readAllBytes();
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void writeHeaderRow(Workbook workbook, Sheet sheet) {
        Row headerRow = sheet.createRow(0);
        CellStyle headerStyle = createHeaderStyle(workbook);
        for (int i = 0; i < HEADERS.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(HEADERS[i]);
            cell.setCellStyle(headerStyle);
            sheet.setColumnWidth(i, 5500);
        }
    }

    private void setCell(Row row, int col, String value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value != null ? value : "");
        cell.setCellStyle(style);
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle createDataStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private String getCellValue(Row row, int colIndex) {
        Cell cell = row.getCell(colIndex);
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING  -> cell.getStringCellValue().trim();
            case NUMERIC -> String.valueOf(cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default      -> "";
        };
    }
}
