package com.mycompany.tike.ui;

import com.github.sarxos.webcam.Webcam;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Image;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.DefaultListCellRenderer;
import java.awt.Component;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

final class CameraQrScannerDialog extends JDialog {
    private final JComboBox<Webcam> cameras = new JComboBox<>();
    private final JLabel preview = new JLabel("Sélectionnez une caméra puis démarrez le scan.",
            SwingConstants.CENTER);
    private final JLabel status = new JLabel("Recherche des caméras disponibles…", SwingConstants.CENTER);
    private final JButton startButton = UiComponents.primaryButton("Démarrer le scan");
    private final JButton closeButton = new JButton("Fermer");
    private final Consumer<String> onCodeScanned;
    private SwingWorker<String, BufferedImage> scanWorker;

    CameraQrScannerDialog(Window owner, Consumer<String> onCodeScanned) {
        super(owner, "Scanner un billet QR", ModalityType.APPLICATION_MODAL);
        this.onCodeScanned = onCodeScanned;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(12, 12));
        add(buildContent(), BorderLayout.CENTER);
        add(buildActions(), BorderLayout.SOUTH);
        setMinimumSize(new Dimension(600, 520));
        setSize(720, 610);
        setLocationRelativeTo(owner);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                stopScan();
            }

            @Override
            public void windowClosed(WindowEvent event) {
                stopScan();
            }
        });
        loadCameras();
    }

    private JPanel buildContent() {
        JPanel content = new JPanel(new BorderLayout(0, 12));
        content.setBorder(BorderFactory.createEmptyBorder(16, 16, 8, 16));
        JPanel cameraRow = new JPanel(new BorderLayout(8, 0));
        cameraRow.add(new JLabel("Caméra"), BorderLayout.WEST);
        cameras.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(
                    javax.swing.JList<?> list, Object value, int index,
                    boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                setText(value instanceof Webcam webcam ? webcam.getName() : "Aucune caméra");
                return this;
            }
        });
        cameraRow.add(cameras, BorderLayout.CENTER);
        preview.setOpaque(true);
        preview.setBackground(Color.BLACK);
        preview.setForeground(Color.WHITE);
        preview.setBorder(BorderFactory.createLineBorder(UiTheme.BORDER));
        content.add(cameraRow, BorderLayout.NORTH);
        content.add(preview, BorderLayout.CENTER);
        content.add(status, BorderLayout.SOUTH);
        return content;
    }

    private JPanel buildActions() {
        JPanel actions = new JPanel();
        startButton.addActionListener(event -> startScan());
        closeButton.addActionListener(event -> {
            stopScan();
            dispose();
        });
        actions.add(startButton);
        actions.add(closeButton);
        return actions;
    }

    private void loadCameras() {
        try {
            List<Webcam> available = Webcam.getWebcams();
            for (Webcam webcam : available) {
                cameras.addItem(webcam);
            }
            if (available.isEmpty()) {
                status.setText("Aucune caméra détectée. Vérifiez la caméra Windows ou le périphérique Iriun.");
                startButton.setEnabled(false);
                return;
            }
            status.setText(available.size() + " caméra(s) détectée(s). Cadrez le QR Code du billet.");
        } catch (RuntimeException error) {
            status.setText("Impossible de détecter les caméras : " + error.getMessage());
            startButton.setEnabled(false);
        }
    }

    private void startScan() {
        Webcam webcam = (Webcam) cameras.getSelectedItem();
        if (webcam == null) {
            status.setText("Aucune caméra n'est sélectionnée.");
            return;
        }
        startButton.setEnabled(false);
        cameras.setEnabled(false);
        status.setText("Ouverture de " + webcam.getName() + "…");
        preview.setIcon(null);
        preview.setText("Recherche du code QR…");
        scanWorker = new SwingWorker<>() {
            @Override
            protected String doInBackground() throws Exception {
                MultiFormatReader reader = new MultiFormatReader();
                reader.setHints(Map.of(
                        DecodeHintType.POSSIBLE_FORMATS, List.of(BarcodeFormat.QR_CODE),
                        DecodeHintType.TRY_HARDER, Boolean.TRUE));
                try {
                    Dimension preferred = findPreferredSize(webcam);
                    if (preferred != null) {
                        webcam.setViewSize(preferred);
                    }
                    webcam.open();
                    while (!isCancelled()) {
                        BufferedImage frame = webcam.getImage();
                        if (frame != null) {
                            publish(frame);
                            Optional<String> decoded = decodeQrCode(frame, reader);
                            if (decoded.isPresent()) {
                                return decoded.get();
                            }
                        }
                        Thread.sleep(80);
                    }
                    return null;
                } finally {
                    if (webcam.isOpen()) {
                        webcam.close();
                    }
                }
            }

            @Override
            protected void process(List<BufferedImage> frames) {
                if (frames.isEmpty() || isCancelled()) {
                    return;
                }
                BufferedImage frame = frames.get(frames.size() - 1);
                Image scaled = frame.getScaledInstance(
                        Math.max(1, preview.getWidth()),
                        Math.max(1, preview.getHeight()),
                        Image.SCALE_FAST);
                preview.setText("");
                preview.setIcon(new javax.swing.ImageIcon(scaled));
                status.setText("Caméra active — placez le QR Code dans le cadre.");
            }

            @Override
            protected void done() {
                scanWorker = null;
                cameras.setEnabled(true);
                startButton.setEnabled(cameras.getItemCount() > 0);
                try {
                    String code = get();
                    if (code != null) {
                        dispose();
                        onCodeScanned.accept(code);
                    } else if (!isCancelled()) {
                        status.setText("Scan terminé sans code.");
                    }
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    status.setText("Le scan a été interrompu.");
                } catch (ExecutionException error) {
                    Throwable cause = error.getCause();
                    String message = cause == null ? null : cause.getMessage();
                    status.setText("Erreur caméra : "
                            + (message == null || message.isBlank()
                                    ? cause == null ? error.getMessage() : cause.toString()
                                    : message));
                } catch (CancellationException cancelled) {
                    status.setText("Scan arrêté.");
                }
            }
        };
        scanWorker.execute();
    }

    private static Dimension findPreferredSize(Webcam webcam) {
        Dimension[] sizes = webcam.getViewSizes();
        for (Dimension size : sizes) {
            if (size.width == 640 && size.height == 480) {
                return size;
            }
        }
        return sizes.length == 0 ? null : sizes[sizes.length - 1];
    }

    static Optional<String> decodeQrCode(BufferedImage image) {
        return decodeQrCode(image, new MultiFormatReader());
    }

    private static Optional<String> decodeQrCode(BufferedImage image, MultiFormatReader reader) {
        if (image == null) {
            return Optional.empty();
        }
        try {
            BinaryBitmap bitmap = new BinaryBitmap(
                    new HybridBinarizer(new BufferedImageLuminanceSource(image)));
            return Optional.of(reader.decodeWithState(bitmap).getText());
        } catch (NotFoundException noQrCode) {
            return Optional.empty();
        } finally {
            reader.reset();
        }
    }

    private void stopScan() {
        if (scanWorker != null) {
            scanWorker.cancel(true);
            scanWorker = null;
        }
    }
}
