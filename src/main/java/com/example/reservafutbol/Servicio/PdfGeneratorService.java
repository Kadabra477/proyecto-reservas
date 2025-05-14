package com.example.reservafutbol.Servicio;

import com.example.reservafutbol.Modelo.Reserva;
import com.itextpdf.text.*;
import com.itextpdf.text.pdf.PdfPTable; // Para tablas si quieres mejorar formato
import com.itextpdf.text.pdf.PdfPCell;  // Para celdas de tabla
import com.itextpdf.text.pdf.PdfWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.text.NumberFormat; // Para formatear moneda
import java.text.SimpleDateFormat;
import java.util.Locale;

@Service
public class PdfGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(PdfGeneratorService.class);

    public ByteArrayInputStream generarPDFReserva(Reserva r) throws DocumentException { // Cambiado Exception a DocumentException
        log.info("Generando PDF para Reserva ID: {}", r.getId());
        Document doc = new Document(PageSize.A4); // Tamaño A4
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
            titulo.setSpacingAfter(20f); // Espacio después del título
            doc.add(titulo);

            // --- Formateadores ---
            // Formato para fecha y hora local (Argentina)
            SimpleDateFormat formatter = new SimpleDateFormat("dd/MM/yyyy 'a las' HH:mm 'hs'");
            // Formato para moneda local (Argentina - ARS)
            NumberFormat currencyFormatter = NumberFormat.getCurrencyInstance(new Locale("es", "AR"));

            // --- Tabla con Detalles (mejor formato) ---
            PdfPTable tabla = new PdfPTable(2); // 2 columnas
            tabla.setWidthPercentage(90); // Ancho de la tabla
            tabla.setWidths(new float[]{1f, 2f}); // Ancho relativo de las columnas (1:2)
            tabla.setSpacingBefore(10f);

            // Helper para añadir celdas
            addCell(tabla, "ID Reserva:", subtituloFont);
            addCell(tabla, String.valueOf(r.getId()), textoFont);

            addCell(tabla, "Cliente:", subtituloFont);
            addCell(tabla, r.getCliente() != null ? r.getCliente() : "N/A", textoFont);

            addCell(tabla, "Email Usuario:", subtituloFont);
            addCell(tabla, r.getUserEmail() != null ? r.getUserEmail() : "N/A", textoFont);

            addCell(tabla, "Teléfono:", subtituloFont);
            addCell(tabla, r.getTelefono() != null ? r.getTelefono() : "N/A", textoFont);

            addCell(tabla, "Cancha:", subtituloFont);
            addCell(tabla, r.getCancha() != null && r.getCancha().getNombre() != null ? r.getCancha().getNombre() : "N/A", textoFont);

            addCell(tabla, "Fecha y Hora:", subtituloFont);
            addCell(tabla, r.getFechaHora() != null ? formatter.format(r.getFechaHora()) : "N/A", textoFont);

            // --- Estado y Pago ---
            String estadoTexto;
            if (Boolean.TRUE.equals(r.getPagada())) {
                estadoTexto = "Pagada";
            } else if (Boolean.TRUE.equals(r.getConfirmada())) {
                estadoTexto = "Confirmada (Pendiente de Pago)";
            } else {
                estadoTexto = "Pendiente de Confirmación";
            }
            addCell(tabla, "Estado Reserva:", subtituloFont);
            addCell(tabla, estadoTexto, textoFont);

            addCell(tabla, "Método de Pago:", subtituloFont);
            addCell(tabla, r.getMetodoPago() != null ? r.getMetodoPago() : "No especificado", textoFont);

            addCell(tabla, "Monto:", subtituloFont);
            addCell(tabla, r.getPrecio() != null ? currencyFormatter.format(r.getPrecio()) : "N/A", textoFont);

            addCell(tabla, "ID Pago MP:", subtituloFont);
            addCell(tabla, r.getMercadoPagoPaymentId() != null ? r.getMercadoPagoPaymentId() : "-", textoFont);

            doc.add(tabla); // Añadir tabla al documento

            // --- Mensaje Importante ---
            Paragraph footer = new Paragraph("IMPORTANTE: Deberás presentar este comprobante (impreso o digital) al llegar a la cancha.", importanteFont);
            footer.setAlignment(Element.ALIGN_CENTER);
            footer.setSpacingBefore(30f);
            doc.add(footer);

            log.info("Contenido del PDF generado para Reserva ID: {}", r.getId());

        } catch (DocumentException e) {
            log.error("Error de iTextPDF al generar PDF para reserva ID {}: {}", r.getId(), e.getMessage(), e);
            throw e; // Relanzar la excepción
        } finally {
            if (doc.isOpen()) {
                doc.close(); // Asegurarse de cerrar el documento
            }
        }

        return new ByteArrayInputStream(out.toByteArray());
    }

    // Método helper para crear y añadir celdas a la tabla
    private void addCell(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBorder(Rectangle.NO_BORDER); // Sin bordes
        cell.setPadding(4f); // Padding interno
        table.addCell(cell);
    }
}