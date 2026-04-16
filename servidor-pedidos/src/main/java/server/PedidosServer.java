package server;

import server.StockDescontador;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dao.PedidosDAO;
import dao.VentaDAO;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import server.StockDescontador;

public class PedidosServer {

    private final VentaDAO ventaDAO = new VentaDAO();
    private final PedidosDAO pedidosDAO = new PedidosDAO();

    private static final int PUERTO = System.getenv("PORT") != null
            ? Integer.parseInt(System.getenv("PORT"))
            : 8888;

    private HttpServer servidor;

    private final Map<String, Long> ultimoPedidoPorIp = new ConcurrentHashMap<>();
    private final Map<String, Integer> contadorPorIp = new ConcurrentHashMap<>();

    public PedidosServer() throws IOException {

        servidor = HttpServer.create(new InetSocketAddress("0.0.0.0", PUERTO), 0);

        servidor.createContext("/api/pedidos", exchange -> {

            agregarCorsHeaders(exchange);

            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if ("POST".equals(exchange.getRequestMethod())) {
                try {

                    String body = readBody(exchange);

                    String cliente = sanitizar(extraerValor(body, "cliente"));
                    String telefono = sanitizar(extraerValor(body, "telefono"));
                    String detalle = sanitizar(extraerValor(body, "detalle"));
                    double total = extraerDouble(body, "total");

                    String tipoPago = extraerValor(body, "tipoPago");
                    if ("-".equals(tipoPago) || tipoPago.isBlank()) {
                        tipoPago = "EFECTIVO";
                    }

                    System.out.println("INSERTANDO PEDIDO: " + cliente + " - " + System.currentTimeMillis());

                    String franja = calcularFranjaActual(detalle);
                    System.out.println("FRANJA CALCULADA: " + franja);

                    if ("FUERA HORARIO".equals(franja)) {
                        enviarRespuesta(exchange, 403,
                                "{\"exito\":false,\"error\":\"Pedido fuera de horario permitido\"}");
                        return;
                    }

                    int[] resultado = pedidosDAO.guardarPedidoAutoNumero(
                            cliente,
                            telefono,
                            detalle,
                            total,
                            franja,
                            "WEB"
                    );

                    int id = resultado[0];
                    int numeroPedido = resultado[1];

                    StockDescontador.descontarDesdeDetalle(detalle);
                    registrarVentaDesdeWeb(body, tipoPago, cliente);

                    String respuesta = "{"
                            + "\"exito\":true,"
                            + "\"id\":" + id + ","
                            + "\"numero\":" + numeroPedido
                            + "}";

                    enviarRespuesta(exchange, 200, respuesta);

                } catch (Exception e) {
                    e.printStackTrace();
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
                            .append(PedidosDAO.formatearNumero(p.numero, p.origen))
                            .append("\",")
                            .append("\"cliente\":\"").append(escaparJson(p.cliente)).append("\",")
                            .append("\"telefono\":\"").append(escaparJson(p.telefono)).append("\",")
                            .append("\"detalle\":\"").append(escaparJson(p.detalle)).append("\",")
                            .append("\"total\":").append(p.total).append(",")
                            .append("\"estado\":\"").append(p.estado).append("\",")
                            .append("\"franja\":\"").append(p.franja).append("\",")
                            .append("\"timestamp\":\"" + obtenerHoraExacta() + "\"")
                            .append("}");

                    if (i < pedidos.size() - 1) {
                        json.append(",");
                    }
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

                    String stock = StockDescontador.obtenerStockJSON();

                    enviarRespuesta(exchange, 200, stock);

                } catch (Exception e) {
                    e.printStackTrace();
                    enviarRespuesta(exchange, 500, "{}");
                }
            }
        });

        servidor.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(10));
        System.out.println("Servidor OK puerto " + PUERTO);
    }

    private void registrarVentaDesdeWeb(String body, String tipoPago, String cliente) {
        double totalPedido = extraerDouble(body, "total");
        ventaDAO.registrarVentaRapida("Pedido Web", 1, totalPedido, tipoPago);
    }

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
            return Double.parseDouble(json.substring(i, f));
        } catch (Exception e) {
            return 0;
        }
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

    private String calcularFranjaActual(String detalle) {

        java.time.LocalTime ahora = java.time.LocalTime.now(
                java.time.ZoneId.of("America/Santiago"));

        int hora = ahora.getHour();
        int minuto = ahora.getMinute();

        boolean esPanaderia = false;

        if (detalle != null) {
            String d = detalle.toLowerCase();
            esPanaderia
                    = d.contains("panaderia")
                    || d.contains("panadería")
                    || d.contains("hallula")
                    || d.contains("marraqueta");
        }

        int inicioMin;
        int horaInicio;

        if (minuto < 30) {
            inicioMin = 30;
            horaInicio = hora;
        } else {
            inicioMin = 0;
            horaInicio = hora + 1;
        }

        if (esPanaderia) {
            if (horaInicio < 12 || horaInicio >= 18) {
                return "FUERA HORARIO";
            }
        } else {
            if (horaInicio < 18 || horaInicio >= 22) {
                return "FUERA HORARIO";
            }
        }

        int finMin = (inicioMin == 30) ? 0 : 30;
        int horaFin = (inicioMin == 30) ? horaInicio + 1 : horaInicio;

        return String.format("%02d:%02d - %02d:%02d",
                horaInicio, inicioMin,
                horaFin, finMin);
    }

    private String obtenerHoraExacta() {
        return java.time.LocalTime.now(
                java.time.ZoneId.of("America/Santiago"))
                .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
    }

    private String sanitizar(String v) {
        return v == null ? "-" : v.replaceAll("[<>\"']", "").trim();
    }

    public void iniciar() {
        servidor.start();
    }

    public static void main(String[] args) throws IOException {
        new PedidosServer().iniciar();
    }
}
