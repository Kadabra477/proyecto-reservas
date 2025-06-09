package com.example.reservafutbol.Servicio;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.AddressException; // Importamos específicamente AddressException
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
import com.example.reservafutbol.Modelo.Reserva; // Asegúrate de importar Reserva si la usas
import com.example.reservafutbol.Modelo.User;    // Asegúrate de importar User si la usas


@Service
public class EmailService {

    @Value("${backend.url}")
    private String backendUrl;

    @Value("${frontend.url}")
    private String frontendUrl;

    @Autowired
    private JavaMailSender mailSender;

    // Inyecta el email FROM configurado en application.properties (o variables de entorno)
    @Value("${spring.mail.properties.mail.smtp.from}")
    private String configuredFromEmail;

    private InternetAddress fromAddress; // Para almacenar la dirección formateada una vez

    // Constructor para inicializar la dirección de remitente
    @Autowired
    public EmailService(@Value("${spring.mail.properties.mail.smtp.from}") String configuredFromEmail) {
        try {
            // Intenta parsear la dirección de email tal como viene de la configuración.
            // InternetAddress.parse() lanza AddressException (subclase de MessagingException)
            InternetAddress tempParsedAddress = new InternetAddress(configuredFromEmail);

            // Si la dirección no tiene un nombre personal (ej. "email@dominio.com"),
            // añadir uno por defecto y especificar UTF-8 para evitar errores de codificación.
            if (tempParsedAddress.getPersonal() == null || tempParsedAddress.getPersonal().isEmpty()) {
                this.fromAddress = new InternetAddress(configuredFromEmail, "ReservaFutbol", "UTF-8");
            } else {
                // Si ya tiene un nombre personal (ej. "Mi App <email@dominio.com>"),
                // volver a crear la InternetAddress asegurando la codificación UTF-8 para el nombre personal.
                this.fromAddress = new InternetAddress(tempParsedAddress.getAddress(), tempParsedAddress.getPersonal(), "UTF-8");
            }
        } catch (UnsupportedEncodingException | AddressException e) { // Capturamos AddressException
            System.err.println("Error al configurar la dirección de remitente ('FROM') del email. Asegúrate de que 'spring.mail.properties.mail.smtp.from' esté en un formato válido como 'Nombre <email@dominio.com>'. Fallback a 'no-reply@reservafutbol.com'. Error: " + e.getMessage());
            try {
                // Si la configuración inicial falla, intenta con una dirección de fallback segura.
                this.fromAddress = new InternetAddress("no-reply@reservafutbol.com", "ReservaFutbol", "UTF-8");
            } catch (UnsupportedEncodingException fallbackE) { // Aquí MessagingException puede ser lanzado por el constructor si el formato es inválido
                System.err.println("Error crítico: Fallback de dirección de remitente también falló: " + fallbackE.getMessage());
                throw new RuntimeException("No se pudo inicializar EmailService debido a problemas con la dirección de remitente.", fallbackE);
            }
        }
    }


    // Método para enviar email de verificación de cuenta (usando MimeMessageHelper para HTML)
    public void sendVerificationEmail(String to, String username, String verificationToken) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(this.fromAddress); // Usamos la dirección de remitente formateada
        helper.setTo(to);
        helper.setSubject("Verifica tu cuenta en ¿Dónde Juego?");

        String verificationLink = frontendUrl + "/verify-account?token=" + verificationToken; // Ajusta a tu ruta de frontend

        String emailContent = "<html><body>"
                + "<h2>¡Hola, " + username + "!</h2>"
                + "<p>Gracias por registrarte en ¿Dónde Juego?.</p>"
                + "<p>Para activar tu cuenta, por favor haz clic en el siguiente enlace:</p>"
                + "<p><a href=\"" + verificationLink + "\">Verificar mi cuenta</a></p>"
                + "<p>Si no te registraste en nuestro sitio, por favor ignora este correo.</p>"
                + "<p>Saludos cordiales,<br/>El equipo de ¿Dónde Juego?</p>"
                + "</body></html>";
        helper.setText(emailContent, true); // true para HTML

        mailSender.send(message);
        System.out.println("Email de verificación enviado a: " + to);
    }

    // Método para enviar email de recuperación de contraseña (usando MimeMessageHelper para HTML)
    public void sendPasswordResetEmail(String to, String token) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(this.fromAddress); // Usamos la dirección de remitente formateada
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
        helper.setText(emailContent, true); // true para HTML

        mailSender.send(message);
        System.out.println(">>> Email de reseteo de contraseña enviado a: " + to);
    }

    // *** MÉTODO RE-AÑADIDO: sendNewReservationNotification ***
    // Método para enviar notificaciones de nueva reserva (para administradores/dueños)
    public void sendNewReservationNotification(Reserva reserva, String toEmail) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(this.fromAddress); // Usamos la dirección de remitente formateada
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

        mailSender.send(message);
        System.out.println("Notificación de nueva reserva enviada a: " + toEmail);
    }


    // Método para enviar comprobante con PDF
    public void enviarComprobanteConPDF(String to, ByteArrayInputStream pdfBytes) throws MessagingException {
        MimeMessage mensaje = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mensaje, true);

        helper.setFrom(this.fromAddress); // Usamos la dirección de remitente formateada
        helper.setTo(to);
        helper.setSubject("Comprobante de reserva de cancha");
        helper.setText("Hola! Te adjuntamos el comprobante de tu reserva. Mostralo al llegar. ¡Gracias por reservar!");

        InputStreamSource adjunto = new ByteArrayResource(pdfBytes.readAllBytes());
        helper.addAttachment("comprobante_reserva.pdf", adjunto);

        mailSender.send(mensaje);
        System.out.println(">>> Comprobante enviado por email a: " + to);
    }
}