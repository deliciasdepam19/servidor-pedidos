package server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dao.AdminDAO;
import dao.VentaDAO;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class PedidosServer {

    private final VentaDAO ventaDAO = new VentaDAO();
    private final AdminDAO adminDAO = new AdminDAO();

    private static final String ADMIN_USER = System.getenv("ADMIN_USER");
    private static final String ADMIN_PASS = System.getenv("ADMIN_PASS");

    static {
    if (ADMIN_USER == null || ADMIN_PASS == null) {
        throw new RuntimeException(
            "ERROR: Variables de entorno ADMIN_USER y ADMIN_PASS no configuradas"
        );
    }
}

    private static final int PUERTO = System.getenv("PORT") != null
            ? Integer.parseInt(System.getenv("PORT"))
            : 8888;

    private HttpServer servidor;
    private List<PedidoListener> listeners       = new CopyOnWriteArrayList<>();
    private List<Pedido>         historicoPedidos = new CopyOnWriteArrayList<>();
    private final Map<String, Long>    ultimoPedidoPorIp = new ConcurrentHashMap<>();
    private final Map<String, Integer> contadorPorIp     = new ConcurrentHashMap<>();

    public static class Pedido {
        public int    numero;
        public String cliente;
        public String telefono;
        public String detalle;
        public double total;
        public String timestamp;

        public Pedido(int numero, String cliente, String telefono, String detalle, double total) {
            this.numero    = numero;
            this.cliente   = cliente;
            this.telefono  = telefono;
            this.detalle   = detalle;
            this.total     = total;
            this.timestamp = LocalDateTime.now(ZoneId.of("America/Santiago"))
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }
    }

    @FunctionalInterface
    public interface PedidoListener {
        void onNuevoPedido(Pedido pedido);
    }

    public PedidosServer() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("0.0.0.0", PUERTO), 0);

        servidor.createContext("/api/pedidos", exchange -> {
            agregarCorsHeaders(exchange);
            if ("OPTIONS".equals(exchange.getRequestMethod())) { exchange.sendResponseHeaders(204, -1); return; }

            if ("POST".equals(exchange.getRequestMethod())) {
                String ip = exchange.getRemoteAddress().getAddress().getHostAddress();
                try {
                    String body = readBody(exchange);

                    long ahoraMs = System.currentTimeMillis();
                    Long  ultimo = ultimoPedidoPorIp.get(ip);
                    int contador = contadorPorIp.getOrDefault(ip, 0);

                    if (ultimo != null && (ahoraMs - ultimo) < 600_000 && contador >= 5) {
                        adminDAO.registrarLog(ip, "POST", "/api/pedidos", 429,
                                exchange.getRequestHeaders().getFirst("User-Agent"), null);
                        adminDAO.bloquearIP(ip, "Rate limit: 5 pedidos en 10 min");
                        enviarRespuesta(exchange, 429,
                                "{\"exito\":false,\"error\":\"Demasiados pedidos. Intenta en unos minutos.\"}");
                        return;
                    }
                    if (ultimo == null || (ahoraMs - ultimo) >= 600_000) {
                        contadorPorIp.put(ip, 1);
                    } else {
                        contadorPorIp.put(ip, contador + 1);
                    }
                    ultimoPedidoPorIp.put(ip, ahoraMs);

                    if (adminDAO.estaBloqueada(ip)) {
                        adminDAO.registrarLog(ip, "POST", "/api/pedidos", 403,
                                exchange.getRequestHeaders().getFirst("User-Agent"), null);
                        enviarRespuesta(exchange, 403,
                                "{\"exito\":false,\"error\":\"Acceso denegado.\"}");
                        return;
                    }

                    String cliente  = sanitizar(extraerValor(body, "cliente"));
                    String telefono = sanitizar(extraerValor(body, "telefono"));
                    String detalle  = sanitizar(extraerValor(body, "detalle"));
                    double total    = extraerDouble(body, "total");
                    String correo   = sanitizar(extraerValor(body, "correo"));
                    String nombre   = sanitizar(extraerValor(body, "nombre"));

                    String tipoPago = extraerValor(body, "tipoPago");
                    if ("-".equals(tipoPago) || tipoPago.isBlank()) tipoPago = "EFECTIVO";

                    if (!"-".equals(correo)) {
                        adminDAO.registrarOActualizarUsuario(correo, nombre, ip);
                    }

                    LocalDateTime ahora = LocalDateTime.now(ZoneId.of("America/Santiago"));
                    int hora = ahora.getHour();

                    String  bodyLower   = body.toLowerCase();
                    boolean esPanaderia = bodyLower.contains("panaderia") || bodyLower.contains("panadería");

                    System.out.println(">>> HORA SANTIAGO: " + hora
                            + " | ES_PANADERIA: " + esPanaderia + " | DETALLE: " + detalle);

                    boolean fueraHorario;
                    String  mensajeHorario;
                    if (esPanaderia) {
                        fueraHorario   = hora < 12 || hora >= 18;
                        mensajeHorario = "Los pedidos de Panadería se reciben entre las 12:00 y las 18:00 hrs.";
                    } else {
                        fueraHorario   = hora < 18 || hora >= 22;
                        mensajeHorario = "Los pedidos se reciben entre las 18:00 hrs y las 22:00 hrs.";
                    }

                    if (fueraHorario) {
                        System.err.println("Pedido rechazado por horario (" + hora + ":xx) - esPanaderia=" + esPanaderia);
                        adminDAO.registrarLog(ip, "POST", "/api/pedidos", 403,
                                exchange.getRequestHeaders().getFirst("User-Agent"), correo);
                        enviarRespuesta(exchange, 403,
                                "{\"exito\":false,\"error\":\"" + mensajeHorario + "\"}");
                        return;
                    }

System.out.println("Pedido recibido desde IP: " + ip);

int numeroPedido = ventaDAO.obtenerSiguienteNumeroPedido();

Pedido pedido = new Pedido(numeroPedido, cliente, telefono, detalle, total);
historicoPedidos.add(pedido);

                    StockDescontador.descontarDesdeDetalle(detalle);
                    registrarVentaDesdeWeb(body, tipoPago, cliente);

                    for (PedidoListener listener : listeners) listener.onNuevoPedido(pedido);

                    adminDAO.registrarLog(ip, "POST", "/api/pedidos", 200,
                            exchange.getRequestHeaders().getFirst("User-Agent"), correo);

                   String numeroFormateado = String.format("%03d", numeroPedido);

enviarRespuesta(exchange, 200,
        "{\"exito\":true,\"mensaje\":\"Pedido recibido\",\"numero\":\"" + numeroFormateado + "\"}");

                } catch (Exception e) {
                    e.printStackTrace();
                    adminDAO.registrarLog(ip, "POST", "/api/pedidos", 400,
                            exchange.getRequestHeaders().getFirst("User-Agent"), null);
                    enviarRespuesta(exchange, 400,
                            "{\"exito\":false,\"error\":\"" + e.getMessage() + "\"}");
                }
            } else {
                enviarRespuesta(exchange, 405, "{\"error\":\"Método no permitido\"}");
            }
        });

        servidor.createContext("/api/pedidos/historico", exchange -> {
            agregarCorsHeaders(exchange);
            if ("OPTIONS".equals(exchange.getRequestMethod())) { exchange.sendResponseHeaders(204, -1); return; }

            if ("GET".equals(exchange.getRequestMethod())) {
                StringBuilder json = new StringBuilder("[");
                for (int i = 0; i < historicoPedidos.size(); i++) {
                    Pedido p = historicoPedidos.get(i);
                    json.append("{")
                            .append("\"numero\":").append(p.numero).append(",")
                            .append("\"cliente\":\"").append(escaparJson(p.cliente)).append("\",")
                            .append("\"telefono\":\"").append(escaparJson(p.telefono)).append("\",")
                            .append("\"detalle\":\"").append(escaparJson(p.detalle)).append("\",")
                            .append("\"total\":").append(p.total).append(",")
                            .append("\"timestamp\":\"").append(p.timestamp).append("\"")
                            .append("}");
                    if (i < historicoPedidos.size() - 1) json.append(",");
                }
                json.append("]");
                enviarRespuesta(exchange, 200, json.toString());
            } else {
                enviarRespuesta(exchange, 405, "{\"error\":\"Método no permitido\"}");
            }
        });

        servidor.createContext("/api/pedidos/eliminar", exchange -> {
            agregarCorsHeaders(exchange);
            if ("OPTIONS".equals(exchange.getRequestMethod())) { exchange.sendResponseHeaders(204, -1); return; }

            if ("DELETE".equals(exchange.getRequestMethod())) {
                try {
                    String body   = readBody(exchange);
                    int    numero = (int) extraerDouble(body, "numero");
                    boolean eliminado = historicoPedidos.removeIf(p -> p.numero == numero);
                    if (eliminado) {
                        System.out.println("Pedido #" + numero + " eliminado del servidor.");
                        enviarRespuesta(exchange, 200,
                                "{\"exito\":true,\"mensaje\":\"Pedido eliminado\",\"numero\":" + numero + "}");
                    } else {
                        enviarRespuesta(exchange, 404,
                                "{\"exito\":false,\"error\":\"Pedido no encontrado\"}");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    enviarRespuesta(exchange, 400,
                            "{\"exito\":false,\"error\":\"" + e.getMessage() + "\"}");
                }
            } else {
                enviarRespuesta(exchange, 405, "{\"error\":\"Método no permitido\"}");
            }
        });

        servidor.createContext("/api/pedidos/limpiar", exchange -> {
            agregarCorsHeaders(exchange);
            if ("OPTIONS".equals(exchange.getRequestMethod())) { exchange.sendResponseHeaders(204, -1); return; }

            if ("DELETE".equals(exchange.getRequestMethod())) {
                int cantidad = historicoPedidos.size();
                historicoPedidos.clear();
                System.out.println("Se eliminaron " + cantidad + " pedidos del servidor.");
                enviarRespuesta(exchange, 200,
                        "{\"exito\":true,\"mensaje\":\"Todos los pedidos eliminados\",\"cantidad\":" + cantidad + "}");
            } else {
                enviarRespuesta(exchange, 405, "{\"error\":\"Método no permitido\"}");
            }
        });

        servidor.createContext("/api/stock", exchange -> {
            agregarCorsHeaders(exchange);
            if ("OPTIONS".equals(exchange.getRequestMethod())) { exchange.sendResponseHeaders(204, -1); return; }

            if ("GET".equals(exchange.getRequestMethod())) {
                dao.ProductoDAO pDao = new dao.ProductoDAO();
                StringBuilder json = new StringBuilder("{");
                for (String cat : new String[]{"empanadas", "churros", "sopaipillas"}) {
                    java.util.List<model.Producto> lista = pDao.listarPorCategoria(cat);
                    for (model.Producto p : lista) {
                        String key = p.getNombre().replaceAll("[^a-zA-Z0-9]", "_").toLowerCase();
                        json.append("\"").append(key).append("\":").append(p.getStock()).append(",");
                    }
                }
                if (json.charAt(json.length() - 1) == ',') json.deleteCharAt(json.length() - 1);
                json.append("}");
                enviarRespuesta(exchange, 200, json.toString());
            } else {
                enviarRespuesta(exchange, 405, "{\"error\":\"Método no permitido\"}");
            }
        });

        servidor.createContext("/", exchange -> {
            agregarCorsHeaders(exchange);
            if ("OPTIONS".equals(exchange.getRequestMethod())) { exchange.sendResponseHeaders(204, -1); return; }
            enviarRespuesta(exchange, 200, "{\"status\":\"ok\",\"puerto\":" + PUERTO + "}");
        });

servidor.createContext("/api/admin/stats", exchange -> {
    agregarCorsHeaders(exchange);
    if ("OPTIONS".equals(exchange.getRequestMethod())) { exchange.sendResponseHeaders(204, -1); return; }
    if (!autenticarAdmin(exchange)) { requerirAuth(exchange); return; }

    registrarAcceso(exchange, "/api/admin/stats", 200);

    enviarRespuesta(exchange, 200, toJson(adminDAO.obtenerEstadisticas()));
});
       servidor.createContext("/api/admin/logs", exchange -> {
    agregarCorsHeaders(exchange);
    if ("OPTIONS".equals(exchange.getRequestMethod())) { exchange.sendResponseHeaders(204, -1); return; }
    if (!autenticarAdmin(exchange)) { requerirAuth(exchange); return; }

    String query  = exchange.getRequestURI().getQuery();
    int    limite = 200;

    if (query != null && query.contains("limite=")) {
        try { 
            limite = Integer.parseInt(query.replaceAll(".*limite=(\\d+).*", "$1")); 
        } catch (Exception ignored) {}
    }

    registrarAcceso(exchange, "/api/admin/logs", 200);

    enviarRespuesta(exchange, 200, toJson(adminDAO.obtenerLogs(limite)));
});

        servidor.createContext("/api/admin/ips", exchange -> {
    agregarCorsHeaders(exchange);
    if ("OPTIONS".equals(exchange.getRequestMethod())) { exchange.sendResponseHeaders(204, -1); return; }
    if (!autenticarAdmin(exchange)) { requerirAuth(exchange); return; }

    if ("GET".equals(exchange.getRequestMethod())) {

        registrarAcceso(exchange, "/api/admin/ips", 200);

        java.util.Map<String, Object> resp = new java.util.LinkedHashMap<>();
        resp.put("bloqueadas", adminDAO.obtenerIPsBloqueadas());
        resp.put("top_ips",    adminDAO.obtenerTopIPs(30));

        enviarRespuesta(exchange, 200, toJson(resp));

    } else if ("POST".equals(exchange.getRequestMethod())) {

        String body     = readBody(exchange);
        String ipTarget = extraerValor(body, "ip");
        String accion   = extraerValor(body, "accion");
        String razon    = extraerValor(body, "razon");

        if ("bloquear".equals(accion)) {
            adminDAO.bloquearIPManual(ipTarget, razon);
            enviarRespuesta(exchange, 200, "{\"ok\":true,\"accion\":\"bloqueada\"}");
        } else if ("desbloquear".equals(accion)) {
            adminDAO.desbloquearIP(ipTarget);
            enviarRespuesta(exchange, 200, "{\"ok\":true,\"accion\":\"desbloqueada\"}");
        } else {
            enviarRespuesta(exchange, 400, "{\"error\":\"accion invalida\"}");
        }
    } else {
        enviarRespuesta(exchange, 405, "{\"error\":\"Método no permitido\"}");
    }
});

        servidor.createContext("/api/admin/usuarios", exchange -> {
    agregarCorsHeaders(exchange);
    if ("OPTIONS".equals(exchange.getRequestMethod())) { exchange.sendResponseHeaders(204, -1); return; }
    if (!autenticarAdmin(exchange)) { requerirAuth(exchange); return; }

    registrarAcceso(exchange, "/api/admin/usuarios", 200);

    enviarRespuesta(exchange, 200, toJson(adminDAO.obtenerUsuarios(500)));
});

    private boolean autenticarAdmin(HttpExchange exchange) {
        String auth = exchange.getRequestHeaders().getFirst("Authorization");
        if (auth == null || !auth.startsWith("Basic ")) return false;
        String decoded = new String(
                java.util.Base64.getDecoder().decode(auth.substring(6)),
                StandardCharsets.UTF_8);
        String[] partes = decoded.split(":", 2);
        return partes.length == 2 && ADMIN_USER.equals(partes[0]) && ADMIN_PASS.equals(partes[1]);
    }

    private void requerirAuth(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("WWW-Authenticate", "Basic realm=\"Admin Panel\"");
        enviarRespuesta(exchange, 401, "{\"error\":\"No autorizado\"}");
    }

    private String toJson(Object obj) {
        if (obj == null) return "null";
        if (obj instanceof Boolean || obj instanceof Number) return obj.toString();
        if (obj instanceof String s)
            return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"")
                           .replace("\n", "\\n").replace("\r", "\\r") + "\"";
        if (obj instanceof java.util.List<?> list) {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(toJson(list.get(i)));
            }
            return sb.append("]").toString();
        }
        if (obj instanceof java.util.Map<?, ?> map) {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (var entry : map.entrySet()) {
                if (!first) sb.append(",");
                sb.append("\"").append(entry.getKey()).append("\":").append(toJson(entry.getValue()));
                first = false;
            }
            return sb.append("}").toString();
        }
        return "\"" + obj + "\"";
    }

    private void registrarVentaDesdeWeb(String body, String tipoPago, String cliente) {
        if (registrarDesdeItems(body, tipoPago, cliente)) return;
        String detalle     = extraerValor(body, "detalle");
        double totalPedido = extraerDouble(body, "total");
        registrarDesdeDetalle(detalle, totalPedido, tipoPago, cliente);
    }

    private boolean registrarDesdeItems(String body, String tipoPago, String cliente) {
        int itemsIdx = body.indexOf("\"items\":");
        if (itemsIdx == -1) return false;
        int arrStart = body.indexOf("[", itemsIdx);
        int arrEnd   = body.lastIndexOf("]");
        if (arrStart == -1 || arrEnd == -1 || arrEnd <= arrStart) return false;

        String arr = body.substring(arrStart + 1, arrEnd);
        java.util.List<String> objetos = new java.util.ArrayList<>();
        int depth = 0, start = -1;
        for (int i = 0; i < arr.length(); i++) {
            char c = arr.charAt(i);
            if (c == '{') { if (depth++ == 0) start = i; }
            else if (c == '}') { if (--depth == 0 && start != -1) { objetos.add(arr.substring(start, i + 1)); start = -1; } }
        }
        if (objetos.isEmpty()) return false;

        boolean alguno = false;
        for (String obj : objetos) {
            String nombre   = extraerValorObj(obj, "nombre");
            double precio   = extraerDoubleObj(obj, "precio");
            int    cantidad = (int) extraerDoubleObj(obj, "cantidad");
            if (nombre.isBlank() || cantidad <= 0) continue;
            if (precio == 0) {
                dao.ProductoDAO productoDAO = new dao.ProductoDAO();
                java.util.List<model.Producto> todos = new java.util.ArrayList<>();
                for (String cat : new String[]{"empanadas", "sopaipillas", "churros", "rapidos"})
                    todos.addAll(productoDAO.listarPorCategoria(cat));
                precio = buscarPrecioEnBD(todos, nombre, nombre);
            }
            ventaDAO.registrarVentaRapida(nombre, cantidad, precio, tipoPago);
            System.out.println(" [items] " + cantidad + "x " + nombre + " | $" + (precio * cantidad));
            alguno = true;
        }
        return alguno;
    }

    private String extraerValorObj(String obj, String clave) {
        String patron = "\"" + clave + "\":\"";
        int ini = obj.indexOf(patron);
        if (ini == -1) return "";
        ini += patron.length();
        int fin = obj.indexOf("\"", ini);
        return fin == -1 ? "" : obj.substring(ini, fin);
    }

    private double extraerDoubleObj(String obj, String clave) {
        String patron = "\"" + clave + "\":";
        int ini = obj.indexOf(patron);
        if (ini == -1) return 0;
        ini += patron.length();
        if (ini < obj.length() && obj.charAt(ini) == '"') ini++;
        int fin = ini;
        while (fin < obj.length() && (Character.isDigit(obj.charAt(fin)) || obj.charAt(fin) == '.')) fin++;
        try { return Double.parseDouble(obj.substring(ini, fin)); } catch (Exception e) { return 0; }
    }

    private void registrarDesdeDetalle(String detalle, double totalPedido, String tipoPago, String cliente) {
        if (detalle == null || detalle.isBlank()) return;
        dao.ProductoDAO productoDAO = new dao.ProductoDAO();
        java.util.List<model.Producto> todosProductos = new java.util.ArrayList<>();
        for (String cat : new String[]{"empanadas", "sopaipillas", "churros", "rapidos"})
            todosProductos.addAll(productoDAO.listarPorCategoria(cat));

        String[] items = detalle.split("\\+");
        boolean registroIndividual = false;
        for (String item : items) {
            item = item.trim();
            if (!item.matches("\\d+x .+")) continue;
            int xIdx = item.indexOf('x');
            int cantidad;
            try { cantidad = Integer.parseInt(item.substring(0, xIdx).trim()); }
            catch (NumberFormatException e) { continue; }
            String nombreWeb      = item.substring(xIdx + 1).trim();
            String nombreNorm     = normalizarNombreWeb(nombreWeb);
            double precioUnitario = buscarPrecioEnBD(todosProductos, nombreWeb, nombreNorm);
            if (precioUnitario == 0 && totalPedido > 0) precioUnitario = totalPedido / cantidad;
            ventaDAO.registrarVentaRapida(nombreNorm, cantidad, precioUnitario, tipoPago);
            registroIndividual = true;
            System.out.println("[detalle] " + cantidad + "x " + nombreNorm + " | $" + (precioUnitario * cantidad));
        }
        if (!registroIndividual && totalPedido > 0)
            ventaDAO.registrarVentaRapida("Pedido Web (" + detalle + ")", 1, totalPedido, tipoPago);
    }

    String normalizarNombreWeb(String nombre) {
        if (nombre == null) return "";
        String[] prefijos = {"Panadería ", "Panaderia ", "Empanadas ", "Sopaipillas ", "Churros ", "Rápidos ", "Rapidos "};
        for (String pref : prefijos) if (nombre.startsWith(pref)) return nombre.substring(pref.length()).trim();
        return nombre;
    }

    private double buscarPrecioEnBD(java.util.List<model.Producto> productos, String nombreWeb, String nombreNorm) {
        for (model.Producto p : productos)
            if (p.getNombre().equalsIgnoreCase(nombreWeb) || p.getNombre().equalsIgnoreCase(nombreNorm)) {
                System.out.println("Precio en inventario: [" + p.getNombre() + "] = $" + p.getPrecio());
                return p.getPrecio();
            }
        double precio = buscarUltimoPrecioRapido(nombreWeb, nombreNorm);
        if (precio > 0) return precio;
        System.out.println("Precio no encontrado para: [" + nombreWeb + "] / [" + nombreNorm + "]");
        return 0;
    }

    private double buscarUltimoPrecioRapido(String nombreWeb, String nombreNorm) {
        String sql = "SELECT precio_unitario FROM ventas_rapidas "
                + "WHERE LOWER(nombre) = LOWER(?) OR LOWER(nombre) = LOWER(?) "
                + "ORDER BY id DESC LIMIT 1";
        try (java.sql.Connection conn = dao.Conexion.conectar();
             java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, nombreWeb);
            ps.setString(2, nombreNorm);
            java.sql.ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                double precio = rs.getDouble(1);
                System.out.println("Precio en ventas_rapidas: [" + nombreNorm + "] = $" + precio);
                return precio;
            }
        } catch (java.sql.SQLException e) {
            System.err.println("Error buscando precio en ventas_rapidas: " + e.getMessage());
        }
        return 0;
    }

    public void iniciar()  { servidor.start(); }
    public void detener()  { servidor.stop(0); System.out.println("Servidor detenido"); }
    public void registrarListener(PedidoListener listener) { listeners.add(listener); }
    public void removerListener(PedidoListener listener)   { listeners.remove(listener); }

    private String readBody(HttpExchange exchange) throws IOException {
        final int MAX_BYTES = 65536;
        InputStream is = exchange.getRequestBody();
        byte[] buffer  = new byte[MAX_BYTES];
        int bytesRead  = is.read(buffer);
        if (bytesRead <= 0) return "";
        return new String(buffer, 0, bytesRead, StandardCharsets.UTF_8);
    }

    private String extraerValor(String json, String clave) {
        String patron = "\"" + clave + "\":\"";
        int inicio = json.indexOf(patron);
        if (inicio == -1) return "-";
        inicio += patron.length();
        int fin = json.indexOf("\"", inicio);
        return fin == -1 ? "-" : json.substring(inicio, fin);
    }

    private double extraerDouble(String json, String clave) {
        String patron = "\"" + clave + "\":";
        int inicio = json.indexOf(patron);
        if (inicio == -1) return 0.0;
        inicio += patron.length();
        int fin = json.indexOf(",", inicio);
        if (fin == -1) fin = json.indexOf("}", inicio);
        if (fin == -1) return 0.0;
        try { return Double.parseDouble(json.substring(inicio, fin).trim()); }
        catch (Exception e) { return 0.0; }
    }

    private String escaparJson(String texto) {
        if (texto == null) return "";
        return texto.replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    private void agregarCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin",  "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
        exchange.getResponseHeaders().set("Access-Control-Max-Age",       "86400");
    }

    private void enviarRespuesta(HttpExchange exchange, int codigo, String respuesta) throws IOException {
        byte[] bytes = respuesta.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(codigo, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
    }

    private String sanitizar(String valor) {
        if (valor == null) return "-";
        String limpio = valor
                .replaceAll("[\\p{Cntrl}]", "")
                .replaceAll("[<>\"']", "")
                .trim();
        return limpio.substring(0, Math.min(limpio.length(), 200));
    }

   private void registrarAcceso(HttpExchange exchange, String endpoint, int status) {
    try {
        String ip = exchange.getRemoteAddress().getAddress().getHostAddress();
        String metodo = exchange.getRequestMethod();
        String userAgent = exchange.getRequestHeaders().getFirst("User-Agent");

        adminDAO.registrarLog(ip, metodo, endpoint, status, userAgent, null);

    } catch (Exception e) {
        e.printStackTrace();
    }
}

    public static void main(String[] args) throws IOException {
        PedidosServer server = new PedidosServer();
        server.iniciar();
    }
}
