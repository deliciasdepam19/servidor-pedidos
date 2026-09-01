package server;

import javax.net.ssl.*;
import java.io.*;
import java.net.Socket;
import java.security.SecureRandom;
import java.util.Base64;

public class EmailService {

    private static final String GMAIL_USER = System.getenv("GMAIL_USER");
    private static final String GMAIL_APP_PASSWORD = System.getenv("GMAIL_APP_PASSWORD");

    public static boolean enviarCodigoOTP(String email, String codigo) {
        if (GMAIL_USER == null || GMAIL_USER.isBlank() || GMAIL_APP_PASSWORD == null || GMAIL_APP_PASSWORD.isBlank()) {
            System.err.println("[EMAIL] GMAIL_USER o GMAIL_APP_PASSWORD no configurados");
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

        try {
            // Conectar a SMTP
            Socket plainSocket = new Socket("smtp.gmail.com", 587);
            plainSocket.setSoTimeout(15000);
            BufferedReader reader = new BufferedReader(new InputStreamReader(plainSocket.getInputStream()));
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(plainSocket.getOutputStream()));

            // Leer greeting
            System.out.println("[EMAIL] S: " + reader.readLine());

            // EHLO
            writer.write("EHLO deliciasdepam.com\r\n");
            writer.flush();
            System.out.println("[EMAIL] S: " + reader.readLine());

            // STARTTLS
            writer.write("STARTTLS\r\n");
            writer.flush();
            System.out.println("[EMAIL] S: " + reader.readLine());

            // Envolver con SSL
            SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            SSLSocket sslSocket = (SSLSocket) factory.createSocket(
                    plainSocket,
                    plainSocket.getInetAddress().getHostAddress(),
                    plainSocket.getPort(),
                    true
            );
            sslSocket.startHandshake();

            // Re-negociar después de TLS
            reader = new BufferedReader(new InputStreamReader(sslSocket.getInputStream()));
            writer = new BufferedWriter(new OutputStreamWriter(sslSocket.getOutputStream()));

            // EHLO de nuevo
            writer.write("EHLO deliciasdepam.com\r\n");
            writer.flush();
            String line;
            while ((line = reader.readLine()) != null && line.startsWith("250-")) {
                System.out.println("[EMAIL] S: " + line);
            }
            System.out.println("[EMAIL] S: " + line);

            // AUTH LOGIN
            writer.write("AUTH LOGIN\r\n");
            writer.flush();
            System.out.println("[EMAIL] S: " + reader.readLine());

            // Username
            writer.write(Base64.getEncoder().encodeToString(GMAIL_USER.getBytes()) + "\r\n");
            writer.flush();
            System.out.println("[EMAIL] S: " + reader.readLine());

            // Password
            writer.write(Base64.getEncoder().encodeToString(GMAIL_APP_PASSWORD.getBytes()) + "\r\n");
            writer.flush();
            System.out.println("[EMAIL] S: " + reader.readLine());

            // MAIL FROM
            writer.write("MAIL FROM:<" + GMAIL_USER + ">\r\n");
            writer.flush();
            System.out.println("[EMAIL] S: " + reader.readLine());

            // RCPT TO
            writer.write("RCPT TO:<" + email + ">\r\n");
            writer.flush();
            System.out.println("[EMAIL] S: " + reader.readLine());

            // DATA
            writer.write("DATA\r\n");
            writer.flush();
            System.out.println("[EMAIL] S: " + reader.readLine());

            // Contenido del email
            writer.write("From: Delicias de Pam <" + GMAIL_USER + ">\r\n");
            writer.write("To: <" + email + ">\r\n");
            writer.write("Subject:=?UTF-8?B?" + Base64.getEncoder().encodeToString("Tu código de verificación - Delicias de Pam".getBytes("UTF-8")) + "?=\r\n");
            writer.write("MIME-Version: 1.0\r\n");
            writer.write("Content-Type: text/html; charset=UTF-8\r\n");
            writer.write("\r\n");
            writer.write(htmlBody + "\r\n");
            writer.write(".\r\n");
            writer.flush();
            System.out.println("[EMAIL] S: " + reader.readLine());

            // QUIT
            writer.write("QUIT\r\n");
            writer.flush();
            System.out.println("[EMAIL] S: " + reader.readLine());

            writer.close();
            reader.close();
            sslSocket.close();

            System.out.println("[EMAIL] OTP enviado a " + email);
            return true;

        } catch (Exception e) {
            System.err.println("[EMAIL] Error: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}
