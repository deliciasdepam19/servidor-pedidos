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

        String htmlBody = """
                <div style="font-family: system-ui, sans-serif, Arial; font-size: 12px">
                  <div style="text-align: center; margin-bottom: 15px;">
                    <img
                      src="https://i.imgur.com/X4ja4OO.png"
                      alt="Delicias de Pam"
                      width="80"
                      style="display: block; margin: 0 auto;"
                    />
                  </div>
                  <div style="font-size: 16px; color: #2c3e50; text-align: center;">
                    Tu código de verificación para acceder a nuestra app
                  </div>
                  <div
                    style="
                      margin-top: 20px;
                      padding: 15px 0;
                      border-width: 1px 0;
                      border-style: dashed;
                      border-color: lightgrey;
                      text-align: center;
                    "
                  >
                    <div style="color: #2c3e50; font-size: 14px; margin-bottom: 10px;">
                      Usa el siguiente código para completar tu verificación:
                    </div>
                    <div
                      style="
                        display: inline-block;
                        padding: 10px 20px;
                        background-color: aliceblue;
                        border-radius: 5px;
                        font-size: 28px;
                        font-weight: bold;
                        letter-spacing: 4px;
                        color: #2c3e50;
                      "
                    >
                      %s
                    </div>
                    <p style="font-size: 13px; color: #888; margin-top: 15px;">
                      Este código vence en 5 minutos. Si no lo solicitaste, puedes ignorar este mensaje.
                    </p>
                  </div>
                </div>
                """.formatted(codigo);

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