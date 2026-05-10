package ui.paneles;

import dao.PendientesDAO;
import java.awt.*;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import ui.UIHelper;

public class PendientesPanel {

    private static final Color ACCENT = new Color(180, 100, 210);
    private static final Color ACCENT_HOV = new Color(200, 120, 230);
    private static final Color COLOR_EFECTIVO = new Color(201, 168, 76);
    private static final Color COLOR_TRANSF = new Color(91, 127, 212);
    private static final Color COLOR_EXITO = new Color(46, 180, 100);
    private static final Color COLOR_ROJO = new Color(200, 80, 80);

    private final UIHelper h;
    private final JFrame parent;
    private JPanel listPanel;
    private JScrollPane scroll;
    private JLabel lblTotalPendiente;

    public PendientesPanel(UIHelper h, JFrame parent) {
        this.h = h;
        this.parent = parent;
    }

    public JPanel build() {
        JPanel root = new JPanel(new BorderLayout(0, 16));
        root.setOpaque(false);
        root.setBorder(new EmptyBorder(0, 16, 0, 0));

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JLabel titulo = new JLabel("Panel de pedidos Pendientes");
        titulo.setFont(new Font("Georgia", Font.BOLD, 22));
        titulo.setForeground(UIHelper.ACCENT_PRIMARY);

        JLabel SubTitulo = new JLabel("Pedidos pendientes de efectivo o tranferencia.");
        SubTitulo.setFont(new Font("SansSerif", Font.PLAIN, 11));
        SubTitulo.setForeground(UIHelper.TEXT_MUTED);

        JPanel titulos = new JPanel();
        titulos.setLayout(new BoxLayout(titulos, BoxLayout.Y_AXIS));
        titulos.setOpaque(false);
        titulos.add(titulo);
        titulos.add(Box.createVerticalStrut(2));
        titulos.add(SubTitulo);

        lblTotalPendiente = new JLabel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(180, 100, 210, 30));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);
                g2.setColor(new Color(180, 100, 210, 80));
                g2.setStroke(new BasicStroke(1f));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 20, 20);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        lblTotalPendiente.setFont(new Font("SansSerif", Font.BOLD, 11));
        lblTotalPendiente.setForeground(new Color(200, 140, 240));
        lblTotalPendiente.setOpaque(false);
        lblTotalPendiente.setBorder(new EmptyBorder(6, 14, 6, 14));

        header.add(titulos, BorderLayout.WEST);
        header.add(lblTotalPendiente, BorderLayout.EAST);

        JSeparator sep = new JSeparator();
        sep.setForeground(UIHelper.BORDER_COLOR);

        JPanel headerWrap = new JPanel(new BorderLayout(0, 8));
        headerWrap.setOpaque(false);
        headerWrap.add(header, BorderLayout.CENTER);
        headerWrap.add(sep, BorderLayout.SOUTH);

        root.add(headerWrap, BorderLayout.NORTH);

        listPanel = new JPanel();
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        listPanel.setOpaque(false);

        scroll = new JScrollPane(listPanel,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(14);

        root.add(scroll, BorderLayout.CENTER);

        cargar();
        return root;
    }

    public void cargar() {
        listPanel.removeAll();

        List<String[]> pendientes = PendientesDAO.listarPendientes();
        double totalGlobal = PendientesDAO.totalPendiente();

        lblTotalPendiente.setText("Total pendiente: " + UIHelper.formatPrecio(totalGlobal));

        if (pendientes.isEmpty()) {
            listPanel.setLayout(new GridBagLayout());

            JPanel vacioInner = new JPanel();
            vacioInner.setLayout(new BoxLayout(vacioInner, BoxLayout.Y_AXIS));
            vacioInner.setOpaque(false);

            JLabel icoVacio = new JLabel("📭", SwingConstants.CENTER);
            icoVacio.setFont(new Font("SansSerif", Font.PLAIN, 48));
            icoVacio.setForeground(UIHelper.TEXT_MUTED);
            icoVacio.setAlignmentX(Component.CENTER_ALIGNMENT);

            JLabel lblVacioTxt = new JLabel(
                    "<html><center>No hay pagos<br>pendientes</center></html>",
                    SwingConstants.CENTER);
            lblVacioTxt.setFont(new Font("SansSerif", Font.PLAIN, 13));
            lblVacioTxt.setForeground(UIHelper.TEXT_MUTED);
            lblVacioTxt.setAlignmentX(Component.CENTER_ALIGNMENT);

            vacioInner.add(icoVacio);
            vacioInner.add(Box.createVerticalStrut(12));
            vacioInner.add(lblVacioTxt);
            listPanel.add(vacioInner);

        } else {
            listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
            listPanel.setBorder(new EmptyBorder(8, 0, 8, 0));
            for (String[] p : pendientes) {
                listPanel.add(buildFila(p));
                listPanel.add(Box.createVerticalStrut(10));
            }
        }

        listPanel.revalidate();
        listPanel.repaint();
    }

    private JPanel buildFila(String[] p) {
        int id = Integer.parseInt(p[0]);
        String cliente = p[1];
        double total = Double.parseDouble(p[2]);
        String fechaVenta = p[3];
        String detalle = p[4];

        String detalleClean = detalle.trim();
        if (detalleClean.endsWith("|")) {
            detalleClean = detalleClean.substring(0, detalleClean.length() - 1).trim();
        }
        String[] items = detalleClean.split("\\|");

        String fechaFmt = java.time.LocalDate.parse(fechaVenta)
                .format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy",
                        new java.util.Locale("es", "CL")));

        JPanel card = new JPanel(new BorderLayout(0, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                GradientPaint gp = new GradientPaint(0, 0, new Color(35, 22, 48),
                        getWidth(), getHeight(), new Color(24, 28, 42));
                g2.setPaint(gp);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);
                g2.setColor(new Color(ACCENT.getRed(), ACCENT.getGreen(), ACCENT.getBlue(), 90));
                g2.setStroke(new BasicStroke(1.2f));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);
                g2.dispose();
            }
        };
        card.setOpaque(false);
        card.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel franja = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                GradientPaint gp = new GradientPaint(0, 0, ACCENT, 0, getHeight(), ACCENT_HOV);
                g2.setPaint(gp);
                g2.fillRoundRect(0, 0, getWidth() * 2, getHeight(), 14, 14);
                g2.dispose();
            }
        };
        franja.setOpaque(false);
        franja.setPreferredSize(new Dimension(5, 0));

        JPanel columnas = new JPanel(new GridLayout(1, 3, 0, 0));
        columnas.setOpaque(false);
        columnas.setBorder(new EmptyBorder(14, 16, 14, 16));

        JPanel col1 = new JPanel();
        col1.setLayout(new BoxLayout(col1, BoxLayout.Y_AXIS));
        col1.setOpaque(false);

        JLabel lblClienteTit = new JLabel("CLIENTE");
        lblClienteTit.setFont(new Font("SansSerif", Font.BOLD, 9));
        lblClienteTit.setForeground(UIHelper.TEXT_MUTED);
        lblClienteTit.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel lblClienteVal = new JLabel(cliente);
        lblClienteVal.setFont(new Font("Georgia", Font.BOLD, 16));
        lblClienteVal.setForeground(UIHelper.TEXT_PRIMARY);
        lblClienteVal.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel lblFechaTit = new JLabel("FECHA PENDIENTE");
        lblFechaTit.setFont(new Font("SansSerif", Font.BOLD, 9));
        lblFechaTit.setForeground(UIHelper.TEXT_MUTED);
        lblFechaTit.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel lblFechaVal = new JLabel(fechaFmt);
        lblFechaVal.setFont(new Font("SansSerif", Font.PLAIN, 12));
        lblFechaVal.setForeground(new Color(180, 100, 210));
        lblFechaVal.setAlignmentX(Component.LEFT_ALIGNMENT);

        col1.add(lblClienteTit);
        col1.add(Box.createVerticalStrut(2));
        col1.add(lblClienteVal);
        col1.add(Box.createVerticalStrut(12));
        col1.add(lblFechaTit);
        col1.add(Box.createVerticalStrut(2));
        col1.add(lblFechaVal);

        JPanel col2 = new JPanel();
        col2.setLayout(new BoxLayout(col2, BoxLayout.Y_AXIS));
        col2.setOpaque(false);
        col2.setBorder(new EmptyBorder(0, 16, 0, 16));

        JLabel lblDetTit = new JLabel("DETALLE DEL PEDIDO");
        lblDetTit.setFont(new Font("SansSerif", Font.BOLD, 9));
        lblDetTit.setForeground(UIHelper.TEXT_MUTED);
        lblDetTit.setAlignmentX(Component.LEFT_ALIGNMENT);
        col2.add(lblDetTit);
        col2.add(Box.createVerticalStrut(4));

        for (String item : items) {
            String t = item.trim();
            if (t.isEmpty()) {
                continue;
            }
            JLabel chip = new JLabel(t) {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(new Color(ACCENT.getRed(), ACCENT.getGreen(), ACCENT.getBlue(), 25));
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);
                    g2.setColor(new Color(ACCENT.getRed(), ACCENT.getGreen(), ACCENT.getBlue(), 70));
                    g2.setStroke(new BasicStroke(1f));
                    g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 20, 20);
                    g2.dispose();
                    super.paintComponent(g);
                }
            };
            chip.setFont(new Font("SansSerif", Font.PLAIN, 11));
            chip.setForeground(new Color(200, 160, 230));
            chip.setOpaque(false);
            chip.setBorder(new EmptyBorder(3, 10, 3, 10));
            chip.setAlignmentX(Component.LEFT_ALIGNMENT);
            col2.add(chip);
            col2.add(Box.createVerticalStrut(3));
        }

        col2.add(Box.createVerticalStrut(8));
        JSeparator sepCol = new JSeparator();
        sepCol.setForeground(new Color(ACCENT.getRed(), ACCENT.getGreen(), ACCENT.getBlue(), 50));
        sepCol.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        col2.add(sepCol);
        col2.add(Box.createVerticalStrut(6));

        JLabel lblTotalTit = new JLabel("TOTAL");
        lblTotalTit.setFont(new Font("SansSerif", Font.BOLD, 9));
        lblTotalTit.setForeground(UIHelper.TEXT_MUTED);
        lblTotalTit.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel lblTotalVal = new JLabel(UIHelper.formatPrecio(total));
        lblTotalVal.setFont(new Font("Georgia", Font.BOLD, 20));
        lblTotalVal.setForeground(ACCENT);
        lblTotalVal.setAlignmentX(Component.LEFT_ALIGNMENT);

        col2.add(lblTotalTit);
        col2.add(Box.createVerticalStrut(2));
        col2.add(lblTotalVal);

        JPanel col3 = new JPanel(new BorderLayout(0, 8));
        col3.setOpaque(false);
        col3.setBorder(new EmptyBorder(0, 16, 0, 0));

        JLabel lblBtnTit = new JLabel("LIQUIDAR", SwingConstants.CENTER);
        lblBtnTit.setFont(new Font("SansSerif", Font.BOLD, 9));
        lblBtnTit.setForeground(UIHelper.TEXT_MUTED);

        JButton btnEfectivo = mkBtnPago("💵  Efectivo", COLOR_EFECTIVO);
        JButton btnTransf = mkBtnPago("🏦  Transferencia", COLOR_TRANSF);
        JButton btnAnular = mkBtnPago("✕  Anular", COLOR_ROJO);

        btnEfectivo.addActionListener(e -> liquidar(id, "EFECTIVO", cliente, total));
        btnTransf.addActionListener(e -> liquidar(id, "TRANSFERENCIA", cliente, total));
        btnAnular.addActionListener(e -> confirmarAnular(id, cliente));

        JPanel btnsPanel = new JPanel();
        btnsPanel.setLayout(new BoxLayout(btnsPanel, BoxLayout.Y_AXIS));
        btnsPanel.setOpaque(false);
        btnsPanel.add(btnEfectivo);
        btnsPanel.add(Box.createVerticalStrut(5));
        btnsPanel.add(btnTransf);
        btnsPanel.add(Box.createVerticalStrut(5));
        btnsPanel.add(btnAnular);

        col3.add(lblBtnTit, BorderLayout.NORTH);
        col3.add(btnsPanel, BorderLayout.CENTER);

        columnas.add(col1);
        columnas.add(col2);
        columnas.add(col3);

        card.add(franja, BorderLayout.WEST);
        card.add(columnas, BorderLayout.CENTER);

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
        wrapper.setMaximumSize(new Dimension(Integer.MAX_VALUE, 160));
        wrapper.add(card, BorderLayout.CENTER);
        return wrapper;
    }

    private JButton mkBtnPago(String texto, Color color) {
        JButton btn = new JButton(texto) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 30));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 120));
                g2.setStroke(new BasicStroke(1f));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btn.setFont(new Font("SansSerif", Font.BOLD, 11));
        btn.setForeground(color);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setAlignmentX(Component.CENTER_ALIGNMENT);
        btn.setMaximumSize(new Dimension(160, 30));
        btn.setPreferredSize(new Dimension(160, 30));
        btn.setBorder(new EmptyBorder(4, 12, 4, 12));
        return btn;
    }

    private void liquidar(int id, String tipoPago, String cliente, double total) {
        new SwingWorker<Boolean, Void>() {
            @Override
            protected Boolean doInBackground() {
                return PendientesDAO.liquidar(id, tipoPago);
            }

            @Override
            protected void done() {
                boolean ok;
                try {
                    ok = get();
                } catch (Exception ex) {
                    ex.printStackTrace();
                    ok = false;
                }

                if (!ok) {
                    h.showDialog(parent,
                            "<html><center>No se pudo liquidar el pendiente.</center></html>",
                            "Error",
                            JOptionPane.WARNING_MESSAGE);
                    return;
                }
                cargar();
                showExito(cliente, total, tipoPago);
            }
        }.execute();
    }

    private void showExito(String cliente, double total, String tipoPago) {
        JDialog dlg = new JDialog(parent, "Liquidado", true);
        dlg.setUndecorated(true);
        JPanel outer = h.dialogOuter(COLOR_EXITO);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setOpaque(false);
        content.add(h.dialogIcon("✓", COLOR_EXITO));
        content.add(Box.createVerticalStrut(4));
        JLabel lbl = h.dialogTitle("¡Pago Liquidado!");
        lbl.setForeground(COLOR_EXITO);
        content.add(lbl);
        content.add(Box.createVerticalStrut(14));
        content.add(h.dialogSep());

        JPanel filas = new JPanel();
        filas.setLayout(new BoxLayout(filas, BoxLayout.Y_AXIS));
        filas.setOpaque(false);
        filas.setBorder(new EmptyBorder(14, 0, 14, 0));
        filas.setMaximumSize(new Dimension(Integer.MAX_VALUE, 2000));
        filas.add(h.filaResumen("Cliente", cliente));
        filas.add(Box.createVerticalStrut(8));
        filas.add(h.filaResumen("Monto", UIHelper.formatPrecio(total)));
        filas.add(Box.createVerticalStrut(8));
        filas.add(h.filaResumen("Forma de pago",
                tipoPago.charAt(0) + tipoPago.substring(1).toLowerCase()));
        content.add(filas);
        content.add(h.dialogSep());

        JLabel nota = new JLabel(
                "<html><center>Registrado en el reporte del día de la venta original.</center></html>",
                SwingConstants.CENTER);
        nota.setFont(new Font("SansSerif", Font.ITALIC, 11));
        nota.setForeground(UIHelper.TEXT_MUTED);
        nota.setAlignmentX(Component.CENTER_ALIGNMENT);
        nota.setBorder(new EmptyBorder(10, 0, 10, 0));
        content.add(nota);

        JButton btnOk = h.accentButton("OK", COLOR_EXITO, COLOR_EXITO.darker());
        btnOk.setPreferredSize(new Dimension(110, 38));
        btnOk.addActionListener(ev -> dlg.dispose());
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        btnPanel.setOpaque(false);
        btnPanel.add(btnOk);
        content.add(btnPanel);

        outer.add(content, BorderLayout.CENTER);
        dlg.setContentPane(outer);
        dlg.pack();
        dlg.setMinimumSize(new Dimension(300, 0));
        dlg.setLocationRelativeTo(parent);
        dlg.setVisible(true);
    }

    private void confirmarAnular(int id, String cliente) {
        JDialog dlg = new JDialog(parent, "Confirmar", true);
        dlg.setUndecorated(true);
        JPanel outer = h.dialogOuter(COLOR_ROJO);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setOpaque(false);

        JLabel lblT = h.dialogTitle("¿Anular pendiente?");
        lblT.setForeground(COLOR_ROJO);
        content.add(lblT);
        content.add(Box.createVerticalStrut(8));
        content.add(h.dialogSep());
        content.add(Box.createVerticalStrut(12));

        JLabel lblMsg = new JLabel(
                "<html><center>Se eliminará el pendiente de <b>" + cliente + "</b>.<br>"
                + "El stock ya fue descontado y no se revierte.</center></html>",
                SwingConstants.CENTER);
        lblMsg.setFont(new Font("SansSerif", Font.PLAIN, 12));
        lblMsg.setForeground(UIHelper.TEXT_PRIMARY);
        lblMsg.setAlignmentX(Component.CENTER_ALIGNMENT);
        lblMsg.setBorder(new EmptyBorder(0, 0, 16, 0));
        content.add(lblMsg);

        JButton btnSi = h.accentButton("Sí, anular", COLOR_ROJO, COLOR_ROJO.darker());
        btnSi.setPreferredSize(new Dimension(120, 36));
        JButton btnNo = h.accentButton("Cancelar", UIHelper.BG_PANEL, UIHelper.BORDER_COLOR);
        btnNo.setPreferredSize(new Dimension(110, 36));

        btnNo.addActionListener(e -> dlg.dispose());

        btnSi.addActionListener(e -> {
            dlg.dispose();

            JDialog dlgCarga = new JDialog(parent, "Anulando", false);
            dlgCarga.setUndecorated(true);
            JPanel outerCarga = h.dialogOuter(COLOR_ROJO);
            JPanel contentCarga = new JPanel();
            contentCarga.setLayout(new BoxLayout(contentCarga, BoxLayout.Y_AXIS));
            contentCarga.setOpaque(false);
            contentCarga.setBorder(new EmptyBorder(20, 30, 20, 30));

            JLabel lblCargando = new JLabel("Eliminando pendiente...", SwingConstants.CENTER);
            lblCargando.setFont(new Font("SansSerif", Font.PLAIN, 13));
            lblCargando.setForeground(UIHelper.TEXT_PRIMARY);
            lblCargando.setAlignmentX(Component.CENTER_ALIGNMENT);

            JProgressBar spinner = new JProgressBar();
            spinner.setIndeterminate(true);
            spinner.setPreferredSize(new Dimension(220, 6));
            spinner.setMaximumSize(new Dimension(220, 6));
            spinner.setAlignmentX(Component.CENTER_ALIGNMENT);
            spinner.setForeground(COLOR_ROJO);
            spinner.setBackground(UIHelper.BORDER_COLOR);
            spinner.setBorderPainted(false);

            contentCarga.add(lblCargando);
            contentCarga.add(Box.createVerticalStrut(14));
            contentCarga.add(spinner);
            outerCarga.add(contentCarga, BorderLayout.CENTER);
            dlgCarga.setContentPane(outerCarga);
            dlgCarga.pack();
            dlgCarga.setMinimumSize(new Dimension(280, 0));
            dlgCarga.setLocationRelativeTo(parent);

            new SwingWorker<Void, Void>() {
                @Override
                protected Void doInBackground() {
                    SwingUtilities.invokeLater(() -> dlgCarga.setVisible(true));
                    PendientesDAO.eliminar(id);
                    return null;
                }

                @Override
                protected void done() {
                    dlgCarga.dispose();
                    cargar();
                }
            }.execute();
        });

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        btnPanel.setOpaque(false);
        btnPanel.add(btnNo);
        btnPanel.add(btnSi);
        content.add(btnPanel);

        outer.add(content, BorderLayout.CENTER);
        dlg.setContentPane(outer);
        dlg.pack();
        dlg.setMinimumSize(new Dimension(340, 0));
        dlg.setLocationRelativeTo(parent);
        dlg.setVisible(true);
    }
}
