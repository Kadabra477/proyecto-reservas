package com.example.reservafutbol.Servicio;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.InputStreamSource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

import com.example.reservafutbol.Modelo.Reserva;

@Service
public class EmailService {

    @Value("${backend.url}")
    private String backendUrl;

    @Value("${frontend.url}")
    private String frontendUrl;

    @Autowired
    private JavaMailSender mailSender;

    @Value("${spring.mail.properties.mail.smtp.from}")
    private String configuredFromEmail;

    private InternetAddress fromAddress;

    @Autowired
    public EmailService(@Value("${spring.mail.properties.mail.smtp.from}") String configuredFromEmail) {
        try {
            InternetAddress parsed = new InternetAddress(configuredFromEmail);
            if (parsed.getPersonal() == null || parsed.getPersonal().isEmpty()) {
                this.fromAddress = new InternetAddress(parsed.getAddress(), "ReservaFutbol", "UTF-8");
            } else {
                this.fromAddress = new InternetAddress(parsed.getAddress(), parsed.getPersonal(), "UTF-8");
            }
            System.out.println("✔ Dirección de remitente configurada correctamente: " + this.fromAddress.toString());
        } catch (UnsupportedEncodingException | AddressException e) {
            System.err.println("⚠ Error en dirección de remitente configurada: " + configuredFromEmail);
            System.err.println("Usando dirección de fallback: hernandimichele477@gmail.com");
            e.printStackTrace();

            try {
                this.fromAddress = new InternetAddress("hernandimichele477@gmail.com", "ReservaFutbol", "UTF-8");
            } catch (UnsupportedEncodingException fallbackE) {
                System.err.println("❌ FALLO CRÍTICO: No se pudo establecer ni dirección original ni fallback.");
                fallbackE.printStackTrace();
                throw new RuntimeException("No se pudo inicializar EmailService.", fallbackE);
            }
        }
    }

    public void sendVerificationEmail(String to, String username, String verificationToken) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(this.fromAddress);
        helper.setTo(to);
        helper.setSubject("Verifica tu cuenta en ¿Dónde Juego?");

        // <-- CAMBIO: La URL de verificación ahora apunta a una ruta específica del frontend para manejar la redirección -->
        String verificationLink = frontendUrl + "/verify-account?token=" + verificationToken;

        String content = "<html><body>"
                + "<h2>¡Hola, " + username + "!</h2>"
                + "<p>Gracias por registrarte en ¿Dónde Juego?.</p>"
                + "<p>Para activar tu cuenta, haz clic en el siguiente enlace:</p>"
                + "<p><a href=\"" + verificationLink + "\">Verificar mi cuenta</a></p>"
                + "<p>Si no te registraste, ignora este correo.</p>"
                + "<p>Saludos,<br/>El equipo de ¿Dónde Juego?</p>"
                + "</body></html>";
        helper.setText(content, true);

        try {
            mailSender.send(message);
            System.out.println("✔ Email de verificación enviado a: " + to);
        } catch (Exception e) {
            System.err.println("❌ Error al enviar email de verificación a " + to + ": " + e.getMessage());
            e.printStackTrace();
            throw new MessagingException("Fallo al enviar el correo de verificación", e);
        }
    }

    public void sendPasswordResetEmail(String to, String token) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(this.fromAddress);
        helper.setTo(to);
        helper.setSubject("Restablecer tu contraseña en ¿Dónde Juego?");

        String resetLink = frontendUrl + "/reset-password?token=" + token;

        String content = "<html><body>"
                + "<h2>Hola,</h2>"
                + "<p>Recibimos una solicitud para restablecer tu contraseña. Haz clic en el siguiente enlace:</p>"
                + "<p><a href=\"" + resetLink + "\">Restablecer Contraseña</a></p>"
                + "<p>Este enlace expirará en 1 hora.</p>"
                + "<p>Si no lo solicitaste, puedes ignorar este mensaje.</p>"
                + "<p>Saludos,<br/>El equipo de ¿Dónde Juego?</p>"
                + "</body></html>";
        helper.setText(content, true);

        try {
            mailSender.send(message);
            System.out.println("✔ Email de recuperación enviado a: " + to);
        } catch (Exception e) {
            System.err.println("❌ Error al enviar email de recuperación a " + to + ": " + e.getMessage());
            e.printStackTrace();
            throw new MessagingException("Fallo al enviar el correo de recuperación", e);
        }
    }

    public void sendNewReservationNotification(Reserva reserva, String toEmail) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(this.fromAddress);
        helper.setTo(toEmail);
        helper.setSubject("Nueva Reserva en " + reserva.getComplejo().getNombre() + " - ¿Dónde Juego?");

        String content = "<html><body>"
                + "<h2>¡Nueva Reserva Realizada!</h2>"
                + "<p>Se ha realizado una nueva reserva para tu complejo:</p>"
                + "<ul>"
                + "<li><strong>Complejo:</strong> " + reserva.getComplejo().getNombre() + "</li>"
                + "<li><strong>Tipo de Cancha:</strong> " + reserva.getTipoCanchaReservada() + "</li>"
                + "<li><strong>Fecha y Hora:</strong> " + reserva.getFechaHora() + "</li>"
                + "<li><strong>Cliente:</strong> " + reserva.getCliente() + "</li>"
                + "<li><strong>Teléfono:</strong> " + reserva.getTelefono() + "</li>"
                + "<li><strong>DNI:</strong> " + reserva.getDni() + "</li>"
                + "<li><strong>Método de Pago:</strong> " + reserva.getMetodoPago() + "</li>"
                + "<li><strong>Precio:</strong> $" + reserva.getPrecio() + "</li>"
                + "</ul>"
                + "<p>Revisá el panel de administración para más info.</p>"
                + "<p>Saludos,<br/>El equipo de ¿Dónde Juego?</p>"
                + "</body></html>";
        helper.setText(content, true);

        try {
            mailSender.send(message);
            System.out.println("✔ Notificación enviada a: " + toEmail);
        } catch (Exception e) {
            System.err.println("❌ Error al enviar notificación a " + toEmail + ": " + e.getMessage());
            e.printStackTrace();
            throw new MessagingException("Fallo al enviar notificación de reserva", e);
        }
    }
    public void enviarComprobanteConPDF(String to, ByteArrayInputStream pdfBytes) throws MessagingException {
        MimeMessage mensaje = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mensaje, true, "UTF-8");

        helper.setFrom(this.fromAddress);
        helper.setTo(to);
        helper.setSubject("Comprobante de reserva de cancha");
        helper.setText("Hola! Te adjuntamos el comprobante de tu reserva. Mostralo al llegar. ¡Gracias por reservar!");

        try {
            byte[] pdfData = pdfBytes.readAllBytes();
            InputStreamSource adjunto = new ByteArrayResource(pdfData);
            helper.addAttachment("comprobante_reserva.pdf", adjunto);

            mailSender.send(mensaje);
            System.out.println("✔ Comprobante enviado a: " + to);
        } catch (Exception e) {
            System.err.println("❌ Error al enviar comprobante a " + to + ": " + e.getMessage());
            e.printStackTrace();
            throw new MessagingException("Fallo al enviar comprobante", e);
        }
    }
}