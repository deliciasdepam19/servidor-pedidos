package server;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class EmailService {

    private static final String RESEND_API_KEY = System.getenv("RESEND_API_KEY");
    private static final String FROM_EMAIL = "Delicias de Pam <onboarding@resend.dev>";

    public static boolean enviarCodigoOTP(String email, String codigo) {
        if (RESEND_API_KEY == null || RESEND_API_KEY.isBlank()) {
            System.err.println("[EMAIL] RESEND_API_KEY no configurada");
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
                + "<h2 style='color:#40cee0;'>🍞 Delicias de Pam</h2>"
                + "<p>Tu código de verificación es:</p>"
                + "<div class='code'>" + codigo + "</div>"
                + "<p style='color:#6a8f96;'>Este código expira en 5 minutos.</p>"
                + "<p class='footer'>Si no solicitaste este código, podés ignorar este mensaje.</p>"
                + "</div></body></html>";

        String jsonPayload = "{"
                + "\"from\":\"Delicias de Pam <" + FROM_EMAIL + ">\","
                + "\"to\":[\"" + email + "\"],"
                + "\"subject\":\"Tu código de verificación - Delicias de Pam\","
                + "\"html\":\"" + escaparJson(htmlBody) + "\""
                + "}";

        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.resend.com/emails"))
                    .header("Authorization", "Bearer " + RESEND_API_KEY)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200 || response.statusCode() == 201) {
                System.out.println("[EMAIL] OTP enviado a " + email);
                return true;
            } else {
                System.err.println("[EMAIL] Error Resend " + response.statusCode() + ": " + response.body());
                return false;
            }
        } catch (Exception e) {
            System.err.println("[EMAIL] Error enviando: " + e.getMessage());
            return false;
        }
    }

    private static String escaparJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
