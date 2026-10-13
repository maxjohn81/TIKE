package com.mycompany.tike.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mycompany.tike.util.TicketQrCode;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CameraQrScannerDialogTest {
    @Test
    void decodesTheTicketQrPayload() throws Exception {
        String ticketCode = UUID.randomUUID().toString();

        assertEquals(Optional.of(ticketCode), CameraQrScannerDialog.decodeQrCode(TicketQrCode.create(ticketCode)));
    }

    @Test
    void ignoresFramesWithoutAReadableQrCode() {
        assertTrue(CameraQrScannerDialog.decodeQrCode(new java.awt.image.BufferedImage(
                120, 80, java.awt.image.BufferedImage.TYPE_INT_RGB)).isEmpty());
        assertTrue(CameraQrScannerDialog.decodeQrCode(null).isEmpty());
    }
}
