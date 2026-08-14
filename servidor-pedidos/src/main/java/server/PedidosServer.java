package server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import dao.AdminDAO;
import dao.InventarioDAO;
import dao.PedidosDAO;
import dao.ProductoDAO;
import dao.RecetaDAO;
import dao.RecetaItem;
import server.EstadoWeb;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Exchanger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import model.Producto;

public class PedidosServer {

    private final PedidosDAO pedidosDAO = new PedidosDAO();
    private final RecetaDAO recetaDAO = new RecetaDAO();
    private final InventarioDAO invDAO = new InventarioDAO();
    private final AdminDAO adminDAO = new AdminDAO();
    private final List<Map<String, Object>> notificaciones = new ArrayList<>();

    private final Object pedidoLock = new Object();

    private static final int PUERTO = System.getenv("PORT") != null
            ? Integer.parseInt(System.getenv("PORT")) : 8888;

    public static final String ADMIN_USER = System.getenv("ADMIN_USER") != null
            ? System.getenv("ADMIN_USER") : "admin";
    public static final String ADMIN_PASS = requireEnv("ADMIN_PASS");

    private static String requireEnv(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required env var: " + key);
        }
        return value;
    }

    private HttpServer servidor;

    // ── Kitchen Display ──────────────────────────────────────────────────
    private static final String API_KEY = System.getenv("API_KEY") != null
            ? System.getenv("API_KEY") : "delicias-kds-2026";

    private static final Pattern PATRON_DETALLE = Pattern.compile("(\\d+)?\\s*(.+)");
    private static final Pattern PATRON_DETALLE_NUEVO = Pattern.compile("(.+):\\s*(\\d+)\\s*uds\\.?");

    private static String cocinaHtmlCache = null;
    private static long cocinaHtmlTimestamp = 0;

    // ── Rate limiting ────────────────────────────────────────────────────
    private static final long VENTANA_MS = 60_000L;  // 60 segundos entre pedidos
    private static final int MAX_PEDIDOS_HORA = 20;   // maximo 20 pedidos por hora
    private static final long HORA_MS = 60 * 60 * 1000L;
    private static final long BLOQUEO_MS = 30 * 60 * 1000L;

    private final Map<String, Long> ultimoPedidoPorIp = new ConcurrentHashMap<>();
    private final Map<String, Integer> contadorPorIp = new ConcurrentHashMap<>();
    private final Map<String, Long> bloqueadoHasta = new ConcurrentHashMap<>();

    private static class ItemCarrito {

        String nombre;
        String categoria;
        String detalle;
        int cantidad;
    }

    static class ItemPedido {
        String producto;
        int cantidad;
        String categoria;
        ItemPedido(String p, int c, String cat) { this.producto = p; this.cantidad = c; this.categoria = cat; }
    }

    public PedidosServer() throws IOException {

        servidor = HttpServer.create(new InetSocketAddress("0.0.0.0", PUERTO), 0);

        servidor.createContext("/api/pedidos/cliente", exchange -> {
            agregarCorsHeaders(exchange);
            if ("GET".equals(exchange.getRequestMethod())) {
                String query = exchange.getRequestURI().getQuery();
                String telefono = "";
                if (query != null && query.contains("telefono=")) {
                    telefono = query.split("telefono=")[1].split("&")[0];
                }
                List<PedidosDAO.PedidoBD> pedidos = pedidosDAO.cargarPedidosPorTelefono(telefono);
                StringBuilder json = new StringBuilder("[");
                for (int i = 0; i < pedidos.size(); i++) {
                    PedidosDAO.PedidoBD p = pedidos.get(i);
                    json.append("{")
                            .append("\"id\":").append(p.id).append(",")
                            .append("\"numero\":").append(p.numero).append(",")
                            .append("\"cliente\":\"").append(escaparJson(p.cliente)).append("\",")
                            .append("\"telefono\":\"").append(escaparJson(p.telefono)).append("\",")
                            .append("\"detalle\":\"").append(escaparJson(p.detalle)).append("\",")
                            .append("\"total\":").append(p.total).append(",")
                            .append("\"estado\":\"").append(p.estado).append("\",")
                            .append("\"fecha_hora\":\"").append(p.timestamp != null ? p.timestamp : "").append("\"")
                            .append("}");
                    if (i < pedidos.size() - 1) {
                        json.append(",");
                    }
                }
                json.append("]");
                enviarRespuesta(exchange, 200, json.toString());
            }
        });

        servidor.createContext("/api/pedidos/activo", exchange -> {
            agregarCorsHeaders(exchange);
            if ("GET".equals(exchange.getRequestMethod())) {
                String query = exchange.getRequestURI().getQuery();
                String telefono = "";
                if (query != null && query.contains("telefono=")) {
                    telefono = query.split("telefono=")[1].split("&")[0];
                }
                PedidosDAO.PedidoBD p = pedidosDAO.cargarPedidoActivo(telefono);
                if (p != null) {
                    String json = "{"
                            + "\"activo\":true,"
                            + "\"id\":" + p.id + ","
                            + "\"numero\":" + p.numero + ","
                            + "\"cliente\":\"" + escaparJson(p.cliente) + "\","
                            + "\"telefono\":\"" + escaparJson(p.telefono) + "\","
                            + "\"detalle\":\"" + escaparJson(p.detalle) + "\","
                            + "\"total\":" + p.total + ","
                            + "\"estado\":\"" + p.estado + "\","
                            + "\"hora\":\"" + (p.timestamp != null ? p.timestamp : "") + "\""
                            + "}";
                    enviarRespuesta(exchange, 200, json);
                } else {
                    enviarRespuesta(exchange, 200, "{\"activo\":false}");
                }
            }
        });

        servidor.createContext("/api/pedidos/", exchange -> {
            agregarCorsHeaders(exchange);
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            String path = exchange.getRequestURI().getPath();
            // PUT /api/pedidos/{id}/estado
            if ("PUT".equals(exchange.getRequestMethod()) && path.matches("/api/pedidos/\\d+/estado")) {
                try {
                    int id = Integer.parseInt(path.split("/")[3]);
                    String body = readBody(exchange);
                    String nuevoEstado = extraerValor(body, "estado");
                    boolean ok = pedidosDAO.actualizarEstado(id, nuevoEstado);
                    enviarRespuesta(exchange, 200, "{\"exito\":" + ok + "}");
                } catch (Exception e) {
                    enviarRespuesta(exchange, 400, "{\"exito\":false}");
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
                    if (i < pedidos.size() - 1) {
                        json.append(",");
                    }
                }
                json.append("]");
                enviarRespuesta(exchange, 200, json.toString());
            }
        });

        servidor.createContext("/api/pedidos", exchange -> {
            agregarCorsHeaders(exchange);
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            if ("POST".equals(exchange.getRequestMethod())) {
                String body = readBody(exchange);

                synchronized (pedidoLock) {
                    try {
                        String cliente = sanitizar(extraerValor(body, "cliente"));
                        String telefono = sanitizar(extraerValor(body, "telefono"));

                        String errorThrottle = verificarThrottle(exchange, telefono);
                        if (errorThrottle != null) {
                            enviarRespuesta(exchange, 429, errorThrottle);
                            return;
                        }
                        String detalle = construirDetalleDesdeItems(body);
                        if (detalle == null || detalle.isBlank()) {
                            detalle = sanitizar(extraerValor(body, "detalle"));
                        }

                        double total = extraerDouble(body, "total");

                        String tipoPago = extraerValor(body, "tipoPago");
                        if ("-".equals(tipoPago) || tipoPago.isBlank()) {
                            tipoPago = "EFECTIVO";
                        }

                        if (esPedidoDuplicado(cliente, detalle)) {
                            System.out.println("[PEDIDOS] Duplicado detectado para: " + cliente);
                            enviarRespuesta(exchange, 200,
                                    "{\"exito\":true,\"numero\":0,\"duplicado\":true}");
                            return;
                        }

                        String categorias = extraerCategoriasDeLosItems(body);
                        String franja = calcularFranjaActual(detalle, categorias);

                        if ("FUERA HORARIO".equals(franja)) {
                            enviarRespuesta(exchange, 403,
                                    "{\"exito\":false,\"error\":\"No se aceptan pedidos en este horario.\"}");
                            return;
                        }

                        String fechaEntrega = extraerValor(body, "fecha_entrega");
                        if ("-".equals(fechaEntrega) || fechaEntrega.isBlank()) {
                            fechaEntrega = null;
                        }

                        String categoriasDetalle = construirCategoriasDetalle(body);

                        String origen = extraerValor(body, "origen");
                        if (!"WEB".equalsIgnoreCase(origen)
                                && !"LOCAL".equalsIgnoreCase(origen)
                                && !"APP".equalsIgnoreCase(origen)) {
                            origen = "WEB";
                        } else {
                            origen = origen.toUpperCase();
                        }

                        int[] resultado = pedidosDAO.guardarPedidoAutoNumero(
                                cliente, telefono, detalle, total, franja, origen,
                                fechaEntrega, categoriasDetalle);

                        int id = resultado[0];
                        int numeroPedido = resultado[1];

                        if (id > 0) {
                            descontarInventarioDesdeItems(body);
                        }

                        enviarRespuesta(exchange, 200, "{"
                                + "\"exito\":true,"
                                + "\"id\":" + id + ","
                                + "\"numero\":" + numeroPedido + "}");

                    } catch (Exception e) {
                        System.err.println("[PedidosServer] " + e.getMessage());
                        enviarRespuesta(exchange, 400, "{\"exito\":false}");
                    }
                }
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
                    System.err.println("[PedidosServer] " + e.getMessage());
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
                    String body = readBody(exchange);
                    String nombre = sanitizar(extraerValor(body, "nombre"));
                    String email = sanitizar(extraerValor(body, "email"));
                    enviarRespuesta(exchange, 200, "{\"exito\":true}");
                } catch (Exception e) {
                    System.err.println("[PedidosServer] " + e.getMessage());
                    enviarRespuesta(exchange, 400, "{\"exito\":false}");
                }
            }
        });

        servidor.createContext("/api/admin/stats", exchange -> {
            agregarCorsHeaders(exchange);
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            if (!autenticado(exchange.getRequestHeaders().getFirst("Authorization"))) {
                exchange.sendResponseHeaders(401, -1);
                return;
            }
            try {
                enviarRespuesta(exchange, 200, mapToJson(adminDAO.obtenerEstadisticas()));
            } catch (Exception e) {
                System.err.println("[PedidosServer] " + e.getMessage());
                enviarRespuesta(exchange, 500, "{}");
            }
        });

        servidor.createContext("/api/admin/logs", exchange -> {
            agregarCorsHeaders(exchange);
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            if (!autenticado(exchange.getRequestHeaders().getFirst("Authorization"))) {
                exchange.sendResponseHeaders(401, -1);
                return;
            }
            try {
                String query = exchange.getRequestURI().getQuery();
                int limite = 200;
                if (query != null && query.contains("limite=")) {
                    try {
                        limite = Integer.parseInt(query.split("limite=")[1].split("&")[0]);
                    } catch (Exception ignored) {
                    }
                }
                enviarRespuesta(exchange, 200, listOfMapsToJson(adminDAO.obtenerLogs(limite)));
            } catch (Exception e) {
                System.err.println("[PedidosServer] " + e.getMessage());
                enviarRespuesta(exchange, 500, "[]");
            }
        });

        servidor.createContext("/api/admin/ips", exchange -> {
            agregarCorsHeaders(exchange);
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            if (!autenticado(exchange.getRequestHeaders().getFirst("Authorization"))) {
                exchange.sendResponseHeaders(401, -1);
                return;
            }
            try {
                if ("GET".equals(exchange.getRequestMethod())) {
                    Map<String, Object> resp = new LinkedHashMap<>();
                    resp.put("bloqueadas", adminDAO.obtenerIPsBloqueadas());
                    resp.put("top_ips", adminDAO.obtenerTopIPs(20));
                    enviarRespuesta(exchange, 200, mapToJson(resp));
                } else if ("POST".equals(exchange.getRequestMethod())) {
                    String body = readBody(exchange);
                    String ip = extraerValor(body, "ip");
                    String accion = extraerValor(body, "accion");
                    String razon = extraerValor(body, "razon");
                    if ("bloquear".equals(accion)) {
                        adminDAO.bloquearIPManual(ip, razon);
                    } else if ("desbloquear".equals(accion)) {
                        adminDAO.desbloquearIP(ip);
                    }
                    enviarRespuesta(exchange, 200, "{\"ok\":true}");
                }
            } catch (Exception e) {
                System.err.println("[PedidosServer] " + e.getMessage());
                enviarRespuesta(exchange, 500, "{\"ok\":false}");
            }
        });

        servidor.createContext("/api/admin/usuarios", exchange -> {
            agregarCorsHeaders(exchange);
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            if (!autenticado(exchange.getRequestHeaders().getFirst("Authorization"))) {
                exchange.sendResponseHeaders(401, -1);
                return;
            }
            try {
                enviarRespuesta(exchange, 200, listOfMapsToJson(adminDAO.obtenerUsuarios(500)));
            } catch (Exception e) {
                System.err.println("[PedidosServer] " + e.getMessage());
                enviarRespuesta(exchange, 500, "[]");
            }
        });

        servidor.createContext("/api/estado", exchange -> {
            agregarCorsHeaders(exchange);
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            if ("GET".equals(exchange.getRequestMethod())) {
                enviarRespuesta(exchange, 200, "{\"abierta\":" + EstadoWeb.abierta() + "}");
                return;
            }
            if ("POST".equals(exchange.getRequestMethod())) {
                if (!autenticado(exchange.getRequestHeaders().getFirst("Authorization"))) {
                    exchange.sendResponseHeaders(401, -1);
                    return;
                }
                String body = readBody(exchange);
                String valor = extraerValor(body, "abierta");
                EstadoWeb.setAbierta(valor.isEmpty() || "-".equals(valor) ? null : "true".equals(valor));
                System.out.println("[ESTADO WEB] → " + (EstadoWeb.abierta() ? "ABIERTA" : "CERRADA"));
                enviarRespuesta(exchange, 200, "{\"ok\":true,\"abierta\":" + EstadoWeb.abierta() + "}");
                return;
            }
            exchange.sendResponseHeaders(405, -1);
        });

        servidor.createContext("/api/horarios", exchange -> {
            agregarCorsHeaders(exchange);
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            if ("GET".equals(exchange.getRequestMethod())) {
                enviarRespuesta(exchange, 200, construirJsonHorarios());
                return;
            }
            exchange.sendResponseHeaders(405, -1);
        });

        servidor.createContext("/api/cocina/pedidos", exchange -> {

            agregarCorsHeaders(exchange);

            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if ("GET".equals(exchange.getRequestMethod())) {
                String key = exchange.getRequestHeaders().getFirst("X-API-Key");
                if (key == null || !key.equals(API_KEY)) {
                    enviarRespuesta(exchange, 401, "{\"error\":\"No autorizado\"}");
                    return;
                }

                try {
                    List<PedidosDAO.PedidoBD> pedidos = pedidosDAO.cargarPedidosPendientesDeHoy();
                    Map<String, String> catMap = cargarMapaProductos();

                    List<PedidosDAO.PedidoBD> activos = new ArrayList<>();
                    for (PedidosDAO.PedidoBD p : pedidos) {
                        if ("PENDIENTE".equals(p.estado)) {
                            activos.add(p);
                        }
                    }

                    StringBuilder json = new StringBuilder("{\"pedidos\":[");
                    Map<String, Map<String, Integer>> totalesGeneral = new LinkedHashMap<>();

                    for (int i = 0; i < activos.size(); i++) {
                        PedidosDAO.PedidoBD p = activos.get(i);
                        String origen = p.origen != null ? p.origen : "WEB";
                        List<ItemPedido> items = parsearDetalle(p.detalle, catMap);
                        Map<String, Map<String, Integer>> totalesOrigen = agruparTotales(items, origen);
                        for (Map.Entry<String, Map<String, Integer>> e : totalesOrigen.entrySet()) {
                            totalesGeneral.putIfAbsent(e.getKey(), new LinkedHashMap<>());
                            for (Map.Entry<String, Integer> ce : e.getValue().entrySet()) {
                                totalesGeneral.get(e.getKey()).merge(ce.getKey(), ce.getValue(), Integer::sum);
                            }
                        }
                        json.append("{\"numero\":\"").append(PedidosDAO.formatearNumero(p.numero, origen))
                            .append("\",\"cliente\":\"").append(escaparJson(p.cliente))
                            .append("\",\"telefono\":\"").append(escaparJson(p.telefono))
                            .append("\",\"hora\":\"").append(obtenerHora(p.timestamp))
                            .append("\",\"origen\":\"").append(origen)
                            .append("\",\"estado\":\"").append(p.estado)
                            .append("\",\"cats\":\"").append(escaparJson(p.categoriasDetalle != null ? p.categoriasDetalle : ""))
                            .append("\",\"items\":[");
                        for (int j = 0; j < items.size(); j++) {
                            ItemPedido it = items.get(j);
                            json.append("{\"producto\":\"").append(escaparJson(it.producto))
                                .append("\",\"cantidad\":").append(it.cantidad)
                                .append(",\"categoria\":\"").append(escaparJson(it.categoria)).append("\"}");
                            if (j < items.size() - 1) json.append(",");
                        }
                        json.append("]}");
                        if (i < activos.size() - 1) json.append(",");
                    }

                    json.append("],\"totales\":{");
                    int oi = 0;
                    for (Map.Entry<String, Map<String, Integer>> e : totalesGeneral.entrySet()) {
                        json.append("\"").append(e.getKey()).append("\":{");
                        int ci = 0;
                        for (Map.Entry<String, Integer> ce : e.getValue().entrySet()) {
                            json.append("\"").append(escaparJson(ce.getKey())).append("\":").append(ce.getValue());
                            if (ci++ < e.getValue().size() - 1) json.append(",");
                        }
                        json.append("}");
                        if (oi++ < totalesGeneral.size() - 1) json.append(",");
                    }
                    json.append("}}");

                    byte[] bb = json.toString().getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
                    exchange.sendResponseHeaders(200, bb.length);
                    exchange.getResponseBody().write(bb);
                    exchange.close();

                } catch (Exception e) {
                    System.err.println("[KDS] Error API: " + e.getMessage());
                    enviarRespuesta(exchange, 500, "{\"error\":\"Error al consultar pedidos\"}");
                }
            }
        });

        servidor.createContext("/cocina", exchange -> {
            if ("GET".equals(exchange.getRequestMethod())) {
                String html = cargarCocinaHtml();
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                byte[] b = html.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, b.length);
                exchange.getResponseBody().write(b);
                exchange.close();
            }
        });

servidor.createContext("/img/", exchange -> {
    if (!"GET".equals(exchange.getRequestMethod())) {
        exchange.sendResponseHeaders(405, -1);
        return;
    }
    String path = exchange.getRequestURI().getPath(); 
    String filename = path.substring("/img/".length());

    if (filename.contains("..") || filename.isBlank()) {
        exchange.sendResponseHeaders(400, -1);
        return;
    }

    try (InputStream is = PedidosServer.class.getResourceAsStream("/static/img/" + filename)) {
        if (is == null) {
            exchange.sendResponseHeaders(404, -1);
            return;
        }
        byte[] bytes = is.readAllBytes();

        String contentType = filename.endsWith(".png") ? "image/png"
                : (filename.endsWith(".jpg") || filename.endsWith(".jpeg")) ? "image/jpeg"
                : "application/octet-stream";

        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", "public, max-age=86400");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
    } finally {
        exchange.close();
    }
});

        // ── Productos rápidos (panes) ──────────────────────────────────────────
        servidor.createContext("/api/productos-rapidos", exchange -> {

            agregarCorsHeaders(exchange);

            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if ("GET".equals(exchange.getRequestMethod())) {
                try {
                    dao.ProductoRapidoDAO dao = new dao.ProductoRapidoDAO();
                    List<Object[]> productos = dao.listar();

                    StringBuilder json = new StringBuilder("[");
                    for (int i = 0; i < productos.size(); i++) {
                        Object[] p = productos.get(i);
                        json.append("{\"id\":").append(p[0])
                            .append(",\"nombre\":\"").append(escaparJson((String)p[1]))
                            .append("\",\"precio\":").append(p[2])
                            .append(",\"categoria\":\"").append(escaparJson((String)p[3])).append("\"}");
                        if (i < productos.size() - 1) json.append(",");
                    }
                    json.append("]");

                    exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
                    byte[] b = json.toString().getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(200, b.length);
                    exchange.getResponseBody().write(b);
                    exchange.close();
                } catch (Exception e) {
                    e.printStackTrace();
                    enviarRespuesta(exchange, 500, "{\"error\":\"Error\"}");
                }
            }
        });

        // ── Notificaciones para app ────────────────────────────────────────────
        servidor.createContext("/api/notificaciones", exchange -> {

            agregarCorsHeaders(exchange);

            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if ("POST".equals(exchange.getRequestMethod())) {
                try {
                    String body = readBody(exchange);
                    String titulo = extraerValor(body, "titulo");
                    String mensaje = extraerValor(body, "mensaje");
                    String icono = extraerValor(body, "icono");
                    String color = extraerValor(body, "color");

                    Map<String, Object> notif = new LinkedHashMap<>();
                    notif.put("id", notificaciones.size() + 1);
                    notif.put("titulo", titulo);
                    notif.put("mensaje", mensaje);
                    notif.put("icono", "-".equals(icono) ? "bell-outline" : icono);
                    notif.put("color", "-".equals(color) ? "#40cee0" : color);
                    java.time.ZoneId chile = java.time.ZoneId.of("America/Santiago");
                    java.time.ZonedDateTime now = java.time.ZonedDateTime.now(chile);
                    notif.put("fecha", now.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")));
                    notif.put("hora", now.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")));
                    notif.put("createdAt", System.currentTimeMillis());
                    notificaciones.add(notif);

                    enviarRespuesta(exchange, 200, "{\"exito\":true,\"id\":" + notif.get("id") + "}");
                } catch (Exception e) {
                    e.printStackTrace();
                    enviarRespuesta(exchange, 400, "{\"exito\":false}");
                }
            }

            if ("GET".equals(exchange.getRequestMethod())) {
                try {
                    int start = Math.max(0, notificaciones.size() - 50);
                    List<Map<String, Object>> recent = notificaciones.subList(start, notificaciones.size());

                    StringBuilder json = new StringBuilder("[");
                    for (int i = 0; i < recent.size(); i++) {
                        Map<String, Object> n = recent.get(i);
                        json.append("{");
                        json.append("\"id\":").append(n.get("id")).append(",");
                        json.append("\"titulo\":\"").append(escaparJson((String)n.get("titulo"))).append("\",");
                        json.append("\"mensaje\":\"").append(escaparJson((String)n.get("mensaje"))).append("\",");
                        json.append("\"icono\":\"").append(escaparJson((String)n.get("icono"))).append("\",");
                        json.append("\"color\":\"").append(escaparJson((String)n.get("color"))).append("\",");
                        json.append("\"fecha\":\"").append(escaparJson((String)n.get("fecha"))).append("\",");
                        json.append("\"hora\":\"").append(escaparJson((String)n.get("hora"))).append("\",");
                        json.append("\"createdAt\":").append(n.get("createdAt"));
                        json.append("}");
                        if (i < recent.size() - 1) json.append(",");
                    }
                    json.append("]");

                    exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
                    byte[] b = json.toString().getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(200, b.length);
                    exchange.getResponseBody().write(b);
                    exchange.close();
                } catch (Exception e) {
                    e.printStackTrace();
                    enviarRespuesta(exchange, 500, "{\"error\":\"Error\"}");
                }
            }
        });

        servidor.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(10));
        System.out.println("Servidor OK puerto " + PUERTO);
    }

    private boolean autenticado(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Basic ")) {
            return false;
        }
        try {
            String decoded = new String(java.util.Base64.getDecoder()
                    .decode(authHeader.substring(6)));
            String[] partes = decoded.split(":", 2);
            return partes.length == 2
                    && ADMIN_USER.equals(partes[0])
                    && ADMIN_PASS.equals(partes[1]);
        } catch (Exception e) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private String mapToJson(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (!first) {
                sb.append(",");
            }
            first = false;
            sb.append("\"").append(e.getKey()).append("\":");
            Object v = e.getValue();
            if (v == null) {
                sb.append("null");
            } else if (v instanceof Number) {
                sb.append(v);
            } else if (v instanceof Boolean) {
                sb.append(v);
            } else if (v instanceof List) {
                sb.append(listOfMapsToJson((List<Map<String, Object>>) v));
            } else {
                sb.append("\"").append(escaparJson(v.toString())).append("\"");
            }
        }
        return sb.append("}").toString();
    }

    private String listOfMapsToJson(List<Map<String, Object>> lista) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < lista.size(); i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append(mapToJson(lista.get(i)));
        }
        return sb.append("]").toString();
    }

    // â”€â”€ Throttling â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    private String obtenerIp(HttpExchange exchange) {
        String forwarded = exchange.getRequestHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return exchange.getRemoteAddress().getAddress().getHostAddress();
    }

    private String verificarThrottle(HttpExchange exchange, String telefono) {
        // Usar teléfono como identificador (si está disponible), si no IP
        String id = (telefono != null && !telefono.isBlank() && !"-".equals(telefono))
                ? "tel:" + telefono
                : "ip:" + obtenerIp(exchange);
        long ahora = System.currentTimeMillis();

        System.out.println("[THROTTLE] Identificador: " + id);

        Long bloqueado = bloqueadoHasta.get(id);
        if (bloqueado != null && ahora < bloqueado) {
            long min = (bloqueado - ahora) / 60_000 + 1;
            return "{\"exito\":false,\"error\":\"Demasiados intentos. Reintente en " + min + " minutos.\"}";
        } else if (bloqueado != null) {
            bloqueadoHasta.remove(id);
            contadorPorIp.remove(id);
            ultimoPedidoPorIp.remove(id);
        }

        Long ultimo = ultimoPedidoPorIp.get(id);
        if (ultimo != null && (ahora - ultimo) < VENTANA_MS) {
            long segs = (VENTANA_MS - (ahora - ultimo)) / 1000 + 1;
            System.out.println("[THROTTLE] Bloqueado por ventana: " + segs + "s restantes. ID: " + id + ", ultimo: " + ultimo + ", ahora: " + ahora);
            return "{\"exito\":false,\"error\":\"Espere " + segs + " segundos antes de enviar otro pedido.\"}";
        }

        int contador = contadorPorIp.getOrDefault(id, 0);
        if (ultimo != null && (ahora - ultimo) >= HORA_MS) {
            contador = 0;
            contadorPorIp.put(id, 0);
        }

        if (contador >= MAX_PEDIDOS_HORA) {
            bloqueadoHasta.put(id, ahora + BLOQUEO_MS);
            return "{\"exito\":false,\"error\":\"Demasiados pedidos. Cuenta bloqueada durante 30 minutos.\"}";
        }

        ultimoPedidoPorIp.put(id, ahora);
        contadorPorIp.put(id, contador + 1);
        return null;
    }

    // â”€â”€ DetecciÃ³n de duplicados â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    private boolean esPedidoDuplicado(String cliente, String detalle) {
        String sql = "SELECT COUNT(*) FROM pedidos WHERE cliente=? AND detalle=? "
                + "AND fecha_hora::timestamptz > NOW() - INTERVAL '15 seconds' AND origen='WEB'";
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
            if (conn != null) {
                dao.Conexion.devolver(conn);
            }
        }
    }

    // â”€â”€ CategorÃ­as e inventario â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    private String construirCategoriasDetalle(String json) {
        List<ItemCarrito> items = extraerItems(json);
        Map<String, Integer> conteo = new LinkedHashMap<>();
        for (ItemCarrito item : items) {
            conteo.merge(normalizarCategoria(item.categoria), item.cantidad, Integer::sum);
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> e : conteo.entrySet()) {
            if (sb.length() > 0) {
                sb.append(",");
            }
            sb.append(e.getKey()).append(":").append(e.getValue());
        }
        return sb.toString();
    }

    private void descontarInventarioDesdeItems(String json) {
        for (ItemCarrito item : extraerItems(json)) {
            try {
                String cat = normalizarCategoria(item.categoria);
                List<RecetaItem> receta = (cat.equals("rapido") || cat.isEmpty())
                        ? recetaDAO.obtenerPorNombre(item.nombre)
                        : recetaDAO.obtenerPorProducto(0, cat);
                for (RecetaItem r : receta) {
                    double g = r.getCantidadG() * item.cantidad;
                    invDAO.descontarStock(r.getIdIngrediente(), g);
                }
            } catch (Exception e) {
                System.out.println("[INV] Error descontando " + item.nombre + ": " + e.getMessage());
            }
        }
    }

    private String normalizarCategoria(String cat) {
        if (cat == null) {
            return "rapido";
        }
        cat = cat.toLowerCase().trim();
        if (cat.endsWith("s") && !cat.equals("rapido")) {
            cat = cat.substring(0, cat.length() - 1);
        }
        cat = cat.replace("Ã¡", "a").replace("Ã©", "e").replace("Ã­", "i")
                .replace("Ã³", "o").replace("Ãº", "u").replace("Ã±", "n");
        return cat.isEmpty() ? "rapido" : cat;
    }

    private List<ItemCarrito> extraerItems(String json) {
        List<ItemCarrito> lista = new ArrayList<>();
        try {
            int inicio = json.indexOf("\"items\":");
            if (inicio == -1) {
                return lista;
            }
            inicio = json.indexOf("[", inicio);
            int fin = json.indexOf("]", inicio);
            if (inicio == -1 || fin == -1) {
                return lista;
            }
            String itemsStr = json.substring(inicio + 1, fin);
            int i = 0;
            while ((i = itemsStr.indexOf("{", i)) != -1) {
                int cierre = itemsStr.indexOf("}", i);
                if (cierre == -1) {
                    break;
                }
                String obj = itemsStr.substring(i, cierre + 1);
                ItemCarrito item = new ItemCarrito();
                item.nombre = extraerValor(obj, "nombre");
                item.categoria = extraerValor(obj, "categoria");
                item.detalle = extraerValor(obj, "detalle");
                item.cantidad = (int) extraerDouble(obj, "cantidad");
                if (item.nombre != null && !item.nombre.equals("-") && item.cantidad > 0) {
                    lista.add(item);
                }
                i = cierre + 1;
            }
        } catch (Exception e) {
            System.out.println("[INV] Error parseando items: " + e.getMessage());
        }
        return lista;
    }

    // â”€â”€ Utilidades â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    private String readBody(HttpExchange exchange) throws IOException {
        return new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    private String extraerValor(String json, String clave) {
        String patron = "\"" + clave + "\":\"";
        int i = json.indexOf(patron);
        if (i == -1) {
            return "-";
        }
        i += patron.length();
        int f = json.indexOf("\"", i);
        return f == -1 ? "-" : json.substring(i, f);
    }

    private double extraerDouble(String json, String clave) {
        try {
            String patron = "\"" + clave + "\":";
            int i = json.indexOf(patron);
            if (i == -1) {
                return 0;
            }
            i += patron.length();
            int f = json.indexOf(",", i);
            if (f == -1) {
                f = json.indexOf("}", i);
            }
            return Math.max(0, Double.parseDouble(json.substring(i, f).trim()));
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
            if (f != -1) {
                cats.append(json, i, f).append(" ");
            }
        }
        return cats.toString().toLowerCase();
    }

    private static final int PANADERIA_APERTURA = 12, PANADERIA_CIERRE = 15;
    private static final int PASTELERIA_APERTURA = 12, PASTELERIA_CIERRE = 22;
    private static final int EMPANADA_APERTURA = 18, EMPANADA_CIERRE = 22;
    private static final int GENERAL_APERTURA = 18, GENERAL_CIERRE = 22;

    private String construirJsonHorarios() {
        boolean webAbierta = EstadoWeb.abierta();
        int hora = java.time.LocalTime.now(java.time.ZoneId.of("America/Santiago")).getHour();

        boolean panaderiaOk = webAbierta && hora >= PANADERIA_APERTURA && hora < PANADERIA_CIERRE;
        boolean pasteleriaOk = webAbierta && hora >= PASTELERIA_APERTURA && hora < PASTELERIA_CIERRE;
        boolean empanadaOk = webAbierta && hora >= EMPANADA_APERTURA && hora < EMPANADA_CIERRE;
        boolean generalOk = webAbierta && hora >= GENERAL_APERTURA && hora < GENERAL_CIERRE;

        return "{"
                + "\"abierta\":" + webAbierta + ","
                + "\"categorias\":{"
                + "\"panaderia\":{\"disponible\":" + panaderiaOk + ",\"desde\":\"" + String.format("%02d:00", PANADERIA_APERTURA) + "\",\"hasta\":\"" + String.format("%02d:00", PANADERIA_CIERRE) + "\"},"
                + "\"pasteleria\":{\"disponible\":" + pasteleriaOk + ",\"desde\":\"" + String.format("%02d:00", PASTELERIA_APERTURA) + "\",\"hasta\":\"" + String.format("%02d:00", PASTELERIA_CIERRE) + "\"},"
                + "\"empanada\":{\"disponible\":" + empanadaOk + ",\"desde\":\"" + String.format("%02d:00", EMPANADA_APERTURA) + "\",\"hasta\":\"" + String.format("%02d:00", EMPANADA_CIERRE) + "\"},"
                + "\"general\":{\"disponible\":" + generalOk + ",\"desde\":\"" + String.format("%02d:00", GENERAL_APERTURA) + "\",\"hasta\":\"" + String.format("%02d:00", GENERAL_CIERRE) + "\"}"
                + "}"
                + "}";
    }

    private String normalizar(String s) {
        if (s == null) return "";
        return java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase();
    }

    private String calcularFranjaActual(String detalle, String categorias) {
        if (!EstadoWeb.abierta()) {
            return "FUERA HORARIO";
        }

        java.time.LocalTime ahora = java.time.LocalTime.now(java.time.ZoneId.of("America/Santiago"));
        int hora = ahora.getHour(), minuto = ahora.getMinute();
        String d = normalizar(detalle);
        String c = normalizar(categorias);

        boolean esPanaderia = c.contains("panaderia")
                || d.contains("panaderia")
                || d.contains("hallulla") || d.contains("marraqueta")
                || d.contains("dobladita") || d.contains("pan amasado")
                || d.contains("coliza") || d.contains("amasado")
                || d.contains("integral") || d.contains("pan ");
        boolean esAnticipado = c.contains("pasteler") || c.contains("reposteri")
                || d.contains("pasteler") || d.contains("reposteri");
        boolean esEmpanada = c.contains("empanada") || d.contains("empanada");

        if (esPanaderia) {
            if (hora < PANADERIA_APERTURA || hora >= PANADERIA_CIERRE) {
                return "FUERA HORARIO";

            }
        } else if (esAnticipado) {
            if (hora < PASTELERIA_APERTURA || hora >= PASTELERIA_CIERRE) {
                return "FUERA HORARIO";

            }
        } else if (esEmpanada) {
            if (hora < EMPANADA_APERTURA || hora >= EMPANADA_CIERRE) {
                return "FUERA HORARIO";

            }
        } else {
            if (hora < GENERAL_APERTURA || hora >= GENERAL_CIERRE) {
                return "FUERA HORARIO";

            }
        }

        int iH, iM, fH, fM;
        if (minuto < 30) {
            iH = hora;
            iM = 0;
            fH = hora;
            fM = 30;
        } else {
            iH = hora;
            iM = 30;
            fH = hora + 1;
            fM = 0;
        }
        return String.format("%02d:%02d - %02d:%02d", iH, iM, fH, fM);
    }

    private String escaparJson(String t) {
        return t == null ? "" : t.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private void agregarCorsHeaders(HttpExchange e) {
        e.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        e.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS");
        e.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
    }

    private void enviarRespuesta(HttpExchange ex, int code, String r) throws IOException {
        byte[] b = r.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        ex.sendResponseHeaders(code, b.length);
        ex.getResponseBody().write(b);
        ex.close();
    }

    private String obtenerHoraExacta() {
        return java.time.LocalTime.now(java.time.ZoneId.of("America/Santiago"))
                .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
    }

    private String sanitizar(String v) {
        if (v == null) return "-";
        if (v.length() > 500) v = v.substring(0, 500);
        return v.replaceAll("[<>\"']", "").trim();
    }

    // ── Kitchen Display: helpers ────────────────────────────────────────────

    private String cargarCocinaHtml() {
        File f = new File("cocina.html");
        if (cocinaHtmlCache == null || f.lastModified() > cocinaHtmlTimestamp) {
            try {
                cocinaHtmlCache = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
                cocinaHtmlTimestamp = f.lastModified();
                System.out.println("[KDS] cocina.html cargado (" + cocinaHtmlCache.length() + " bytes)");
            } catch (IOException e) {
                System.err.println("[KDS] No se pudo leer cocina.html: " + e.getMessage());
                return "<html><body><h1>Error</h1><p>No se pudo cargar cocina.html</p></body></html>";
            }
        }
        return cocinaHtmlCache.replace("__API_KEY__", API_KEY);
    }

    private Map<String, String> cargarMapaProductos() {
        ProductoDAO dao = new ProductoDAO();
        List<Producto> productos = dao.listarTodosConStock();
        Map<String, String> mapa = new HashMap<>();
        for (Producto p : productos) {
            mapa.put(p.getNombre().toLowerCase(), p.getCategoria());
        }
        return mapa;
    }

    static List<ItemPedido> parsearDetalle(String detalle, Map<String, String> catMap) {
        List<ItemPedido> items = new ArrayList<>();
        if (detalle == null || detalle.isBlank()) return items;

        // Detectar separador: pipe (nuevo formato web) o coma (formato viejo POS)
        String[] segmentos;
        if (detalle.contains(" | ")) {
            segmentos = detalle.split(" \\| ");
        } else {
            segmentos = splitRespetandoParentesis(detalle);
        }

        for (String seg : segmentos) {
            seg = seg.trim();
            if (seg.isEmpty()) continue;

            int cantidad = 1;
            String texto;

            // Intentar formato nuevo: "producto: X uds."
            Matcher mNuevo = PATRON_DETALLE_NUEVO.matcher(seg);
            if (mNuevo.matches()) {
                texto = mNuevo.group(1).trim();
                try { cantidad = Integer.parseInt(mNuevo.group(2)); } catch (NumberFormatException e) {}
            } else {
                // Intentar formato viejo: "X producto"
                Matcher m = PATRON_DETALLE.matcher(seg);
                if (!m.matches()) continue;
                if (m.group(1) != null) {
                    try { cantidad = Integer.parseInt(m.group(1)); } catch (NumberFormatException ignored) {}
                }
                texto = m.group(2).trim();
            }

            if (texto.isEmpty()) continue;
            String cat = resolverCategoria(texto, catMap);
            items.add(new ItemPedido(texto, cantidad, cat));
        }
        return items;
    }

    /**
     * Splittea por coma pero respeta contenido dentro de paréntesis.
     * Ejemplo: "1x Arma tu Empanada (-Camarón, Jamón, Tomate)" → ["1x Arma tu Empanada (-Camarón, Jamón, Tomate)"]
     * Ejemplo: "Empanada: 2 uds. | Pan: 1 uds." → ["Empanada: 2 uds.", "Pan: 1 uds."] (pipe siempre separa)
     */
    static String[] splitRespetandoParentesis(String detalle) {
        List<String> partes = new ArrayList<>();
        int nivel = 0;
        int inicio = 0;
        for (int i = 0; i < detalle.length(); i++) {
            char c = detalle.charAt(i);
            if (c == '(') nivel++;
            else if (c == ')') nivel = Math.max(0, nivel - 1);
            else if (c == ',' && nivel == 0) {
                String seg = detalle.substring(inicio, i).trim();
                if (!seg.isEmpty()) partes.add(seg);
                inicio = i + 1;
            }
        }
        String ultima = detalle.substring(inicio).trim();
        if (!ultima.isEmpty()) partes.add(ultima);
        return partes.toArray(new String[0]);
    }

    static Map<String, Map<String, Integer>> agruparTotales(List<ItemPedido> items, String origen) {
        Map<String, Map<String, Integer>> r = new LinkedHashMap<>();
        Map<String, Integer> cats = new LinkedHashMap<>();
        for (ItemPedido it : items) cats.merge(it.categoria, it.cantidad, Integer::sum);
        r.put(origen, cats);
        return r;
    }

    private static String resolverCategoria(String texto, Map<String, String> catMap) {
        if (texto == null || texto.isBlank()) return "Otros";
        String t = texto.toLowerCase()
            .replaceAll("\\b(de|del|la|las|los|lo|con|y|e|un|una|unas|unos|docenas?|unidades?|docena|unidad)\\b", " ")
            .replaceAll("\\s+", " ").trim();
        t = t.replaceAll("(?<=\\w)s\\b", "");
        String mejor = null; int mejorLen = 0;
        for (Map.Entry<String, String> e : catMap.entrySet()) {
            String pn = e.getKey().toLowerCase().replaceAll("\\s+", " ").trim().replaceAll("(?<=\\w)s\\b", "");
            if (t.contains(pn) || pn.contains(t)) {
                if (pn.length() > mejorLen) { mejorLen = pn.length(); mejor = e.getValue(); }
            }
        }
        return mejor != null ? mejor : "Otros";
    }

    private static String obtenerHora(String ts) {
        if (ts == null || ts.length() < 16) return "--:--";
        try { return ts.substring(11, 16); } catch (Exception e) { return "--:--"; }
    }

    public void iniciar() {
        new dao.ProductoRapidoDAO().limpiarDuplicados();
        servidor.start();
    }

    private String construirDetalleDesdeItems(String json) {
        List<ItemCarrito> items = extraerItems(json);
        if (items.isEmpty()) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        for (ItemCarrito item : items) {
            if (sb.length() > 0) {
                sb.append(" | ");
            }
            String prefijo = construirPrefijo(item.categoria, item.nombre);

            String nombreCompleto = prefijo.isBlank()
                    ? item.nombre
                    : prefijo + " " + item.nombre;
            sb.append(item.cantidad).append("x ").append(nombreCompleto);
            if (item.detalle != null && !item.detalle.isBlank() && !item.detalle.equals("-")) {
                sb.append(" (-").append(item.detalle).append(")");
            }
        }
        return sb.toString();
    }

    private String construirPrefijo(String categoria, String nombre) {
        if (categoria == null || categoria.isBlank()) {
            return "";
        }

        String cat = categoria.toLowerCase()
                .replace("Ã¡", "a").replace("Ã©", "e").replace("Ã­", "i")
                .replace("Ã³", "o").replace("Ãº", "u").replace("Ã±", "n").trim();

        String nombreLower = nombre != null ? nombre.toLowerCase() : "";

        if (cat.contains("empanada")) {
            return nombreLower.startsWith("empanada") ? "" : "Empanada";
        }
        if (cat.contains("sopaipilla")) {
            return nombreLower.startsWith("sopaipilla") ? "" : "Sopaipilla";
        }
        return "";
    }

    public static void main(String[] args) throws IOException {
        new PedidosServer().iniciar();
    }
}
