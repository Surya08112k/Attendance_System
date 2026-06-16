package com.attendance.controller;

import com.attendance.model.Employee;
import com.attendance.service.EmployeeService;
import com.attendance.service.QrCodeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/employees")
@CrossOrigin(origins = "*")
public class EmployeeController {

    @Autowired private EmployeeService employeeService;
    @Autowired private QrCodeService   qrCodeService;

    /** List all employees */
    @GetMapping
    public ResponseEntity<List<Employee>> getAllEmployees() {
        return ResponseEntity.ok(employeeService.getAllEmployees());
    }

    /** Get a single employee by ID */
    @GetMapping("/{id}")
    public ResponseEntity<?> getEmployee(@PathVariable String id) {
        return employeeService.findById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    /** Generate QR code PNG for an employee */
    @GetMapping("/{id}/qrcode")
    public ResponseEntity<byte[]> getQrCode(@PathVariable String id) {
        if (!employeeService.exists(id)) {
            return ResponseEntity.notFound().build();
        }
        try {
            byte[] png = qrCodeService.generateQrCode(id);
            return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"qr_" + id + ".png\"")
                .body(png);
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }
}
