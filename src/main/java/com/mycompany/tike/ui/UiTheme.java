package com.mycompany.tike.ui;

import java.awt.Color;
import java.awt.Font;
import javax.swing.UIManager;

public final class UiTheme {
    public static final Color PRIMARY = new Color(0x6C3CE9);
    public static final Color PRIMARY_DARK = new Color(0x4C28A8);
    public static final Color PRIMARY_LIGHT = new Color(0xF1ECFF);
    public static final Color BACKGROUND = new Color(0xF8F7FC);
    public static final Color SURFACE = Color.WHITE;
    public static final Color TEXT = new Color(0x242033);
    public static final Color MUTED = new Color(0x706B80);
    public static final Color BORDER = new Color(0xE5E1EF);
    public static final Color SUCCESS = new Color(0x2EAD69);
    public static final Color ERROR = new Color(0xD64545);
    public static final Font FONT = new Font("Segoe UI", Font.PLAIN, 14);
    public static final Font FONT_BOLD = new Font("Segoe UI", Font.BOLD, 14);

    private UiTheme() {
    }

    public static void apply() {
        UIManager.put("control", BACKGROUND);
        UIManager.put("Panel.background", BACKGROUND);
        UIManager.put("OptionPane.background", SURFACE);
        UIManager.put("OptionPane.messageFont", FONT);
        UIManager.put("Button.font", FONT_BOLD);
        UIManager.put("Label.font", FONT);
        UIManager.put("TextField.font", FONT);
        UIManager.put("PasswordField.font", FONT);
        UIManager.put("Table.font", FONT);
        UIManager.put("TableHeader.font", FONT_BOLD);
        UIManager.put("ScrollPane.border", null);
        UIManager.put("Component.focusColor", PRIMARY);
    }
}
