package dao;

import java.sql.*;
import java.util.UUID;

public class InvitacionDAO {

    public String generar() {
        String codigo = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        String sql = "INSERT INTO invitaciones (codigo) VALUES (?)";
        Connection conn = null;
        try {
            conn = Conexion.conectar();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, codigo);
                ps.executeUpdate();
                return codigo;
            }
        } catch (SQLException e) {
            System.err.println("[InvitacionDAO] generar: " + e.getMessage());
        } finally {
            if (conn != null) Conexion.devolver(conn);
        }
        return null;
    }

    public boolean validar(String codigo) {
        String sql = "SELECT id FROM invitaciones WHERE codigo = ? AND activo = true";
        Connection conn = null;
        try {
            conn = Conexion.conectar();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, codigo.toUpperCase().trim());
                ResultSet rs = ps.executeQuery();
                return rs.next();
            }
        } catch (SQLException e) {
            System.err.println("[InvitacionDAO] validar: " + e.getMessage());
        } finally {
            if (conn != null) Conexion.devolver(conn);
        }
        return false;
    }

    public boolean marcarUsado(String codigo, String telefono) {
        String sql = "UPDATE invitaciones SET activo = false, used_by = ?, used_at = NOW() WHERE codigo = ?";
        Connection conn = null;
        try {
            conn = Conexion.conectar();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, telefono);
                ps.setString(2, codigo.toUpperCase().trim());
                return ps.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            System.err.println("[InvitacionDAO] marcarUsado: " + e.getMessage());
        } finally {
            if (conn != null) Conexion.devolver(conn);
        }
        return false;
    }

    public boolean revocar(String codigo) {
        String sql = "UPDATE invitaciones SET activo = false WHERE codigo = ?";
        Connection conn = null;
        try {
            conn = Conexion.conectar();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, codigo.toUpperCase().trim());
                return ps.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            System.err.println("[InvitacionDAO] revocar: " + e.getMessage());
        } finally {
            if (conn != null) Conexion.devolver(conn);
        }
        return false;
    }

    public boolean eliminar(String codigo) {
        String sql = "DELETE FROM invitaciones WHERE codigo = ?";
        Connection conn = null;
        try {
            conn = Conexion.conectar();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, codigo.toUpperCase().trim());
                return ps.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            System.err.println("[InvitacionDAO] eliminar: " + e.getMessage());
        } finally {
            if (conn != null) Conexion.devolver(conn);
        }
        return false;
    }

    public java.util.List<java.util.Map<String, Object>> listar() {
        java.util.List<java.util.Map<String, Object>> result = new java.util.ArrayList<>();
        String sql = "SELECT id, codigo, activo, used_by, created_at, used_at FROM invitaciones ORDER BY id ASC";
        Connection conn = null;
        try {
            conn = Conexion.conectar();
            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
                    row.put("id", rs.getInt("id"));
                    row.put("codigo", rs.getString("codigo"));
                    row.put("activo", rs.getBoolean("activo"));
                    row.put("used_by", rs.getString("used_by"));
                    row.put("created_at", rs.getString("created_at"));
                    row.put("used_at", rs.getString("used_at"));
                    result.add(row);
                }
            }
        } catch (SQLException e) {
            System.err.println("[InvitacionDAO] listar: " + e.getMessage());
        } finally {
            if (conn != null) Conexion.devolver(conn);
        }
        return result;
    }
}
