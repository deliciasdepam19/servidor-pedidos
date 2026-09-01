package server;

import java.io.*;
import java.net.Socket;
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
            Socket socket = new Socket("smtp.gmail.com", 587);
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));

            // Read greeting
            reader.readLine();

            // EHLO
            writer.write("EHLO deliciasdepam.com\r\n");
            writer.flush();
            readAll(reader);

            // STARTTLS
            writer.write("STARTTLS\r\n");
            writer.flush();
            readAll(reader);

            // Re-negotiate after STARTTLS
            writer.write("EHLO deliciasdepam.com\r\n");
            writer.flush();
            readAll(reader);

            // AUTH LOGIN
            writer.write("AUTH LOGIN\r\n");
            writer.flush();
            readAll(reader);

            // Username
            writer.write(Base64.getEncoder().encodeToString(GMAIL_USER.getBytes()) + "\r\n");
            writer.flush();
            readAll(reader);

            // Password
            writer.write(Base64.getEncoder().encodeToString(GMAIL_APP_PASSWORD.getBytes()) + "\r\n");
            writer.flush();
            readAll(reader);

            // MAIL FROM
            writer.write("MAIL FROM:<" + GMAIL_USER + ">\r\n");
            writer.flush();
            readAll(reader);

            // RCPT TO
            writer.write("RCPT TO:<" + email + ">\r\n");
            writer.flush();
            readAll(reader);

            // DATA
            writer.write("DATA\r\n");
            writer.flush();
            readAll(reader);

            // Email content
            writer.write("From: Delicias de Pam <" + GMAIL_USER + ">\r\n");
            writer.write("To: <" + email + ">\r\n");
            writer.write("Subject:=?UTF-8?B?" + Base64.getEncoder().encodeToString("Tu código de verificación - Delicias de Pam".getBytes("UTF-8")) + "?=\r\n");
            writer.write("MIME-Version: 1.0\r\n");
            writer.write("Content-Type: text/html; charset=UTF-8\r\n");
            writer.write("\r\n");
            writer.write(htmlBody + "\r\n");
            writer.write(".\r\n");
            writer.flush();
            readAll(reader);

            // QUIT
            writer.write("QUIT\r\n");
            writer.flush();
            readAll(reader);

            writer.close();
            reader.close();
            socket.close();

            System.out.println("[EMAIL] OTP enviado a " + email);
            return true;

        } catch (Exception e) {
            System.err.println("[EMAIL] Error enviando: " + e.getMessage());
            return false;
        }
    }

    private static void readAll(BufferedReader reader) throws IOException {
        while (reader.ready()) {
            reader.readLine();
        }
    }
}
