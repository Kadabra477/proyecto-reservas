package com.example.reservafutbol.Servicio;

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

@Service
public class EmailService {

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
        } catch (Exception e) {
            System.err.println("Error al enviar email de validación a " + to + ": " + e.getMessage());
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
        } catch (Exception e) {
            System.err.println(">>> ERROR al enviar email de reseteo a " + to + ": " + e.getMessage());
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
        } catch (Exception e) {
            System.err.println(">>> ERROR al enviar comprobante PDF a " + to + ": " + e.getMessage());
        }
    }
}