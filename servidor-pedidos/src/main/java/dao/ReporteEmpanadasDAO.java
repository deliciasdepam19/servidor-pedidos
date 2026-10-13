package dao;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ReporteEmpanadasDAO {

    public List<String[]> listarReportes() {
        List<String[]> lista = new ArrayList<>();

        String sql = "SELECT fecha, total, total_efectivo, total_transferencia, detalle, detalle_categorias, "
                + "COALESCE(total_debito, 0) AS total_debito, "
                + "COALESCE(total_credito, 0) AS total_credito "
                + "FROM reportes "
                + "WHERE fecha::date >= CURRENT_DATE - INTERVAL '3 months' "
                + "AND NOT (detalle = 'Cierre manual BD') "
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
                double totalDebito = rs.getDouble("total_debito");
                double totalCredito = rs.getDouble("total_credito");

                String detalleEmp = filtrarDetalleEmpanadas(detalleCompleto, conn);
                String masVendida = extraerMasVendida(detalleEmp);

                double totalEmp = calcularTotalDesdeDetalle(detalleEmp, conn);
                double proporcion = totalDia > 0 ? totalEmp / totalDia : 0.0;
                double efecEmp = Math.round(totalEfec * proporcion);
                double transfEmp = Math.round(totalTransf * proporcion);
                double debEmp = Math.round(totalDebito * proporcion);
                double credEmp = Math.round(totalCredito * proporcion);

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
                    masVendida,
                    String.format("%.0f", debEmp),
                    String.format("%.0f", credEmp)
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

    private String filtrarDetalleEmpanadas(String detalle, Connection conn) {
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
            String nombreProd = t.contains(":") ? t.split(":")[0].trim().toLowerCase() : nombreLower;
            boolean esEmpanada = nombreLower.startsWith("empanada")
                    || nombreLower.contains("arma tu empanada");
            if (!esEmpanada && conn != null) {
                try {
                    PreparedStatement ps = conn.prepareStatement(
                            "SELECT categoria FROM carta_items WHERE LOWER(TRIM(nombre)) = ? LIMIT 1");
                    ps.setString(1, nombreProd);
                    ResultSet rs = ps.executeQuery();
                    if (rs.next()) {
                        String cat = rs.getString("categoria");
                        if (cat != null && cat.toLowerCase().contains("empanada")) {
                            esEmpanada = true;
                        }
                    }
                    rs.close();
                    ps.close();
                } catch (SQLException e) {
                    // ignore — fall back to name matching
                }
            }
            if (esEmpanada) {
                // Limpiar prefijo "Empanada " para que parsearDetalle pueda mergear "Arma tu Empanada"
                if (nombreLower.startsWith("empanada ") && nombreLower.contains("arma tu empanada")) {
                    t = t.replaceFirst("(?i)^Empanada\\s+", "");
                }
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

    private double calcularTotalDesdeDetalle(String detalle, Connection conn) {
        if (detalle == null || detalle.isBlank()) {
            return 0;
        }
        double total = 0;
        for (String item : detalle.split("\\|")) {
            String t = item.trim();
            if (t.isEmpty() || !t.contains(":")) {
                continue;
            }
            String[] partes = t.split(":", 2);
            String nombre = partes[0].trim();
            int cantidad = 0;
            try {
                cantidad = Integer.parseInt(partes[1].trim().replaceAll("[^0-9]", ""));
            } catch (Exception e) {
                continue;
            }

            String nombreSinPrefijo = nombre.replaceAll("(?i)^empanada\\s*", "").trim();
            boolean esArma = nombre.toLowerCase().contains("arma tu empanada");

            try {
                PreparedStatement ps;
                if (esArma) {
                    ps = conn.prepareStatement(
                            "SELECT precio FROM carta_items WHERE LOWER(nombre) LIKE '%arma%empanada%' LIMIT 1");
                } else {
                    ps = conn.prepareStatement(
                            "SELECT precio FROM empanadas WHERE LOWER(tipo) = LOWER(?) OR LOWER(tipo) = LOWER(?) LIMIT 1");
                    ps.setString(1, nombre);
                    ps.setString(2, nombreSinPrefijo);
                }
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    total += rs.getDouble("precio") * cantidad;
                }
                rs.close();
                ps.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        return total;
    }
}
