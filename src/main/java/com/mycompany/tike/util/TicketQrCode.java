package com.mycompany.tike.util;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.qrcode.QRCodeWriter;
import java.awt.image.BufferedImage;

public final class TicketQrCode {
    private static final int SIZE = 280;

    private TicketQrCode() {
    }

    public static BufferedImage create(String uniqueCode) throws WriterException {
        if (uniqueCode == null || uniqueCode.isBlank()) {
            throw new IllegalArgumentException("Le code unique du billet est obligatoire.");
        }
        return MatrixToImageWriter.toBufferedImage(
                new QRCodeWriter().encode(uniqueCode, BarcodeFormat.QR_CODE, SIZE, SIZE));
    }
}
