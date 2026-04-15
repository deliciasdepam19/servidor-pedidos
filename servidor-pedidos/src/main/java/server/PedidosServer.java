package server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dao.AdminDAO;
import dao.VentaDAO;
import java.io.IOException;
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
            throw new RuntimeException("ERROR: Variables de entorno ADMIN_USER y ADMIN_PASS no configuradas");
        }
    }

    private static final int PUERTO = System.getenv("PORT") != null
            ? Integer.parseInt(System.getenv("PORT"))
            : 8888;

    private HttpServer servidor;
    private List<PedidoListener> listeners = new CopyOnWriteArrayList<>();
    private List<Pedido> historicoPedidos = new CopyOnWriteArrayList<>();
    private final Map<String, Long> ultimoPedidoPorIp = new ConcurrentHashMap<>();
    private final Map<String, Integer> contadorPorIp = new ConcurrentHashMap<>();

    public static class Pedido {
        public int numero;
        public String cliente;
        public String telefono;
        public String detalle;
        public double total;
        public String timestamp;

        public Pedido(int numero, String cliente, String telefono, String detalle, double total) {
            this.numero = numero;
            this.cliente = cliente;
            this.telefono = telefono;
            this.detalle = detalle;
            this.total = total;
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

            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if ("POST".equals(exchange.getRequestMethod())) {
                String ip = exchange.getRemoteAddress().getAddress().getHostAddress();

                try {
                    String body = readBody(exchange);

                    long ahoraMs = System.currentTimeMillis();
                    Long ultimo = ultimoPedidoPorIp.get(ip);
                    int contador = contadorPorIp.getOrDefault(ip, 0);

                    if (ultimo != null && (ahoraMs - ultimo) < 600_000 && contador >= 5) {
                        enviarRespuesta(exchange, 429,
                                "{\"exito\":false,\"error\":\"Demasiados pedidos.\"}");
                        return;
                    }

                    contadorPorIp.put(ip, (ultimo == null || (ahoraMs - ultimo) >= 600_000) ? 1 : contador + 1);
                    ultimoPedidoPorIp.put(ip, ahoraMs);

                    if (adminDAO.estaBloqueada(ip)) {
                        enviarRespuesta(exchange, 403, "{\"error\":\"Bloqueado\"}");
                        return;
                    }

                    String cliente = sanitizar(extraerValor(body, "cliente"));
                    String telefono = sanitizar(extraerValor(body, "telefono"));
                    String detalle = sanitizar(extraerValor(body, "detalle"));
                    double total = extraerDouble(body, "total");

                    String tipoPago = extraerValor(body, "tipoPago");
                    if ("-".equals(tipoPago) || tipoPago.isBlank()) {
                        tipoPago = "EFECTIVO";
                    }

                    int numeroPedido = ventaDAO.obtenerSiguienteNumeroPedido();
                    Pedido pedido = new Pedido(numeroPedido, cliente, telefono, detalle, total);

                    historicoPedidos.add(pedido);

                    boolean procesadoItems = registrarDesdeItems(body, tipoPago, cliente);
                    registrarDesdeDetalle(detalle, total, tipoPago, cliente);

                    for (PedidoListener l : listeners) {
                        l.onNuevoPedido(pedido);
                    }

                    enviarRespuesta(exchange, 200,
                            "{\"exito\":true,\"numero\":\"" + String.format("%03d", numeroPedido) + "\"}");

                } catch (Exception e) {
                    e.printStackTrace();
                    enviarRespuesta(exchange, 400,
                            "{\"error\":\"" + e.getMessage() + "\"}");
                }

            } else {
                enviarRespuesta(exchange, 405, "{\"error\":\"Método no permitido\"}");
            }
        });
    }

    private boolean registrarDesdeItems(String body, String tipoPago, String cliente) {
        int idx = body.indexOf("\"items\":");
        if (idx == -1) return false;

        int start = body.indexOf("[", idx);
        int end = body.lastIndexOf("]");
        if (start == -1 || end == -1) return false;

        String arr = body.substring(start + 1, end);

        String[] objetos = arr.split("\\},\\{");

        boolean alguno = false;

        for (String obj : objetos) {

            String nombre = extraerValorObj(obj, "nombre");
            int cantidad = (int) extraerDoubleObj(obj, "cantidad");
            double precio = extraerDoubleObj(obj, "precio");

            if (nombre.isBlank() || cantidad <= 0) continue;

            if (precio <= 0) {
                precio = buscarUltimoPrecioRapido(nombre, nombre);
            }

            // ✅ MEJORA 1: evitar precio 0
            if (precio <= 0) {
                System.out.println("⚠ Precio no encontrado: " + nombre);
                continue;
            }

            ventaDAO.registrarVentaRapida(nombre, cantidad, precio, tipoPago);
            alguno = true;
        }

        return alguno;
    }

    private void registrarDesdeDetalle(String detalle, double total, String tipoPago, String cliente) {
        if (detalle == null || detalle.isBlank()) return;

        String[] items = detalle.split("\\+");

        for (String item : items) {
            item = item.trim();

            if (!item.matches("\\d+x .+")) continue;

            int x = item.indexOf('x');
            int cantidad = Integer.parseInt(item.substring(0, x).trim());
            String nombre = item.substring(x + 1).trim();

            double precio = buscarUltimoPrecioRapido(nombre, nombre);

            if (precio <= 0 && total > 0) {
                precio = total / cantidad;
            }

            if (precio <= 0) continue;

            ventaDAO.registrarVentaRapida(nombre, cantidad, precio, tipoPago);
        }
    }

    private double buscarUltimoPrecioRapido(String n1, String n2) {
        String sql = "SELECT precio_unitario FROM ventas_rapidas WHERE LOWER(nombre)=LOWER(?) OR LOWER(nombre)=LOWER(?) ORDER BY id DESC LIMIT 1";

        try (java.sql.Connection c = dao.Conexion.conectar();
             java.sql.PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setString(1, n1);
            ps.setString(2, n2);

            var rs = ps.executeQuery();
            if (rs.next()) return rs.getDouble(1);

        } catch (Exception e) {
            e.printStackTrace();
        }

        return 0;
    }

    private String extraerValor(String json, String clave) {
        String patron = "\"" + clave + "\":\"";
        int inicio = json.indexOf(patron);
        if (inicio == -1) return "-";

        inicio += patron.length();
        int fin = json.indexOf("\"", inicio);

        String valor = (fin == -1) ? "-" : json.substring(inicio, fin);

        return valor.trim();
    }

    private String extraerValorObj(String obj, String clave) {
        return extraerValor(obj, clave);
    }

    private double extraerDoubleObj(String obj, String clave) {
        try {
            String val = extraerValor(obj, clave);
            return Double.parseDouble(val);
        } catch (Exception e) {
            return 0;
        }
    }

    private double extraerDouble(String json, String clave) {
        try {
            String patron = "\"" + clave + "\":";
            int inicio = json.indexOf(patron);
            if (inicio == -1) return 0;

            inicio += patron.length();
            int fin = json.indexOf(",", inicio);
            if (fin == -1) fin = json.indexOf("}", inicio);

            return Double.parseDouble(json.substring(inicio, fin).trim());

        } catch (Exception e) {
            return 0;
        }
    }

    private String sanitizar(String v) {
        if (v == null) return "-";
        return v.replaceAll("[<>\"']", "").trim();
    }

    private String readBody(HttpExchange ex) throws IOException {
        return new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    private void agregarCorsHeaders(HttpExchange ex) {
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS");
        ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
    }

    private void enviarRespuesta(HttpExchange ex, int code, String resp) throws IOException {
        byte[] bytes = resp.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    public void iniciar() {
        servidor.start();
    }

    public static void main(String[] args) throws IOException {
        new PedidosServer().iniciar();
    }
}
