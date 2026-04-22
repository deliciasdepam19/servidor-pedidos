package server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dao.PedidosDAO;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PedidosServer {

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
                    String cliente  = sanitizar(extraerValor(body, "cliente"));
                    String telefono = sanitizar(extraerValor(body, "telefono"));
                    String detalle  = sanitizar(extraerValor(body, "detalle"));
                    double total    = extraerDouble(body, "total");

                    String tipoPago = extraerValor(body, "tipoPago");
                    if ("-".equals(tipoPago) || tipoPago.isBlank()) {
                        tipoPago = "EFECTIVO";
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
                    if ("-".equals(fechaEntrega) || fechaEntrega.isBlank()) {
                        fechaEntrega = null;
                    }

                    int[] resultado = pedidosDAO.guardarPedidoAutoNumero(
                            cliente,
                            telefono,
                            detalle,
                            total,
                            franja,
                            "WEB",
                            fechaEntrega
                    );

                    int id            = resultado[0];
                    int numeroPedido  = resultado[1];

                    String respuesta = "{"
                            + "\"exito\":true,"
                            + "\"id\":"     + id            + ","
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

    int inicioMin;
    int horaInicio;

    if (minuto < 30) {
        inicioMin  = 30;
        horaInicio = hora;
    } else {
        inicioMin  = 0;
        horaInicio = hora + 1;
    }

    int finMin  = (inicioMin == 30) ? 0  : 30;
    int horaFin = (inicioMin == 30) ? horaInicio + 1 : horaInicio;

    return String.format("%02d:%02d - %02d:%02d", horaInicio, inicioMin, horaFin, finMin);
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
