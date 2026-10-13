package com.mycompany.tike.ui;

import com.mycompany.tike.model.UserSession;
import com.mycompany.tike.model.UserRole;
import com.mycompany.tike.dao.CatalogDao;
import com.mycompany.tike.dao.ReservationDao;
import com.mycompany.tike.dao.SalesDao;
import com.mycompany.tike.dao.AccessControlDao;
import com.mycompany.tike.dao.RefundDao;
import com.mycompany.tike.dao.ReportingDao;
import com.mycompany.tike.service.AuthenticationService;
import com.mycompany.tike.service.AccessControlService;
import com.mycompany.tike.service.CatalogService;
import com.mycompany.tike.service.ReservationService;
import com.mycompany.tike.service.RefundService;
import com.mycompany.tike.service.ReportingService;
import com.mycompany.tike.service.SalesService;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.EnumMap;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;
import javax.swing.DefaultListModel;
import javax.swing.JList;

public final class MainWindow extends JFrame {
    private static final String LOGO_RESOURCE = "/images/logo.png";

    private final UserSession session;
    private final AuthenticationService authenticationService;
    private final Runnable onLogout;
    private final ReportingService reportingService = new ReportingService(new ReportingDao());
    private final JPanel pageContainer = new JPanel(new CardLayout());
    private final JLabel pageTitle = new JLabel();
    private final Map<NavigationItem, JButton> navigationButtons = new EnumMap<>(NavigationItem.class);
    private NavigationItem selectedItem = NavigationItem.HOME;
    private final JLabel dashboardEventValue = new JLabel("—");
    private final JLabel dashboardTicketValue = new JLabel("—");
    private final JLabel dashboardRevenueValue = new JLabel("—");
    private final JLabel dashboardReservationValue = new JLabel("—");
    private final JLabel dashboardStatus = new JLabel("Chargement des indicateurs…");
    private final DefaultListModel<String> recentActivityModel = new DefaultListModel<>();
    private final DashboardCharts.BarChart monthlyRevenueChart = new DashboardCharts.BarChart();
    private final DashboardCharts.PieChart eventDistributionChart = new DashboardCharts.PieChart();

    public MainWindow(UserSession session, AuthenticationService authenticationService, Runnable onLogout) {
        super("TIKE | Billetterie & contrôle d'accès");
        this.session = session;
        this.authenticationService = authenticationService;
        this.onLogout = onLogout;
        configureWindow();
        setContentPane(buildLayout());
        showPage(NavigationItem.HOME);
    }

    private void configureWindow() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        Rectangle usableBounds = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        setMinimumSize(new Dimension(
                Math.min(1024, usableBounds.width),
                Math.min(680, usableBounds.height)));
        setSize(Math.min(1280, usableBounds.width), Math.min(800, usableBounds.height));
        setLocation(
                usableBounds.x + (usableBounds.width - getWidth()) / 2,
                usableBounds.y + (usableBounds.height - getHeight()) / 2);
    }

    private JPanel buildLayout() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(UiTheme.BACKGROUND);
        root.add(buildSidebar(), BorderLayout.WEST);
        root.add(buildWorkspace(), BorderLayout.CENTER);
        return root;
    }

    private JPanel buildSidebar() {
        JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.setPreferredSize(new Dimension(245, 0));
        sidebar.setBackground(UiTheme.SURFACE);
        sidebar.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, UiTheme.BORDER));
        sidebar.add(buildBrand(), BorderLayout.NORTH);

        JPanel menu = new JPanel(new GridLayout(0, 1, 0, 5));
        menu.setBackground(UiTheme.SURFACE);
        menu.setBorder(new EmptyBorder(14, 12, 14, 12));
        for (NavigationItem item : NavigationItem.values()) {
            if (item.isAvailableTo(session.getRole())) {
                JButton button = createNavigationButton(item);
                navigationButtons.put(item, button);
                menu.add(button);
            }
        }
        sidebar.add(menu, BorderLayout.CENTER);
        sidebar.add(buildProfile(), BorderLayout.SOUTH);
        return sidebar;
    }

    private JPanel buildBrand() {
        JPanel brand = new JPanel(new BorderLayout(10, 0));
        brand.setBackground(UiTheme.SURFACE);
        brand.setBorder(new EmptyBorder(20, 18, 18, 16));
        java.net.URL logoUrl = MainWindow.class.getResource(LOGO_RESOURCE);
        if (logoUrl != null) {
            ImageIcon source = new ImageIcon(logoUrl);
            java.awt.Image scaled = source.getImage().getScaledInstance(48, 48, java.awt.Image.SCALE_SMOOTH);
            brand.add(new JLabel(new ImageIcon(scaled)), BorderLayout.WEST);
        }
        JPanel brandText = new JPanel(new GridLayout(0, 1, 0, 2));
        brandText.setOpaque(false);
        JLabel name = new JLabel("TIKE");
        name.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 23));
        name.setForeground(UiTheme.PRIMARY_DARK);
        JLabel slogan = new JLabel("Billetterie & accès");
        slogan.setFont(new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 11));
        slogan.setForeground(UiTheme.MUTED);
        brandText.add(name);
        brandText.add(slogan);
        brand.add(brandText, BorderLayout.CENTER);
        return brand;
    }

    private JPanel buildProfile() {
        JPanel profile = new JPanel(new BorderLayout(4, 6));
        profile.setBackground(UiTheme.SURFACE);
        profile.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, UiTheme.BORDER),
                new EmptyBorder(14, 18, 16, 18)));
        JLabel name = new JLabel(session.getDisplayName());
        name.setFont(UiTheme.FONT_BOLD);
        name.setForeground(UiTheme.TEXT);
        JLabel role = new JLabel(session.getUsername() + " · " + session.getRole().getLabel());
        role.setFont(new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 12));
        role.setForeground(UiTheme.MUTED);
        profile.add(name, BorderLayout.NORTH);
        profile.add(role, BorderLayout.CENTER);
        JButton signOut = new JButton("Se déconnecter");
        signOut.setHorizontalAlignment(SwingConstants.LEFT);
        signOut.setForeground(UiTheme.PRIMARY_DARK);
        signOut.setContentAreaFilled(false);
        signOut.setBorder(BorderFactory.createEmptyBorder(7, 0, 0, 0));
        signOut.addActionListener(event -> {
            Object[] options = {"Rester connecté", "Se déconnecter"};
            int choice = javax.swing.JOptionPane.showOptionDialog(
                    this,
                    "Voulez-vous vraiment vous déconnecter de TIKE ?",
                    "Confirmer la déconnexion",
                    javax.swing.JOptionPane.YES_NO_OPTION,
                    javax.swing.JOptionPane.QUESTION_MESSAGE,
                    null,
                    options,
                    options[0]);
            if (choice == 1) {
                dispose();
                onLogout.run();
            }
        });
        profile.add(signOut, BorderLayout.SOUTH);
        return profile;
    }

    private JButton createNavigationButton(NavigationItem item) {
        JButton button = new JButton("   " + item.getLabel());
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setFont(UiTheme.FONT);
        button.setForeground(UiTheme.TEXT);
        button.setBackground(UiTheme.SURFACE);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createEmptyBorder(11, 8, 11, 8));
        button.addActionListener(event -> showPage(item));
        return button;
    }

    private JPanel buildWorkspace() {
        JPanel workspace = new JPanel(new BorderLayout(0, 18));
        workspace.setBackground(UiTheme.BACKGROUND);
        workspace.setBorder(new EmptyBorder(25, 30, 28, 30));

        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setOpaque(false);
        pageTitle.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 24));
        pageTitle.setForeground(UiTheme.TEXT);
        topBar.add(pageTitle, BorderLayout.WEST);
        pageContainer.setOpaque(false);
        pageContainer.add(buildDashboard(), NavigationItem.HOME.name());
        CatalogService catalogService = new CatalogService(new CatalogDao());
        SalesService salesService = new SalesService(new SalesDao());
        ReservationService reservationService = new ReservationService(new ReservationDao());
        AccessControlService accessControlService = new AccessControlService(new AccessControlDao());
        RefundService refundService = new RefundService(new RefundDao());
        for (NavigationItem item : NavigationItem.values()) {
            if (item != NavigationItem.HOME) {
                JPanel page;
                if (item == NavigationItem.USERS && session.getRole() == UserRole.ADMIN) {
                    page = new UserManagementPanel(session, authenticationService);
                } else if (item == NavigationItem.VENUES && session.getRole() == UserRole.ADMIN) {
                    page = new VenuesManagementPanel(session, catalogService);
                } else if (item == NavigationItem.EVENTS
                        && (session.getRole() == UserRole.ADMIN || session.getRole() == UserRole.AGENT)) {
                    page = new EventManagementPanel(session, catalogService);
                } else if (item == NavigationItem.CLIENTS
                        && (session.getRole() == UserRole.ADMIN || session.getRole() == UserRole.AGENT)) {
                    page = new ClientManagementPanel(session, salesService);
                } else if (item == NavigationItem.SALES
                        && (session.getRole() == UserRole.ADMIN || session.getRole() == UserRole.AGENT)) {
                    page = new SalesPanel(session, catalogService, salesService, refundService);
                } else if (item == NavigationItem.RESERVATIONS
                        && (session.getRole() == UserRole.ADMIN || session.getRole() == UserRole.AGENT)) {
                    page = new ReservationPanel(session, catalogService, salesService, reservationService);
                } else if (item == NavigationItem.ACCESS_CONTROL
                        && (session.getRole() == UserRole.ADMIN || session.getRole() == UserRole.CONTROLEUR)) {
                    page = new AccessControlPanel(session, accessControlService);
                } else if (item == NavigationItem.REPORTS && session.getRole() == UserRole.ADMIN) {
                    page = new ReportsPanel(session, reportingService);
                } else if (item == NavigationItem.HISTORY && session.getRole() == UserRole.ADMIN) {
                    page = new ActivityHistoryPanel(session, reportingService);
                } else {
                    page = buildPlaceholder(item);
                }
                pageContainer.add(page, item.name());
            }
        }
        workspace.add(topBar, BorderLayout.NORTH);
        workspace.add(pageContainer, BorderLayout.CENTER);
        return workspace;
    }

    private JPanel buildDashboard() {
        JPanel page = new JPanel(new BorderLayout(0, 20));
        page.setOpaque(false);

        JPanel intro = new JPanel(new BorderLayout(0, 7));
        intro.setOpaque(false);
        JLabel heading = new JLabel("Bienvenue sur TIKE");
        heading.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 28));
        heading.setForeground(UiTheme.TEXT);
        JLabel subtitle = new JLabel("Indicateurs opérationnels calculés depuis les données TIKE.");
        subtitle.setForeground(UiTheme.MUTED);
        intro.add(heading, BorderLayout.NORTH);
        intro.add(subtitle, BorderLayout.CENTER);

        JPanel content = new JPanel();
        content.setLayout(new javax.swing.BoxLayout(content, javax.swing.BoxLayout.Y_AXIS));
        content.setOpaque(false);
        JPanel infoCard = new JPanel(new BorderLayout(0, 10));
        infoCard.setBackground(UiTheme.SURFACE);
        infoCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UiTheme.BORDER),
                new EmptyBorder(22, 24, 22, 24)));
        JLabel cardTitle = new JLabel("Votre espace de travail");
        cardTitle.setFont(UiTheme.FONT_BOLD);
        cardTitle.setForeground(UiTheme.TEXT);
        JLabel message = new JLabel("<html>Les ventes remboursées sont exclues des revenus et des billets vendus. "
                + "Les réservations affichées sont celles encore en attente et non expirées.</html>");
        message.setForeground(UiTheme.MUTED);
        infoCard.add(cardTitle, BorderLayout.NORTH);
        infoCard.add(message, BorderLayout.CENTER);

        JPanel cards = new JPanel(new GridLayout(1, 4, 14, 0));
        cards.setOpaque(false);
        cards.add(UiComponents.card(new JLabel("Événements actifs"), dashboardEventValue, new JLabel("Hors annulés et terminés")));
        cards.add(UiComponents.card(new JLabel("Billets vendus"), dashboardTicketValue, new JLabel("Ventes payées")));
        cards.add(UiComponents.card(new JLabel("Revenus encaissés"), dashboardRevenueValue, new JLabel("Ventes payées, remboursements exclus")));
        cards.add(UiComponents.card(new JLabel("Réservations en attente"), dashboardReservationValue, new JLabel("Non expirées")));

        JPanel recent = new JPanel(new BorderLayout(0, 10));
        recent.setBackground(UiTheme.SURFACE);
        recent.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UiTheme.BORDER),
                new EmptyBorder(18, 20, 18, 20)));
        JLabel recentTitle = new JLabel("Activité récente");
        recentTitle.setFont(UiTheme.FONT_BOLD);
        recentTitle.setForeground(UiTheme.TEXT);
        JList<String> recentList = new JList<>(recentActivityModel);
        recentList.setVisibleRowCount(4);
        recentList.setFont(UiTheme.FONT);
        recentList.setBackground(UiTheme.SURFACE);
        JLabel activityUnavailable = new JLabel(
                session.getRole() == UserRole.ADMIN
                        ? "Chargement du journal d'activité…" : "Le journal détaillé est réservé aux administrateurs.",
                SwingConstants.CENTER);
        activityUnavailable.setForeground(UiTheme.MUTED);
        recent.add(recentTitle, BorderLayout.NORTH);
        if (session.getRole() == UserRole.ADMIN) {
            recent.add(new JScrollPane(recentList), BorderLayout.CENTER);
        } else {
            recent.add(activityUnavailable, BorderLayout.CENTER);
        }
        dashboardStatus.setForeground(UiTheme.MUTED);
        recent.add(dashboardStatus, BorderLayout.SOUTH);
        JPanel charts = new JPanel(new GridLayout(1, 2, 14, 0));
        charts.setOpaque(false);
        charts.add(DashboardCharts.card("Revenus encaissés · 6 derniers mois (Ar)", monthlyRevenueChart));
        charts.add(DashboardCharts.card("Répartition des billets vendus par événement", eventDistributionChart));
        charts.setPreferredSize(new Dimension(900, 310));
        charts.setMinimumSize(new Dimension(0, 310));
        charts.setMaximumSize(new Dimension(Integer.MAX_VALUE, 310));

        for (JPanel section : List.of(cards, infoCard, charts, recent)) {
            section.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
            section.setMaximumSize(new Dimension(Integer.MAX_VALUE, section.getPreferredSize().height));
            content.add(section);
            content.add(javax.swing.Box.createVerticalStrut(16));
        }
        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        page.add(intro, BorderLayout.NORTH);
        page.add(scroll, BorderLayout.CENTER);
        return page;
    }

    private void refreshDashboard() {
        new SwingWorker<DashboardData, Void>() {
            @Override
            protected DashboardData doInBackground() throws Exception {
                ReportingDao.DashboardMetrics metrics = reportingService.dashboard(session);
                List<ReportingDao.ChartValue> monthlyRevenue = reportingService.monthlyRevenue(session);
                List<ReportingDao.ChartValue> ticketDistribution = reportingService.ticketDistribution(session);
                List<ReportingDao.ActivityEntry> activity = session.getRole() == UserRole.ADMIN
                        ? reportingService.activity(session, null, null, "").stream().limit(5).toList()
                        : List.of();
                return new DashboardData(metrics, monthlyRevenue, ticketDistribution, activity);
            }

            @Override
            protected void done() {
                try {
                    DashboardData data = get();
                    dashboardEventValue.setText(Long.toString(data.metrics().activeEvents()));
                    dashboardTicketValue.setText(Long.toString(data.metrics().soldTickets()));
                    dashboardRevenueValue.setText(formatCurrency(data.metrics().revenue()));
                    dashboardReservationValue.setText(Long.toString(data.metrics().pendingReservations()));
                    monthlyRevenueChart.setValues(data.monthlyRevenue());
                    eventDistributionChart.setValues(data.ticketDistribution());
                    recentActivityModel.clear();
                    for (ReportingDao.ActivityEntry entry : data.activity()) {
                        recentActivityModel.addElement(entry.createdAt().toString().replace('T', ' ')
                                + " · " + entry.user() + " · " + entry.action());
                    }
                    if (data.activity().isEmpty() && session.getRole() == UserRole.ADMIN) {
                        recentActivityModel.addElement("Aucune activité enregistrée.");
                    }
                    dashboardStatus.setText("Données actualisées.");
                    dashboardStatus.setForeground(UiTheme.MUTED);
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    showDashboardError("Actualisation interrompue.");
                } catch (java.util.concurrent.ExecutionException error) {
                    Throwable cause = error.getCause() == null ? error : error.getCause();
                    showDashboardError("Impossible de charger les indicateurs : " + cause.getMessage());
                }
            }
        }.execute();
    }

    private void showDashboardError(String message) {
        dashboardStatus.setText(message);
        dashboardStatus.setForeground(UiTheme.ERROR);
        dashboardEventValue.setText("—");
        dashboardTicketValue.setText("—");
        dashboardRevenueValue.setText("—");
        dashboardReservationValue.setText("—");
        monthlyRevenueChart.setError(message);
        eventDistributionChart.setError(message);
    }

    private static String formatCurrency(BigDecimal amount) {
        java.text.NumberFormat format = java.text.NumberFormat.getNumberInstance(Locale.FRANCE);
        format.setMinimumFractionDigits(0);
        format.setMaximumFractionDigits(2);
        return format.format(amount) + " Ar";
    }

    private record DashboardData(
            ReportingDao.DashboardMetrics metrics,
            List<ReportingDao.ChartValue> monthlyRevenue,
            List<ReportingDao.ChartValue> ticketDistribution,
            List<ReportingDao.ActivityEntry> activity) {
    }

    private JPanel buildPlaceholder(NavigationItem item) {
        JPanel page = new JPanel(new BorderLayout(0, 18));
        page.setOpaque(false);
        page.add(UiComponents.pageHeading(item.getLabel(), "Espace de travail TIKE"), BorderLayout.NORTH);
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(UiTheme.SURFACE);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UiTheme.BORDER),
                new EmptyBorder(28, 28, 28, 28)));
        JLabel message = new JLabel(
                "<html>Cette interface est prévue dans la maquette TIKE.<br>"
                        + "Ses fonctionnalités seront ajoutées dans la phase dédiée.</html>",
                SwingConstants.CENTER);
        message.setForeground(UiTheme.MUTED);
        panel.add(message, BorderLayout.CENTER);
        page.add(panel, BorderLayout.CENTER);
        return page;
    }

    private void showPage(NavigationItem item) {
        if (!item.isAvailableTo(session.getRole())) {
            return;
        }
        selectedItem = item;
        pageTitle.setText(item.getLabel());
        for (Map.Entry<NavigationItem, JButton> entry : navigationButtons.entrySet()) {
            boolean active = entry.getKey() == selectedItem;
            JButton button = entry.getValue();
            button.setBackground(active ? UiTheme.PRIMARY_LIGHT : UiTheme.SURFACE);
            button.setForeground(active ? UiTheme.PRIMARY_DARK : UiTheme.TEXT);
            button.setFont(active ? UiTheme.FONT_BOLD : UiTheme.FONT);
        }
        ((CardLayout) pageContainer.getLayout()).show(pageContainer, item.name());
        if (item == NavigationItem.HOME) {
            refreshDashboard();
        }
    }
}
