package com.attendance.service;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpEntity;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestClientException;

import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sends emails via the Resend HTTPS API (https://resend.com) instead of SMTP.
 *
 * Why: many PaaS providers (Railway included) block outbound SMTP ports
 * (25/465/587) on free/hobby plans to prevent abuse. Resend uses a plain
 * HTTPS POST on port 443, which is never blocked, so this works the same
 * locally and in any cloud deployment.
 *
 * Setup:
 *   1. Create a free account at https://resend.com
 *   2. Grab an API key from https://resend.com/api-keys
 *   3. Paste it into src/main/resources/application.properties as the
 *      fallback value of resend.api.key (replace PASTE_YOUR_RESEND_API_KEY_HERE).
 *      On Railway, you can instead set RESEND_API_KEY as an environment
 *      variable in the Variables tab — it overrides the properties file value.
 *   4. (Optional) Verify your own domain in Resend and set resend.from.email
 *      (or the RESEND_FROM_EMAIL env var) to an address on that domain.
 *      Until then, the default "onboarding@resend.dev" sender only delivers
 *      to the email address you used to sign up for Resend - fine for
 *      testing, not for production.
 */
@Service
public class ResendEmailClient {

    private static final String RESEND_API_URL = "https://api.resend.com/emails";
    private static final String PLACEHOLDER_KEY = "PASTE_YOUR_RESEND_API_KEY_HERE";

    @Value("${resend.api.key}")
    private String apiKey;

    @Value("${resend.from.email}")
    private String fromEmail;

    private final RestTemplate restTemplate = new RestTemplate();

    @PostConstruct
    private void checkApiKey() {
        if (isKeyMissing()) {
            System.out.println("[ResendEmailClient] WARNING: resend.api.key is not set. " +
                "Edit src/main/resources/application.properties (or set the RESEND_API_KEY " +
                "environment variable) before emails can be sent. " +
                "Get a key from https://resend.com/api-keys");
        }
    }

    private boolean isKeyMissing() {
        return apiKey == null || apiKey.isBlank() || PLACEHOLDER_KEY.equals(apiKey.trim());
    }

    /**
     * Sends an HTML email with a single attachment via Resend.
     *
     * @param fromName       display name shown in the "From" field
     * @param toEmail        recipient address
     * @param subject        email subject
     * @param htmlBody       HTML email body
     * @param attachmentName filename shown in the email client (e.g. "Daily_Attendance_25-06-2026.xlsx")
     * @param attachmentBytes raw bytes of the attachment
     */
    public void sendEmail(String fromName, String toEmail, String subject, String htmlBody,
                           String attachmentName, byte[] attachmentBytes) {

        if (isKeyMissing()) {
            throw new IllegalStateException(
                "resend.api.key is not configured. Open src/main/resources/application.properties " +
                "and replace PASTE_YOUR_RESEND_API_KEY_HERE with a real key from " +
                "https://resend.com/api-keys (or set the RESEND_API_KEY environment variable).");
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("from", fromName + " <" + fromEmail + ">");
        payload.put("to", List.of(toEmail));
        payload.put("subject", subject);
        payload.put("html", htmlBody);

        if (attachmentBytes != null && attachmentName != null) {
            Map<String, String> attachment = new HashMap<>();
            attachment.put("filename", attachmentName);
            attachment.put("content", Base64.getEncoder().encodeToString(attachmentBytes));
            payload.put("attachments", List.of(attachment));
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

        try {
            restTemplate.postForEntity(RESEND_API_URL, request, String.class);
        } catch (RestClientException e) {
            // Rethrow as a checked-friendly runtime exception with full context,
            // so the controller can surface the real Resend error to the caller.
            throw new RuntimeException("Resend API call failed: " + e.getMessage(), e);
        }
    }
}
