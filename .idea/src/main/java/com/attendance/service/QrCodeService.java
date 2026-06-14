package com.attendance.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Service
public class QrCodeService {

    private static final int QR_WIDTH  = 300;
    private static final int QR_HEIGHT = 300;

    /**
     * Generate a QR code PNG for the given employeeId and return the raw bytes.
     */
    public byte[] generateQrCode(String employeeId) throws WriterException, IOException {
        Map<EncodeHintType, Object> hints = new HashMap<>();
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
        hints.put(EncodeHintType.MARGIN, 2);

        QRCodeWriter writer = new QRCodeWriter();
        BitMatrix bitMatrix = writer.encode(employeeId, BarcodeFormat.QR_CODE,
                                            QR_WIDTH, QR_HEIGHT, hints);

        BufferedImage qrImage = MatrixToImageWriter.toBufferedImage(bitMatrix);

        // Add label below QR code
        int labelHeight = 50;
        BufferedImage combined = new BufferedImage(
                QR_WIDTH, QR_HEIGHT + labelHeight, BufferedImage.TYPE_INT_RGB);

        Graphics2D g = combined.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // White background
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, QR_WIDTH, QR_HEIGHT + labelHeight);

        // Draw QR
        g.drawImage(qrImage, 0, 0, null);

        // Draw label
        g.setColor(new Color(30, 58, 138)); // dark blue
        g.setFont(new Font("Arial", Font.BOLD, 14));
        FontMetrics fm = g.getFontMetrics();
        String label = "ID: " + employeeId;
        int textX = (QR_WIDTH - fm.stringWidth(label)) / 2;
        int textY = QR_HEIGHT + 30;
        g.drawString(label, textX, textY);
        g.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(combined, "PNG", baos);
        return baos.toByteArray();
    }
}
