package dao;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ReporteEmpanadasDAO {

    public List<String[]> listarReportes() {
        List<String[]> lista = new ArrayList<>();

        String sql = "SELECT fecha, total, total_efectivo, total_transferencia, detalle, detalle_categorias "
                + "FROM reportes "
                + "ORDER BY id DESC";

        Connection conn = null;
        try {
            conn = Conexion.conectar();
            PreparedStatement stmt = conn.prepareStatement(sql);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                String detalleCategorias = rs.getString("detalle_categorias");

                int cantEmpanadas = extraerCantCategoria(detalleCategorias, "EMPANADAS");
                if (cantEmpanadas == 0) {
                    continue;
                }

                String detalleCompleto = rs.getString("detalle");
                double totalDia = rs.getDouble("total");
                double totalEfec = rs.getDouble("total_efectivo");
                double totalTransf = rs.getDouble("total_transferencia");

                String detalleEmp = filtrarDetalleEmpanadas(detalleCompleto);
                String masVendida = extraerMasVendida(detalleEmp);

                int cantRapidos = extraerCantCategoria(detalleCategorias, "RAPIDOS");
                int totalUnidades = cantEmpanadas + cantRapidos;
                double proporcion = totalUnidades > 0 ? (double) cantEmpanadas / totalUnidades : 1.0;
                double totalEmp = Math.round(totalDia * proporcion);
                double efecEmp = Math.round(totalEfec * proporcion);
                double transfEmp = Math.round(totalTransf * proporcion);

                String fechaStr = rs.getString("fecha");
                if (fechaStr != null && fechaStr.length() >= 10) {
                    fechaStr = fechaStr.substring(0, 10);
                }

                lista.add(new String[]{
                    fechaStr,
                    String.format("%.0f", totalEmp),
                    String.format("%.0f", efecEmp),
                    String.format("%.0f", transfEmp),
                    detalleEmp,
                    masVendida
                });
            }
            rs.close();
            stmt.close();

        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            if (conn != null) {
                Conexion.devolver(conn);
            }
        }

        return lista;
    }

    private int extraerCantCategoria(String detalleCategorias, String categoria) {
        if (detalleCategorias == null || detalleCategorias.isBlank()) {
            return 0;
        }
        for (String parte : detalleCategorias.split("\\|")) {
            String[] kv = parte.trim().split(":");
            if (kv.length == 2 && kv[0].trim().equalsIgnoreCase(categoria)) {
                try {
                    return Integer.parseInt(kv[1].trim());
                } catch (Exception e) {
                    return 0;
                }
            }
        }
        return 0;
    }

    private String filtrarDetalleEmpanadas(String detalle) {
        if (detalle == null || detalle.isBlank()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String item : detalle.split("\\|")) {
            String t = item.trim();
            if (t.isEmpty()) {
                continue;
            }
            String nombreLower = t.toLowerCase();
            if (nombreLower.startsWith("empanada")) {
                sb.append(t).append("|");
            }
        }
        return sb.toString();
    }

    private String extraerMasVendida(String detalleEmp) {
        if (detalleEmp == null || detalleEmp.isBlank()) {
            return "—";
        }
        String[] partes = detalleEmp.split("\\|");
        for (String p : partes) {
            String t = p.trim();
            if (!t.isEmpty()) {

                return t.contains(":") ? t.split(":")[0].trim() : t;
            }
        }
        return "—";
    }
}
