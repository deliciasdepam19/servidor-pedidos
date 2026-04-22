package dao;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ReporteDAO {

    public List<String[]> listarReportes() {
        List<String[]> lista = new ArrayList<>();

        String sql = "SELECT fecha, total, total_efectivo, total_transferencia, detalle, "
                + "COALESCE(total_pendiente, 0) AS total_pendiente, "
                + "COALESCE(pedidos_web, 0) AS pedidos_web, "
                + "COALESCE(pedidos_local, 0) AS pedidos_local, "
                + "COALESCE(generado_por, '') AS generado_por, "
                + "COALESCE(detalle_categorias, '') AS detalle_categorias "
                + "FROM reportes ORDER BY id DESC";

        Connection conn = null;

        try {
            conn = Conexion.conectar();
            PreparedStatement stmt = conn.prepareStatement(sql);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                lista.add(new String[]{
                    rs.getDate("fecha").toLocalDate().toString(),
                    String.format("$%.0f", rs.getDouble("total")),
                    String.format("$%.0f", rs.getDouble("total_efectivo")),
                    String.format("$%.0f", rs.getDouble("total_transferencia")),
                    rs.getString("detalle"),
                    String.format("$%.0f", rs.getDouble("total_pendiente")),
                    String.valueOf(rs.getInt("pedidos_web")),
                    String.valueOf(rs.getInt("pedidos_local")),
                    rs.getString("generado_por"),
                    rs.getString("detalle_categorias")
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
}
