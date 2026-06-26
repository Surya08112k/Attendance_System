package com.attendance.controller;

import com.attendance.model.ApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@CrossOrigin(origins = "*")
public class AdminController {

    @Value("${admin.access.id}")
    private String adminAccessId;

    /**
     * Verify an admin ID entered on the dashboard login gate.
     * Does NOT issue a long-lived token — the frontend stores a short-lived
     * "verified" flag in sessionStorage for this tab only. This is a simple
     * gate suitable for hiding the dashboard from casual/non-admin users,
     * not a substitute for real authentication.
     */
    @PostMapping("/verify")
    public ResponseEntity<ApiResponse> verify(@RequestBody Map<String, String> payload) {
        String submittedId = payload.getOrDefault("id", "").trim();

        if (submittedId.isEmpty()) {
            return ResponseEntity.badRequest().body(new ApiResponse(false, "Please enter an admin ID."));
        }

        if (adminAccessId != null && adminAccessId.equals(submittedId)) {
            return ResponseEntity.ok(new ApiResponse(true, "Access granted."));
        }
        return ResponseEntity.status(401).body(new ApiResponse(false, "Invalid admin ID."));
    }
}
