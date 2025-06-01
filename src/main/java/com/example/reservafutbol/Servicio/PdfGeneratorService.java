package com.example.reservafutbol.Servicio;

import com.example.reservafutbol.Modelo.Reserva;
import com.itextpdf.text.*;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.time.ZoneId; // Importar ZoneId
import java.util.Date; // Importar Date
import java.util.Locale;

@Service
public class PdfGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(PdfGeneratorService.class);

    public ByteArrayInputStream generarPDFReserva(Reserva reserva) throws DocumentException {
        log.info("Generando PDF para Reserva ID: {}", reserva.getId());
        Document doc = new Document(PageSize.A4);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            PdfWriter.getInstance(doc, out);
            doc.open();

            // --- Fuentes ---
            Font tituloFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, BaseColor.DARK_GRAY);
            Font subtituloFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, BaseColor.BLACK);
            Font textoFont = FontFactory.getFont(FontFactory.HELVETICA, 10, BaseColor.BLACK);
            Font importanteFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, BaseColor.RED);

            // --- Título ---
            Paragraph titulo = new Paragraph("Comprobante de Reserva - ¿Dónde Juego?", tituloFont);
            titulo.setAlignment(Element.ALIGN_CENTER);
            titulo.setSpacingAfter(20f);
            doc.add(titulo);

            // --- Formateadores ---
            SimpleDateFormat formatter = new SimpleDateFormat("dd/MM/yyyy 'a las' HH:mm 'hs'");
            NumberFormat currencyFormatter = NumberFormat.getCurrencyInstance(new Locale("es", "AR"));

            // --- Tabla con Detalles ---
            PdfPTable tabla = new PdfPTable(2);
            tabla.setWidthPercentage(90);
            tabla.setWidths(new float[]{1f, 2f});
            tabla.setSpacingBefore(10f);

            // Helper para añadir celdas
            addCell(tabla, "ID Reserva:", subtituloFont);
            addCell(tabla, String.valueOf(reserva.getId()), textoFont);

            addCell(tabla, "Cliente:", subtituloFont);
            addCell(tabla, reserva.getCliente() != null ? reserva.getCliente() : "N/A", textoFont);

            addCell(tabla, "Email Usuario:", subtituloFont);
            // Asegurarse de que el usuario no sea nulo antes de intentar getUserEmail
            addCell(tabla, reserva.getUsuario() != null && reserva.getUsuario().getUsername() != null ? reserva.getUsuario().getUsername() : "N/A", textoFont);

            addCell(tabla, "Teléfono:", subtituloFont);
            addCell(tabla, reserva.getTelefono() != null ? reserva.getTelefono() : "N/A", textoFont);

            addCell(tabla, "Cancha:", subtituloFont);
            // Asegurarse de que la cancha no sea nula antes de intentar getNombre
            addCell(tabla, reserva.getCancha() != null && reserva.getCancha().getNombre() != null ? reserva.getCancha().getNombre() : "N/A", textoFont);

            addCell(tabla, "Fecha y Hora:", subtituloFont);
            // MODIFICADO: Convertir LocalDateTime a Date para SimpleDateFormat
            if (reserva.getFechaHora() != null) {
                Date fechaHoraDate = Date.from(reserva.getFechaHora().atZone(ZoneId.systemDefault()).toInstant());
                addCell(tabla, formatter.format(fechaHoraDate), textoFont);
            } else {
                addCell(tabla, "N/A", textoFont);
            }

            // --- Estado y Pago ---
            String estadoTexto;
            if (Boolean.TRUE.equals(reserva.getPagada())) {
                estadoTexto = "Pagada";
            } else if ("efectivo".equalsIgnoreCase(reserva.getMetodoPago()) && Boolean.TRUE.equals(reserva.getConfirmada())) {
                estadoTexto = "Confirmada (Pendiente de Pago en Efectivo)";
            } else if ("mercadopago".equalsIgnoreCase(reserva.getMetodoPago()) && !Boolean.TRUE.equals(reserva.getPagada())) {
                estadoTexto = "Pendiente de Pago (Mercado Pago)";
            } else {
                estadoTexto = "Pendiente de Confirmación"; // Estado por defecto si no encaja
            }
            addCell(tabla, "Estado Reserva:", subtituloFont);
            addCell(tabla, estadoTexto, textoFont);

            addCell(tabla, "Método de Pago:", subtituloFont);
            addCell(tabla, reserva.getMetodoPago() != null ? reserva.getMetodoPago() : "No especificado", textoFont);

            addCell(tabla, "Monto:", subtituloFont);
            addCell(tabla, reserva.getPrecio() != null ? currencyFormatter.format(reserva.getPrecio()) : "N/A", textoFont);

            addCell(tabla, "ID Pago MP:", subtituloFont);
            addCell(tabla, reserva.getMercadoPagoPaymentId() != null ? reserva.getMercadoPagoPaymentId() : "-", textoFont);

            doc.add(tabla);

            // --- Mensaje Importante ---
            Paragraph footer = new Paragraph("IMPORTANTE: Deberás presentar este comprobante (impreso o digital) al llegar a la cancha.", importanteFont);
            footer.setAlignment(Element.ALIGN_CENTER);
            footer.setSpacingBefore(30f);
            doc.add(footer);

            log.info("Contenido del PDF generado para Reserva ID: {}", reserva.getId());

        } catch (DocumentException e) {
            log.error("Error de iTextPDF al generar PDF para reserva ID {}: {}", reserva.getId(), e.getMessage(), e);
            throw e;
        } finally {
            if (doc.isOpen()) {
                doc.close();
            }
        }

        return new ByteArrayInputStream(out.toByteArray());
    }

    private void addCell(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(4f);
        table.addCell(cell);
    }
}