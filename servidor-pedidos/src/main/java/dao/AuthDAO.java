package dao;

import java.sql.*;
import java.util.UUID;

public class AuthDAO {

    // Guardar código OTP
    public void guardarCodigo(String email, String codigo) {
        String deleteSql = "DELETE FROM otp_codes WHERE email = ? AND usado = false";
        String insertSql = "INSERT INTO otp_codes (email, codigo, expira_en) VALUES (?, ?, NOW() + INTERVAL '5 minutes')";
        Connection conn = null;
        try {
            conn = Conexion.conectar();
            try (PreparedStatement ps = conn.prepareStatement(deleteSql)) {
                ps.setString(1, email);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                ps.setString(1, email);
                ps.setString(2, codigo);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("[AuthDAO] guardarCodigo: " + e.getMessage());
        } finally {
            if (conn != null) Conexion.devolver(conn);
        }
    }

    // Generar código OTP de 4 dígitos y guardarlo en la BD
    public String generarCodigo(String email) {
        String codigo = String.format("%04d", (int) (Math.random() * 10000));
        String sql = "INSERT INTO otp_codes (email, codigo, expira_en) VALUES (?, ?, NOW() + INTERVAL '5 minutes')";

        // Eliminar códigos anteriores de este email
        String deleteSql = "DELETE FROM otp_codes WHERE email = ? AND usado = false";
        Connection conn = null;
        try {
            conn = Conexion.conectar();
            try (PreparedStatement ps = conn.prepareStatement(deleteSql)) {
                ps.setString(1, email);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, email);
                ps.setString(2, codigo);
                ps.executeUpdate();
            }
            return codigo;
        } catch (SQLException e) {
            System.err.println("[AuthDAO] generarCodigo: " + e.getMessage());
        } finally {
            if (conn != null) Conexion.devolver(conn);
        }
        return null;
    }

    // Verificar código OTP
    public boolean verificarCodigo(String email, String codigo) {
        String sql = "SELECT id FROM otp_codes WHERE email = ? AND codigo = ? AND usado = false AND expira_en > NOW()";
        Connection conn = null;
        try {
            conn = Conexion.conectar();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, email);
                ps.setString(2, codigo);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        int id = rs.getInt("id");
                        // Marcar como usado
                        String updateSql = "UPDATE otp_codes SET usado = true WHERE id = ?";
                        try (PreparedStatement updatePs = conn.prepareStatement(updateSql)) {
                            updatePs.setInt(1, id);
                            updatePs.executeUpdate();
                        }
                        return true;
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("[AuthDAO] verificarCodigo: " + e.getMessage());
        } finally {
            if (conn != null) Conexion.devolver(conn);
        }
        return false;
    }

    // Crear o actualizar cliente por email
    public String crearCliente(String email, String nombre) {
        String sql = "INSERT INTO clientes (email, nombre) VALUES (?, ?) "
                + "ON CONFLICT (email) DO UPDATE SET nombre = EXCLUDED.nombre "
                + "RETURNING id";
        Connection conn = null;
        try {
            conn = Conexion.conectar();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, email);
                ps.setString(2, nombre != null ? nombre : email.split("@")[0]);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString("id");
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("[AuthDAO] crearCliente: " + e.getMessage());
        } finally {
            if (conn != null) Conexion.devolver(conn);
        }
        return null;
    }

    // Obtener cliente por email
    public Object[] obtenerCliente(String email) {
        String sql = "SELECT id, nombre, email FROM clientes WHERE email = ?";
        Connection conn = null;
        try {
            conn = Conexion.conectar();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, email);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return new Object[]{
                            rs.getString("id"),
                            rs.getString("nombre"),
                            rs.getString("email")
                        };
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("[AuthDAO] obtenerCliente: " + e.getMessage());
        } finally {
            if (conn != null) Conexion.devolver(conn);
        }
        return null;
    }

    // Eliminar cliente
    public boolean eliminarCliente(String email) {
        String sql = "DELETE FROM clientes WHERE email = ?";
        Connection conn = null;
        try {
            conn = Conexion.conectar();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, email);
                return ps.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            System.err.println("[AuthDAO] eliminarCliente: " + e.getMessage());
        } finally {
            if (conn != null) Conexion.devolver(conn);
        }
        return false;
    }
}
