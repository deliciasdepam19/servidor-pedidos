package server;

import jakarta.mail.*;
import jakarta.mail.internet.*;
import java.util.Properties;

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

        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", "smtp.gmail.com");
        props.put("mail.smtp.port", "587");
        props.put("mail.smtp.timeout", "10000");
        props.put("mail.smtp.connectiontimeout", "10000");

        Session session = Session.getInstance(props, new Authenticator() {
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(GMAIL_USER, GMAIL_APP_PASSWORD);
            }
        });

        try {
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(GMAIL_USER, "Delicias de Pam"));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(email));
            message.setSubject("Tu código de verificación - Delicias de Pam");
            message.setContent(htmlBody, "text/html; charset=UTF-8");

            Transport.send(message);
            System.out.println("[EMAIL] OTP enviado a " + email);
            return true;

        } catch (Exception e) {
            System.err.println("[EMAIL] Error: " + e.getMessage());
            return false;
        }
    }
}
