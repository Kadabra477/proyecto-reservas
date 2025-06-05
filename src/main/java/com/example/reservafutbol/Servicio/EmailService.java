package com.example.reservafutbol.Servicio;

import com.example.reservafutbol.Modelo.Reserva; // Importar la entidad Reserva
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.InputStreamSource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

// Importaciones para el logger
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Service
public class EmailService {

    // Declaración del logger
    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    @Value("${backend.url}")
    private String backendUrl;

    @Value("${frontend.url}")
    private String frontendUrl;

    @Autowired
    private JavaMailSender mailSender;

    public void sendValidationEmail(String to, String token) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject("Valida tu cuenta en ¿Dónde Juego?");

            String validationUrl = backendUrl + "/api/auth/validate?token=" + token;
            message.setText("¡Gracias por registrarte en ¿Dónde Juego?!\n\n" +
                    "Por favor, haz clic en el siguiente enlace para activar tu cuenta:\n" +
                    validationUrl + "\n\n" +
                    "Si no te registraste, ignora este mensaje.\n\n" +
                    "Saludos,\nEl equipo de ¿Dónde Juego?");
            mailSender.send(message);
            System.out.println("Email de validación enviado a: " + to);
            log.info("Email de validación enviado a: {}", to); // Uso del logger
        } catch (Exception e) {
            System.err.println("Error al enviar email de validación a " + to + ": " + e.getMessage());
            log.error("Error al enviar email de validación a {}: {}", to, e.getMessage(), e); // Uso del logger
        }
    }

    public void sendPasswordResetEmail(String to, String token) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject("Restablecer tu contraseña en ¿Dónde Juego?");

            String resetUrl = frontendUrl + "/reset-password?token=" + token;

            message.setText("Hola,\n\n" +
                    "Recibimos una solicitud para restablecer tu contraseña. Haz clic en el siguiente enlace:\n" +
                    resetUrl + "\n\n" +
                    "Este enlace expirará en 1 hora.\n\n" +
                    "Si no solicitaste esto, puedes ignorar este mensaje.\n\n" +
                    "Saludos,\nEl equipo de ¿Dónde Juego?");
            mailSender.send(message);
            System.out.println(">>> Email de reseteo de contraseña enviado a: " + to);
            log.info("Email de reseteo de contraseña enviado a: {}", to); // Uso del logger
        } catch (Exception e) {
            System.err.println(">>> ERROR al enviar email de reseteo a " + to + ": " + e.getMessage());
            log.error("Error al enviar email de reseteo a {}: {}", to, e.getMessage(), e); // Uso del logger
        }
    }

    public void enviarComprobanteConPDF(String to, ByteArrayInputStream pdfBytes) {
        try {
            MimeMessage mensaje = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mensaje, true);

            helper.setTo(to);
            helper.setSubject("Comprobante de reserva de cancha");
            helper.setText("Hola! Te adjuntamos el comprobante de tu reserva. Mostralo al llegar. ¡Gracias por reservar!");

            InputStreamSource adjunto = new ByteArrayResource(pdfBytes.readAllBytes());
            helper.addAttachment("comprobante_reserva.pdf", adjunto);

            mailSender.send(mensaje);
            System.out.println(">>> Comprobante enviado por email a: " + to);
            log.info("Comprobante enviado por email a: {}", to); // Uso del logger
        } catch (Exception e) {
            System.err.println(">>> ERROR al enviar comprobante PDF a " + to + ": " + e.getMessage());
            log.error("Error al enviar comprobante PDF a {}: {}", to, e.getMessage(), e); // Uso del logger
        }
    }

    // NUEVO MÉTODO: Enviar notificación de nueva reserva al administrador
    public void sendNewReservationNotification(Reserva reserva, String adminEmailAddress) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);

            helper.setTo(adminEmailAddress); // Email del administrador
            helper.setSubject("¡Nueva Reserva Recibida en ¿Dónde Juego?! - ID: " + reserva.getId());

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
            NumberFormat currencyFormatter = NumberFormat.getCurrencyInstance(new Locale("es", "AR"));

            String htmlContent = "<html><body>"
                    + "<h2>¡Nueva Reserva Recibida!</h2>"
                    + "<p>Se ha realizado una nueva reserva a través de tu plataforma:</p>"
                    + "<ul>"
                    + "<li><strong>ID de Reserva:</strong> " + reserva.getId() + "</li>"
                    + "<li><strong>Cancha:</strong> " + reserva.getCanchaNombre() + "</li>"
                    + "<li><strong>Fecha y Hora:</strong> " + reserva.getFechaHora().format(formatter) + "</li>"
                    + "<li><strong>Cliente:</strong> " + reserva.getCliente() + "</li>"
                    + "<li><strong>Email Cliente:</strong> " + reserva.getUserEmail() + "</li>"
                    + "<li><strong>Teléfono Cliente:</strong> " + (reserva.getTelefono() != null ? reserva.getTelefono() : "N/A") + "</li>"
                    + "<li><strong>Precio Total:</strong> " + (reserva.getPrecio() != null ? currencyFormatter.format(reserva.getPrecio()) : "N/A") + "</li>"
                    + "<li><strong>Método de Pago:</strong> " + (reserva.getMetodoPago() != null ? reserva.getMetodoPago() : "N/A") + "</li>"
                    + "<li><strong>Estado Actual:</strong> " + (reserva.getEstado() != null ? reserva.getEstado() : "N/A") + "</li>"
                    + "</ul>"
                    + "<p>Por favor, revisa la reserva en tu panel de administración.</p>"
                    + "<p>Saludos,<br/>El equipo de ¿Dónde Juego?</p>"
                    + "</body></html>";

            helper.setText(htmlContent, true); // true indica que el contenido es HTML
            mailSender.send(message);
            log.info("Notificación de nueva reserva enviada a: {}", adminEmailAddress); // Uso del logger
        } catch (Exception e) {
            log.error("Error al enviar email de notificación de nueva reserva al administrador {}: {}", adminEmailAddress, e.getMessage(), e); // Uso del logger
        }
    }
}