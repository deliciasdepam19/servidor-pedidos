package server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dao.InventarioDAO;
import dao.PedidosDAO;
import dao.RecetaDAO;
import dao.RecetaItem;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PedidosServer {

    private final PedidosDAO    pedidosDAO = new PedidosDAO();
    private final RecetaDAO     recetaDAO  = new RecetaDAO();
    private final InventarioDAO invDAO     = new InventarioDAO();

    private final Object pedidoLock = new Object();

    private static final int PUERTO = System.getenv("PORT") != null
            ? Integer.parseInt(System.getenv("PORT")) : 8888;

    private HttpServer servidor;

    // ── Throttling por IP ─────────────────────────────────────────────────────
    private static final long VENTANA_MS       = 10_000L;       // 10 seg entre pedidos
    private static final int  MAX_PEDIDOS_HORA = 5;             // máx pedidos por hora
    private static final long HORA_MS          = 60 * 60 * 1000L;
    private static final long BLOQUEO_MS       = 30 * 60 * 1000L; // bloqueo 30 min

    private final Map<String, Long>    ultimoPedidoPorIp = new ConcurrentHashMap<>();
    private final Map<String, Integer> contadorPorIp     = new ConcurrentHashMap<>();
    private final Map<String, Long>    bloqueadoHasta    = new ConcurrentHashMap<>();

    private static class ItemCarrito {
        String nombre;
        String categoria;
        int    cantidad;
    }

    public PedidosServer() throws IOException {

        servidor = HttpServer.create(new InetSocketAddress("0.0.0.0", PUERTO), 0);

        servidor.createContext("/api/pedidos", exchange -> {

            agregarCorsHeaders(exchange);

            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if ("POST".equals(exchange.getRequestMethod())) {

                String errorThrottle = verificarThrottle(exchange);
                if (errorThrottle != null) {
                    enviarRespuesta(exchange, 429, errorThrottle);
                    return;
                }
                // ─────────────────────────────────────────────────────────

                String body = readBody(exchange);
                System.out.println("[PEDIDOS] Body recibido: " + body);

                synchronized (pedidoLock) {
                    try {
                        String cliente  = sanitizar(extraerValor(body, "cliente"));
                        String telefono = sanitizar(extraerValor(body, "telefono"));
                        String detalle  = sanitizar(extraerValor(body, "detalle"));
                        double total    = extraerDouble(body, "total");

                        String tipoPago = extraerValor(body, "tipoPago");
                        if ("-".equals(tipoPago) || tipoPago.isBlank()) tipoPago = "EFECTIVO";

                        // ── Detección de duplicados ───────────────────────
                        if (esPedidoDuplicado(cliente, detalle)) {
                            System.out.println("[PEDIDOS] Duplicado detectado para: " + cliente);
                            enviarRespuesta(exchange, 200,
                                    "{\"exito\":true,\"numero\":0,\"duplicado\":true}");
                            return;
                        }

                        String categorias = extraerCategoriasDeLosItems(body);
                        System.out.println("CATEGORIAS DETECTADAS: " + categorias);

                        String franja = calcularFranjaActual(detalle, categorias);
                        System.out.println("FRANJA CALCULADA: " + franja);

                        if ("FUERA HORARIO".equals(franja)) {
                            enviarRespuesta(exchange, 403,
                                    "{\"exito\":false,\"error\":\"Pedido fuera de horario permitido\"}");
                            return;
                        }

                        String fechaEntrega = extraerValor(body, "fecha_entrega");
                        if ("-".equals(fechaEntrega) || fechaEntrega.isBlank()) fechaEntrega = null;

                        String categoriasDetalle = construirCategoriasDetalle(body);
                        System.out.println("[PEDIDOS] categoriasDetalle: " + categoriasDetalle);

                        int[] resultado = pedidosDAO.guardarPedidoAutoNumero(
                                cliente, telefono, detalle, total, franja, "WEB",
                                fechaEntrega, categoriasDetalle);

                        System.out.println("[PEDIDOS] Resultado: id=" + resultado[0] + " num=" + resultado[1]);

                        int id           = resultado[0];
                        int numeroPedido = resultado[1];

                        if (id > 0) descontarInventarioDesdeItems(body);

                        enviarRespuesta(exchange, 200, "{"
                                + "\"exito\":true,"
                                + "\"id\":"     + id           + ","
                                + "\"numero\":" + numeroPedido + "}");

                    } catch (Exception e) {
                        e.printStackTrace();
                        enviarRespuesta(exchange, 400, "{\"exito\":false}");
                    }
                }
            }
        });

        servidor.createContext("/api/pedidos/historico", exchange -> {
            agregarCorsHeaders(exchange);
            if ("GET".equals(exchange.getRequestMethod())) {
                List<PedidosDAO.PedidoBD> pedidos = pedidosDAO.cargarPedidosDeHoy();
                StringBuilder json = new StringBuilder("[");
                for (int i = 0; i < pedidos.size(); i++) {
                    PedidosDAO.PedidoBD p = pedidos.get(i);
                    json.append("{")
                            .append("\"id\":").append(p.id).append(",")
                            .append("\"numero\":").append(p.numero).append(",")
                            .append("\"numeroFormateado\":\"")
                            .append(PedidosDAO.formatearNumero(p.numero, p.origen)).append("\",")
                            .append("\"cliente\":\"").append(escaparJson(p.cliente)).append("\",")
                            .append("\"telefono\":\"").append(escaparJson(p.telefono)).append("\",")
                            .append("\"detalle\":\"").append(escaparJson(p.detalle)).append("\",")
                            .append("\"total\":").append(p.total).append(",")
                            .append("\"estado\":\"").append(p.estado).append("\",")
                            .append("\"franja\":\"").append(p.franja).append("\",")
                            .append("\"timestamp\":\"").append(obtenerHoraExacta()).append("\"")
                            .append("}");
                    if (i < pedidos.size() - 1) json.append(",");
                }
                json.append("]");
                enviarRespuesta(exchange, 200, json.toString());
            }
        });

        servidor.createContext("/api/stock", exchange -> {
            agregarCorsHeaders(exchange);
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            if ("GET".equals(exchange.getRequestMethod())) {
                try {
                    enviarRespuesta(exchange, 200, StockDescontador.obtenerStockJSON());
                } catch (Exception e) {
                    e.printStackTrace();
                    enviarRespuesta(exchange, 500, "{}");
                }
            }
        });

        servidor.createContext("/api/usuarios", exchange -> {
            agregarCorsHeaders(exchange);
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            if ("POST".equals(exchange.getRequestMethod())) {
                try {
                    String body   = readBody(exchange);
                    String nombre = sanitizar(extraerValor(body, "nombre"));
                    String email  = sanitizar(extraerValor(body, "email"));
                    System.out.println("Usuario: " + nombre + " / " + email);
                    enviarRespuesta(exchange, 200, "{\"exito\":true}");
                } catch (Exception e) {
                    e.printStackTrace();
                    enviarRespuesta(exchange, 400, "{\"exito\":false}");
                }
            }
        });

        servidor.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(10));
        System.out.println("Servidor OK puerto " + PUERTO);
    }

    private String obtenerIp(HttpExchange exchange) {
        String forwarded = exchange.getRequestHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return exchange.getRemoteAddress().getAddress().getHostAddress();
    }

    private String verificarThrottle(HttpExchange exchange) {
        String ip  = obtenerIp(exchange);
        long ahora = System.currentTimeMillis();

        // Regla 3: ¿está bloqueada la IP?
        Long bloqueado = bloqueadoHasta.get(ip);
        if (bloqueado != null && ahora < bloqueado) {
            long minutosRestantes = (bloqueado - ahora) / 60_000 + 1;
            System.out.println("[THROTTLE] IP bloqueada rechazada: " + ip
                    + " (" + minutosRestantes + " min restantes)");
            return "{\"exito\":false,\"error\":\"Demasiados intentos. "
                    + "Reintenta en " + minutosRestantes + " minutos.\"}";
        } else if (bloqueado != null) {
            // Bloqueo expirado → limpiar estado
            bloqueadoHasta.remove(ip);
            contadorPorIp.remove(ip);
            ultimoPedidoPorIp.remove(ip);
        }

        // Regla 1: ventana mínima de 10 segundos entre pedidos
        Long ultimo = ultimoPedidoPorIp.get(ip);
        if (ultimo != null && (ahora - ultimo) < VENTANA_MS) {
            long segsRestantes = (VENTANA_MS - (ahora - ultimo)) / 1000 + 1;
            System.out.println("[THROTTLE] Ventana activa para IP: " + ip
                    + " (" + segsRestantes + "s restantes)");
            return "{\"exito\":false,\"error\":\"Espera " + segsRestantes
                    + " segundos antes de enviar otro pedido.\"}";
        }

        // Regla 2: máximo 5 pedidos por hora
        // Si el último pedido fue hace más de 1 hora, resetear contador
        int contador = contadorPorIp.getOrDefault(ip, 0);
        if (ultimo != null && (ahora - ultimo) >= HORA_MS) {
            contador = 0;
            contadorPorIp.put(ip, 0);
        }

        if (contador >= MAX_PEDIDOS_HORA) {
            // Bloquear 30 minutos
            bloqueadoHasta.put(ip, ahora + BLOQUEO_MS);
            System.out.println("[THROTTLE] IP bloqueada 30min por exceso de pedidos: " + ip);
            return "{\"exito\":false,\"error\":\"Demasiados pedidos. "
                    + "IP bloqueada durante 30 minutos.\"}";
        }

        // Todo OK → registrar este pedido
        ultimoPedidoPorIp.put(ip, ahora);
        contadorPorIp.put(ip, contador + 1);
        System.out.println("[THROTTLE] IP " + ip + " → pedido #" + (contador + 1)
                + " en la ventana horaria");
        return null; 
    }

    private boolean esPedidoDuplicado(String cliente, String detalle) {
        String sql = "SELECT COUNT(*) FROM pedidos "
                + "WHERE cliente = ? AND detalle = ? "
                + "AND fecha_hora > NOW() - INTERVAL '15 seconds' "
                + "AND origen = 'WEB'";
        Connection conn = null;
        try {
            conn = dao.Conexion.conectar();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, cliente);
                ps.setString(2, detalle);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() && rs.getInt(1) > 0;
                }
            }
        } catch (Exception e) {
            System.out.println("[PEDIDOS] Error verificando duplicado: " + e.getMessage());
            return false;
        } finally {
            if (conn != null) dao.Conexion.devolver(conn);
        }
    }

    private String construirCategoriasDetalle(String json) {
        List<ItemCarrito> items = extraerItems(json);
        Map<String, Integer> conteo = new LinkedHashMap<>();
        for (ItemCarrito item : items) {
            String cat = normalizarCategoria(item.categoria);
            conteo.merge(cat, item.cantidad, Integer::sum);
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> e : conteo.entrySet()) {
            if (sb.length() > 0) sb.append(",");
            sb.append(e.getKey()).append(":").append(e.getValue());
        }
        return sb.toString();
    }

    private void descontarInventarioDesdeItems(String json) {
        List<ItemCarrito> items = extraerItems(json);
        for (ItemCarrito item : items) {
            try {
                String cat = normalizarCategoria(item.categoria);
                List<RecetaItem> receta;
                if (cat.equals("rapido") || cat.isEmpty()) {
                    receta = recetaDAO.obtenerPorNombre(item.nombre);
                } else {
                    receta = recetaDAO.obtenerPorProducto(0, cat);
                }
                for (RecetaItem r : receta) {
                    double gramosTotal = r.getCantidadG() * item.cantidad;
                    invDAO.descontarStock(r.getIdIngrediente(), gramosTotal);
                    System.out.println("[INV] Descontado " + gramosTotal + "g de ingrediente "
                            + r.getIdIngrediente() + " por " + item.cantidad + "x " + item.nombre);
                }
            } catch (Exception e) {
                System.out.println("[INV] Error descontando " + item.nombre + ": " + e.getMessage());
            }
        }
    }

    private String normalizarCategoria(String cat) {
        if (cat == null) return "rapido";
        cat = cat.toLowerCase().trim();
        if (cat.endsWith("s") && !cat.equals("rapido")) {
            cat = cat.substring(0, cat.length() - 1);
        }
        cat = cat.replace("á","a").replace("é","e").replace("í","i")
                 .replace("ó","o").replace("ú","u").replace("ñ","n");
        return cat.isEmpty() ? "rapido" : cat;
    }

    private List<ItemCarrito> extraerItems(String json) {
        List<ItemCarrito> lista = new ArrayList<>();
        try {
            int inicio = json.indexOf("\"items\":");
            if (inicio == -1) return lista;
            inicio = json.indexOf("[", inicio);
            int fin = json.indexOf("]", inicio);
            if (inicio == -1 || fin == -1) return lista;

            String itemsStr = json.substring(inicio + 1, fin);
            int i = 0;
            while ((i = itemsStr.indexOf("{", i)) != -1) {
                int cierre = itemsStr.indexOf("}", i);
                if (cierre == -1) break;
                String obj = itemsStr.substring(i, cierre + 1);

                ItemCarrito item = new ItemCarrito();
                item.nombre    = extraerValor(obj, "nombre");
                item.categoria = extraerValor(obj, "categoria");
                item.cantidad  = (int) extraerDouble(obj, "cantidad");

                if (item.nombre != null && !item.nombre.equals("-") && item.cantidad > 0) {
                    lista.add(item);
                    System.out.println("[INV] Item parseado: " + item.cantidad
                            + "x " + item.nombre + " cat=" + item.categoria);
                }
                i = cierre + 1;
            }
        } catch (Exception e) {
            System.out.println("[INV] Error parseando items: " + e.getMessage());
        }
        return lista;
    }

    private String readBody(HttpExchange exchange) throws IOException {
        return new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    private String extraerValor(String json, String clave) {
        String patron = "\"" + clave + "\":\"";
        int i = json.indexOf(patron);
        if (i == -1) return "-";
        i += patron.length();
        int f = json.indexOf("\"", i);
        return f == -1 ? "-" : json.substring(i, f);
    }

    private double extraerDouble(String json, String clave) {
        try {
            String patron = "\"" + clave + "\":";
            int i = json.indexOf(patron);
            if (i == -1) return 0;
            i += patron.length();
            int f = json.indexOf(",", i);
            if (f == -1) f = json.indexOf("}", i);
            return Double.parseDouble(json.substring(i, f).trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private String extraerCategoriasDeLosItems(String json) {
        StringBuilder cats = new StringBuilder();
        String patron = "\"categoria\":\"";
        int i = 0;
        while ((i = json.indexOf(patron, i)) != -1) {
            i += patron.length();
            int f = json.indexOf("\"", i);
            if (f != -1) cats.append(json, i, f).append(" ");
        }
        return cats.toString().toLowerCase();
    }

    private String calcularFranjaActual(String detalle, String categorias) {
        java.time.LocalTime ahora = java.time.LocalTime.now(
                java.time.ZoneId.of("America/Santiago"));
        int hora   = ahora.getHour();
        int minuto = ahora.getMinute();

        String d = detalle    != null ? detalle.toLowerCase()    : "";
        String c = categorias != null ? categorias.toLowerCase() : "";

        boolean esPanaderia  = c.contains("panaderia") || c.contains("panadería")
                            || d.contains("panaderia") || d.contains("panadería")
                            || d.contains("hallula")   || d.contains("marraqueta")
                            || d.contains("dobladita") || d.contains("pan amasado")
                            || d.contains("pan ");
        boolean esAnticipado = c.contains("pasteler") || c.contains("reposteri")
                            || d.contains("pasteler") || d.contains("reposteri");

        if (esPanaderia) {
            if (hora < 12 || hora >= 18) return "FUERA HORARIO";
        } else if (esAnticipado) {
            if (hora < 12 || hora >= 22) return "FUERA HORARIO";
        } else {
            if (hora < 18 || hora >= 22) return "FUERA HORARIO";
        }

        int inicioHora, inicioMin, finHora, finMin;
        if (minuto < 30) {
            inicioHora = hora; inicioMin = 0; finHora = hora; finMin = 30;
        } else {
            inicioHora = hora; inicioMin = 30; finHora = hora + 1; finMin = 0;
        }
        return String.format("%02d:%02d - %02d:%02d", inicioHora, inicioMin, finHora, finMin);
    }

    private String escaparJson(String t) {
        return t == null ? "" : t.replace("\"", "\\\"");
    }

    private void agregarCorsHeaders(HttpExchange e) {
        e.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        e.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        e.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
    }

    private void enviarRespuesta(HttpExchange ex, int code, String r) throws IOException {
        byte[] b = r.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(code, b.length);
        ex.getResponseBody().write(b);
        ex.close();
    }

    private String obtenerHoraExacta() {
        return java.time.LocalTime.now(java.time.ZoneId.of("America/Santiago"))
                .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
    }

    private String sanitizar(String v) {
        return v == null ? "-" : v.replaceAll("[<>\"']", "").trim();
    }

    public void iniciar() { servidor.start(); }

    public static void main(String[] args) throws IOException {
        new PedidosServer().iniciar();
    }
}
