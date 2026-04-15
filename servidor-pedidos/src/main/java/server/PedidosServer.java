package server;

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
                            .append("\"timestamp\":\"").append(p.timestamp).append("\"")
                            .append("}");

                    if (i < pedidos.size() - 1) {
                        json.append(",");
                    }
                }

                json.append("]");
                enviarRespuesta(exchange, 200, json.toString());
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

        int hora = java.time.LocalTime.now(java.time.ZoneId.of("America/Santiago")).getHour();

        boolean esPanaderia = false;

        if (detalle != null) {
            String d = detalle.toLowerCase();
            esPanaderia
                    = d.contains("panaderia")
                    || d.contains("panadería")
                    || d.contains("hallula")
                    || d.contains("marraqueta");
        }

        if (esPanaderia) {
            if (hora >= 12 && hora < 13) {
                return "12:00 - 13:00";
            }
            if (hora >= 13 && hora < 14) {
                return "13:00 - 14:00";
            }
            if (hora >= 14 && hora < 15) {
                return "14:00 - 15:00";
            }
            if (hora >= 15 && hora < 16) {
                return "15:00 - 16:00";
            }
            if (hora >= 16 && hora < 17) {
                return "16:00 - 17:00";
            }
            if (hora >= 17 && hora < 18) {
                return "17:00 - 18:00";
            }

            return "FUERA HORARIO";
        } else {
            if (hora >= 18 && hora < 19) {
                return "18:00 - 19:00";
            }
            if (hora >= 19 && hora < 20) {
                return "19:00 - 20:00";
            }
            if (hora >= 20 && hora < 21) {
                return "20:00 - 21:00";
            }
            if (hora >= 21 && hora < 22) {
                return "21:00 - 22:00";
            }

            return "FUERA HORARIO";
        }
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
