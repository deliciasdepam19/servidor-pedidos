package server;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public class EmailService {

    private static final String BREVO_API_KEY = System.getenv("BREVO_API_KEY");
    private static final String SENDER_EMAIL = System.getenv("GMAIL_USER");
    private static final String SENDER_NAME = "Delicias de Pam";
    private static final String BREVO_URL = "https://api.brevo.com/v3/smtp/email";

    public static boolean enviarCodigoOTP(String email, String codigo) {
        if (BREVO_API_KEY == null || BREVO_API_KEY.isBlank()) {
            System.err.println("[EMAIL] BREVO_API_KEY no configurado");
            return false;
        }
        if (SENDER_EMAIL == null || SENDER_EMAIL.isBlank()) {
            System.err.println("[EMAIL] GMAIL_USER (remitente verificado en Brevo) no configurado");
            return false;
        }

        String htmlBody = "<!DOCTYPE html>"
                + "<html><head><style>"
                + "body{font-family:Arial,sans-serif;background:#09100f;color:#e0eff1;text-align:center;padding:40px;}"
                + ".card{background:#101a1c;border-radius:16px;border:1px solid rgba(64,206,224,0.15);padding:32px;max-width:400px;margin:0 auto;}"
                + ".code{font-size:48px;font-weight:800;color:#40cee0;letter-spacing:12px;margin:24px 0;}"
                + ".footer{color:#6a8f96;font-size:12px;margin-top:24px;}"
                + "</style></head><body>"
                + "<div class='card'>"
                + "<h2 style='color:#40cee0;'>Delicias de Pam</h2>"
                + "<p>Tu código de verificación es:</p>"
                + "<div class='code'>" + codigo + "</div>"
                + "<p style='color:#6a8f96;'>Este código expira en 5 minutos.</p>"
                + "<p class='footer'>Si no solicitaste este código, puedes ignorar este mensaje.</p>"
                + "</div></body></html>";

        String payload = String.format("""
                {
                  "sender": {"email": "%s", "name": "%s"},
                  "to": [{"email": "%s"}],
                  "subject": "Tu código de verificación - Delicias de Pam",
                  "htmlContent": "%s"
                }
                """, jsonEscape(SENDER_EMAIL), jsonEscape(SENDER_NAME), jsonEscape(email), jsonEscape(htmlBody));

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BREVO_URL))
                    .timeout(Duration.ofSeconds(30))
                    .header("api-key", BREVO_API_KEY)
                    .header("content-type", "application/json")
                    .header("accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();

            if (status >= 200 && status < 300) {
                System.out.println("[EMAIL] OTP enviado a " + email + " (Brevo HTTP " + status + ")");
                return true;
            }

            System.err.println("[EMAIL] Error Brevo HTTP " + status + ": " + response.body());
            return false;

        } catch (Exception e) {
            System.err.println("[EMAIL] Error: " + e.getMessage());
            return false;
        }
    }

    private static String jsonEscape(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}