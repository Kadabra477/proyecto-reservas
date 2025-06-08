package com.example.reservafutbol.Servicio;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import com.example.reservafutbol.Modelo.Reserva; // Asegúrate de importar Reserva si la usas aquí
import com.example.reservafutbol.Modelo.User;    // Asegúrate de importar User si la usas aquí

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    // Inyecta el email FROM configurado en application.properties (o variables de entorno)
    @Value("${spring.mail.properties.mail.smtp.from}")
    private String fromEmail;

    // Método para enviar email de verificación de cuenta
    public void sendVerificationEmail(String to, String username, String verificationToken) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(fromEmail); // Usa la dirección de remitente configurada
        helper.setTo(to);
        helper.setSubject("Verifica tu cuenta en ReservaFutbol");

        // Construye el enlace de verificación
        // Asegúrate de que tu frontend.url esté configurada correctamente en application.properties/Render
        // y apunte a tu frontend desplegado (ej. https://proyecto-reservas-frontend.vercel.app)
        String verificationLink = "https://proyecto-reservas-frontend.vercel.app" + "/verify-account?token=" + verificationToken; // Ajusta la URL de tu frontend

        String emailContent = "<html><body>"
                + "<h2>¡Hola, " + username + "!</h2>"
                + "<p>Gracias por registrarte en ReservaFutbol.</p>"
                + "<p>Para activar tu cuenta, por favor haz clic en el siguiente enlace:</p>"
                + "<p><a href=\"" + verificationLink + "\">Verificar mi cuenta</a></p>"
                + "<p>Si no te registraste en nuestro sitio, por favor ignora este correo.</p>"
                + "<p>Saludos cordiales,<br/>El equipo de ReservaFutbol</p>"
                + "</body></html>";
        helper.setText(emailContent, true); // true para HTML

        mailSender.send(message);
        System.out.println("Email de verificación enviado a: " + to);
    }

    // Método para enviar email de recuperación de contraseña
    public void sendPasswordResetEmail(String to, String resetToken) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(fromEmail);
        helper.setTo(to);
        helper.setSubject("Restablecer tu contraseña de ReservaFutbol");

        String resetLink = "https://proyecto-reservas-frontend.vercel.app" + "/reset-password?token=" + resetToken; // Ajusta la URL de tu frontend

        String emailContent = "<html><body>"
                + "<h2>Hola,</h2>"
                + "<p>Has solicitado restablecer tu contraseña en ReservaFutbol.</p>"
                + "<p>Haz clic en el siguiente enlace para restablecer tu contraseña:</p>"
                + "<p><a href=\"" + resetLink + "\">Restablecer Contraseña</a></p>"
                + "<p>Este enlace expirará en 15 minutos.</p>"
                + "<p>Si no solicitaste un restablecimiento de contraseña, por favor ignora este correo.</p>"
                + "<p>Saludos cordiales,<br/>El equipo de ReservaFutbol</p>"
                + "</body></html>";
        helper.setText(emailContent, true);

        mailSender.send(message);
        System.out.println("Email de restablecimiento de contraseña enviado a: " + to);
    }

    // Método para enviar notificaciones de nueva reserva (para administradores/dueños)
    public void sendNewReservationNotification(Reserva reserva, String toEmail) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(fromEmail);
        helper.setTo(toEmail);
        helper.setSubject("Nueva Reserva en " + reserva.getComplejo().getNombre());

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
                + "<p>Saludos,<br/>El equipo de ReservaFutbol</p>"
                + "</body></html>";
        helper.setText(emailContent, true);

        mailSender.send(message);
        System.out.println("Notificación de nueva reserva enviada a: " + toEmail);
    }

    // Otros métodos de correo (ej. confirmación al usuario, etc.)
}