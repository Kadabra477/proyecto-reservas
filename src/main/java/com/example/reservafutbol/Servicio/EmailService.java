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
import java.io.UnsupportedEncodingException;
import com.example.reservafutbol.Modelo.Reserva;
import com.example.reservafutbol.Modelo.User;


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
            InternetAddress tempParsedAddress = new InternetAddress(configuredFromEmail);

            if (tempParsedAddress.getPersonal() == null || tempParsedAddress.getPersonal().isEmpty()) {
                this.fromAddress = new InternetAddress(configuredFromEmail, "ReservaFutbol", "UTF-8");
            } else {
                this.fromAddress = new InternetAddress(tempParsedAddress.getAddress(), tempParsedAddress.getPersonal(), "UTF-8");
            }
        } catch (UnsupportedEncodingException | AddressException e) {
            System.err.println("Error al configurar la dirección de remitente ('FROM') del email. Asegúrate de que 'spring.mail.properties.mail.smtp.from' esté en un formato válido como 'Nombre <email@dominio.com>'. Fallback a 'no-reply@reservafutbol.com'. Error: " + e.getMessage());
            try {
                this.fromAddress = new InternetAddress("no-reply@reservafutbol.com", "ReservaFutbol", "UTF-8");
            } catch (UnsupportedEncodingException fallbackE) {
                System.err.println("Error crítico: Fallback de dirección de remitente también falló: " + fallbackE.getMessage());
                throw new RuntimeException("No se pudo inicializar EmailService debido a problemas con la dirección de remitente.", fallbackE);
            }
        }
    }


    // Método para enviar email de verificación de cuenta (usando MimeMessageHelper para HTML)
    public void sendVerificationEmail(String to, String username, String verificationToken) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(this.fromAddress);
        helper.setTo(to);
        helper.setSubject("Verifica tu cuenta en ¿Dónde Juego?");

        String verificationLink = frontendUrl + "/verify-account?token=" + verificationToken;

        String emailContent = "<html><body>"
                + "<h2>¡Hola, " + username + "!</h2>"
                + "<p>Gracias por registrarte en ¿Dónde Juego?.</p>"
                + "<p>Para activar tu cuenta, por favor haz clic en el siguiente enlace:</p>"
                + "<p><a href=\"" + verificationLink + "\">Verificar mi cuenta</a></p>"
                + "<p>Si no te registraste en nuestro sitio, por favor ignora este correo.</p>"
                + "<p>Saludos cordiales,<br/>El equipo de ¿Dónde Juego?</p>"
                + "</body></html>";
        helper.setText(emailContent, true);

        // *** CAMBIO: Se reintroduce el try-catch aquí para que EmailService capture MessagingException ***
        try {
            mailSender.send(message);
            System.out.println("Email de verificación enviado a: " + to);
        } catch (Exception e) { // Capturamos Exception genérica para loguear el detalle
            System.err.println("Error REAL al enviar email de verificación a " + to + ": " + e.getMessage());
            e.printStackTrace(); // <-- Imprimimos el stack trace completo
            throw new MessagingException("Fallo en el envío del correo de verificación: " + e.getMessage(), e); // Volvemos a lanzar como MessagingException
        }
    }

    // Método para enviar email de recuperación de contraseña (usando MimeMessageHelper para HTML)
    public void sendPasswordResetEmail(String to, String token) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(this.fromAddress);
        helper.setTo(to);
        helper.setSubject("Restablecer tu contraseña en ¿Dónde Juego?");

        String resetUrl = frontendUrl + "/reset-password?token=" + token;

        String emailContent = "<html><body>"
                + "<h2>Hola,</h2>"
                + "<p>Recibimos una solicitud para restablecer tu contraseña. Haz clic en el siguiente enlace:</p>"
                + "<p><a href=\"" + resetUrl + "\">Restablecer Contraseña</a></p>"
                + "<p>Este enlace expirará en 1 hora.</p>"
                + "<p>Si no solicitaste esto, puedes ignorar este mensaje.</p>"
                + "<p>Saludos,<br/>El equipo de ¿Dónde Juego?</p>"
                + "</body></html>";
        helper.setText(emailContent, true);

        // *** CAMBIO: Se reintroduce el try-catch aquí para que EmailService capture MessagingException ***
        try {
            mailSender.send(message);
            System.out.println(">>> Email de reseteo de contraseña enviado a: " + to);
        } catch (Exception e) { // Capturamos Exception genérica para loguear el detalle
            System.err.println("Error REAL al enviar email de reseteo a " + to + ": " + e.getMessage());
            e.printStackTrace(); // <-- Imprimimos el stack trace completo
            throw new MessagingException("Fallo en el envío del correo de reseteo: " + e.getMessage(), e); // Volvemos a lanzar como MessagingException
        }
    }

    // *** MÉTODO RE-AÑADIDO: sendNewReservationNotification ***
    public void sendNewReservationNotification(Reserva reserva, String toEmail) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(this.fromAddress);
        helper.setTo(toEmail);
        helper.setSubject("Nueva Reserva en " + reserva.getComplejo().getNombre() + " - ¿Dónde Juego?");

        String emailContent = "<html><body>"
                + "<h2>¡Nueva Reserva Realizada!</h2>"
                + "<p>Se ha realizado una nueva reserva para tu complejo:</p>"
                + "<ul>"
                + "<li><strong>Complejo:</strong> " + reserva.getComplejo().getNombre() + "</li>"
                + "<li><strong>Tipo de Cancha:</strong> " + reserva.getTipoCanchaReservada() + "</li>"
                + "<li><strong>Fecha y Hora:</strong> " + reserva.getFechaHora().toString() + "</li>"
                + "<li><strong>Cliente:</strong> " + reserva.getCliente() + "</li>"
                + "<li><strong>Teléfono:</strong> " + reserva.getTelefono() + "</li>"
                + "<li><strong>DNI:</strong> " + reserva.getDni() + "</li>"
                + "<li><strong>Método de Pago:</strong> " + reserva.getMetodoPago() + "</li>"
                + "<li><strong>Precio:</strong> $" + reserva.getPrecio().toString() + "</li>"
                + "</ul>"
                + "<p>Por favor, revisa el panel de administración para más detalles.</p>"
                + "<p>Saludos,<br/>El equipo de ¿Dónde Juego?</p>"
                + "</body></html>";
        helper.setText(emailContent, true);

        // *** CAMBIO: Se reintroduce el try-catch aquí para que EmailService capture MessagingException ***
        try {
            mailSender.send(message);
            System.out.println("Notificación de nueva reserva enviada a: " + toEmail);
        } catch (Exception e) { // Capturamos Exception genérica para loguear el detalle
            System.err.println("Error REAL al enviar notificación de nueva reserva a " + toEmail + ": " + e.getMessage());
            e.printStackTrace(); // <-- Imprimimos el stack trace completo
            throw new MessagingException("Fallo en el envío de notificación de reserva: " + e.getMessage(), e); // Volvemos a lanzar como MessagingException
        }
    }


    // Método para enviar comprobante con PDF
    public void enviarComprobanteConPDF(String to, ByteArrayInputStream pdfBytes) throws MessagingException {
        MimeMessage mensaje = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mensaje, true);

        helper.setFrom(this.fromAddress);
        helper.setTo(to);
        helper.setSubject("Comprobante de reserva de cancha");
        helper.setText("Hola! Te adjuntamos el comprobante de tu reserva. Mostralo al llegar. ¡Gracias por reservar!");

        InputStreamSource adjunto = new ByteArrayResource(pdfBytes.readAllBytes());
        helper.addAttachment("comprobante_reserva.pdf", adjunto);

        // *** CAMBIO: Se reintroduce el try-catch aquí para que EmailService capture MessagingException ***
        try {
            mailSender.send(mensaje);
            System.out.println(">>> Comprobante enviado por email a: " + to);
        } catch (Exception e) { // Capturamos Exception genérica para loguear el detalle
            System.err.println("Error REAL al enviar comprobante PDF a " + to + ": " + e.getMessage());
            e.printStackTrace(); // <-- Imprimimos el stack trace completo
            throw new MessagingException("Fallo en el envío del comprobante PDF: " + e.getMessage(), e); // Volvemos a lanzar como MessagingException
        }
    }
}