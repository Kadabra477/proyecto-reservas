package com.example.reservafutbol.Servicio;

import com.example.reservafutbol.Modelo.Reserva;
import com.itextpdf.text.*;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.draw.LineSeparator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.time.ZoneId;
import java.util.Date;
import java.util.Locale;

@Service
public class PdfGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(PdfGeneratorService.class);

    public ByteArrayInputStream generarPDFReserva(Reserva reserva) throws DocumentException, IOException {
        log.info("Generando PDF para Reserva ID: {}", reserva.getId());
        Document doc = new Document(PageSize.A4, 50, 50, 50, 50);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            doc.open();

            // --- Definición de Fuentes y Colores ---
            // Usando colores de tu diseño: Primary (azul) y otros grises/negros
            BaseColor primaryColor = new BaseColor(46, 116, 181); // var(--primary)
            BaseColor primaryDarkColor = new BaseColor(30, 80, 130); // Un poco más oscuro que --primary
            BaseColor darkTextColor = new BaseColor(51, 51, 51); // var(--text-dark) o similar
            BaseColor lightTextColor = new BaseColor(102, 102, 102); // var(--text-light) o similar
            BaseColor lineColor = new BaseColor(200, 200, 200);

            BaseFont bfHelveticaBold = BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.WINANSI, BaseFont.EMBEDDED);
            BaseFont bfHelvetica = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.EMBEDDED);

            // Fuente para el texto del logo (más grande y con tu color principal)
            Font logoTextFont = new Font(bfHelveticaBold, 28, Font.NORMAL, primaryColor);
            Font mainTitleFont = new Font(bfHelveticaBold, 22, Font.NORMAL, darkTextColor);
            Font sectionTitleFont = new Font(bfHelveticaBold, 14, Font.NORMAL, primaryDarkColor); // Títulos de sección en color principal oscuro
            Font headerFooterFont = new Font(bfHelvetica, 9, Font.ITALIC, lightTextColor);
            Font labelFont = new Font(bfHelveticaBold, 10, Font.NORMAL, darkTextColor);
            Font valueFont = new Font(bfHelvetica, 10, Font.NORMAL, BaseColor.BLACK);
            Font totalAmountLabelFont = new Font(bfHelveticaBold, 16, Font.NORMAL, darkTextColor);
            Font totalAmountValueFont = new Font(bfHelveticaBold, 18, Font.NORMAL, primaryColor);
            Font importantNoteFont = new Font(bfHelveticaBold, 10, Font.NORMAL, BaseColor.RED);

            // --- Encabezado del documento: Texto del Logo y Título ---
            // Usar solo texto estilizado para el logo
            Paragraph logoText = new Paragraph("¿DÓNDE JUEGO?", logoTextFont);
            logoText.setAlignment(Element.ALIGN_CENTER);
            doc.add(logoText);
            doc.add(new Paragraph("\n")); // Espacio después del logo

            Paragraph headerTitle = new Paragraph("Comprobante de Reserva", mainTitleFont);
            headerTitle.setAlignment(Element.ALIGN_CENTER);
            headerTitle.setSpacingAfter(10f);
            doc.add(headerTitle);

            // Línea divisoria
            LineSeparator line = new LineSeparator();
            line.setLineColor(lineColor);
            line.setLineWidth(0.5f);
            doc.add(new Chunk(line));
            doc.add(new Paragraph("\n\n"));

            // --- Formateadores ---
            SimpleDateFormat formatter = new SimpleDateFormat("dd/MM/yyyy 'a las' HH:mm 'hs'");
            NumberFormat currencyFormatter = NumberFormat.getCurrencyInstance(new Locale("es", "AR"));

            // --- Sección de Datos de la Reserva ---
            doc.add(new Paragraph("Detalles de la Reserva", sectionTitleFont));
            doc.add(new Paragraph("\n"));

            PdfPTable tablaDetalles = new PdfPTable(2);
            tablaDetalles.setWidthPercentage(100);
            tablaDetalles.setWidths(new float[]{1.5f, 3f});
            tablaDetalles.setSpacingBefore(5f);
            tablaDetalles.getDefaultCell().setBorder(Rectangle.NO_BORDER);

            addKeyValueRow(tablaDetalles, "ID Reserva:", String.valueOf(reserva.getId()), labelFont, valueFont);
            addKeyValueRow(tablaDetalles, "Cancha:", reserva.getCancha() != null && reserva.getCancha().getNombre() != null ? reserva.getCancha().getNombre() : "N/A", labelFont, valueFont);
            if (reserva.getCancha() != null && reserva.getCancha().getUbicacion() != null) {
                addKeyValueRow(tablaDetalles, "Ubicación:", reserva.getCancha().getUbicacion(), labelFont, valueFont);
            }
            if (reserva.getFechaHora() != null) {
                Date fechaHoraDate = Date.from(reserva.getFechaHora().atZone(ZoneId.systemDefault()).toInstant());
                addKeyValueRow(tablaDetalles, "Fecha y Hora:", formatter.format(fechaHoraDate), labelFont, valueFont);
            } else {
                addKeyValueRow(tablaDetalles, "Fecha y Hora:", "N/A", labelFont, valueFont);
            }
            doc.add(tablaDetalles);

            doc.add(new Paragraph("\n"));
            doc.add(new Chunk(line));
            doc.add(new Paragraph("\n\n"));

            // --- Sección de Datos del Cliente ---
            doc.add(new Paragraph("Datos del Cliente", sectionTitleFont));
            doc.add(new Paragraph("\n"));

            PdfPTable tablaCliente = new PdfPTable(2);
            tablaCliente.setWidthPercentage(100);
            tablaCliente.setWidths(new float[]{1.5f, 3f});
            tablaCliente.setSpacingBefore(5f);
            tablaCliente.getDefaultCell().setBorder(Rectangle.NO_BORDER);

            addKeyValueRow(tablaCliente, "Cliente:", reserva.getCliente() != null ? reserva.getCliente() : "N/A", labelFont, valueFont);
            addKeyValueRow(tablaCliente, "Email Usuario:", reserva.getUsuario() != null && reserva.getUsuario().getUsername() != null ? reserva.getUsuario().getUsername() : "N/A", labelFont, valueFont);
            addKeyValueRow(tablaCliente, "Teléfono:", reserva.getTelefono() != null ? reserva.getTelefono() : "N/A", labelFont, valueFont);
            doc.add(tablaCliente);

            doc.add(new Paragraph("\n"));
            doc.add(new Chunk(line));
            doc.add(new Paragraph("\n\n"));

            // --- Sección de Información de Pago ---
            doc.add(new Paragraph("Información de Pago", sectionTitleFont));
            doc.add(new Paragraph("\n"));

            PdfPTable tablaPago = new PdfPTable(2);
            tablaPago.setWidthPercentage(100);
            tablaPago.setWidths(new float[]{1.5f, 3f});
            tablaPago.setSpacingBefore(5f);
            tablaPago.getDefaultCell().setBorder(Rectangle.NO_BORDER);

            String estadoTexto;
            if (Boolean.TRUE.equals(reserva.getPagada())) {
                estadoTexto = "Pagada";
            } else if ("efectivo".equalsIgnoreCase(reserva.getMetodoPago()) && Boolean.TRUE.equals(reserva.getConfirmada())) {
                estadoTexto = "Confirmada (Pendiente de Pago en Efectivo)";
            } else if ("mercadopago".equalsIgnoreCase(reserva.getMetodoPago()) && !Boolean.TRUE.equals(reserva.getPagada())) {
                estadoTexto = "Pendiente de Pago (Mercado Pago)";
            } else {
                estadoTexto = "Pendiente de Confirmación";
            }
            addKeyValueRow(tablaPago, "Estado Reserva:", estadoTexto, labelFont, valueFont);
            addKeyValueRow(tablaPago, "Método de Pago:", reserva.getMetodoPago() != null ? reserva.getMetodoPago() : "No especificado", labelFont, valueFont);
            addKeyValueRow(tablaPago, "ID Pago MP:", reserva.getMercadoPagoPaymentId() != null ? reserva.getMercadoPagoPaymentId() : "-", labelFont, valueFont);
            doc.add(tablaPago);

            doc.add(new Paragraph("\n"));
            doc.add(new Chunk(line));
            doc.add(new Paragraph("\n\n"));

            // --- Monto Total (resaltado) ---
            PdfPTable tablaMontoTotal = new PdfPTable(2);
            tablaMontoTotal.setWidthPercentage(100);
            tablaMontoTotal.setWidths(new float[]{2f, 1f});
            tablaMontoTotal.getDefaultCell().setBorder(Rectangle.NO_BORDER);

            PdfPCell totalLabelCell = new PdfPCell(new Phrase("Monto Total:", totalAmountLabelFont));
            totalLabelCell.setBorder(Rectangle.NO_BORDER);
            totalLabelCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            totalLabelCell.setPadding(4f);
            tablaMontoTotal.addCell(totalLabelCell);

            PdfPCell totalValueCell = new PdfPCell(new Phrase(reserva.getPrecio() != null ? currencyFormatter.format(reserva.getPrecio()) : "N/A", totalAmountValueFont));
            totalValueCell.setBorder(Rectangle.NO_BORDER);
            totalValueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            totalValueCell.setPadding(4f);
            tablaMontoTotal.addCell(totalValueCell);
            doc.add(tablaMontoTotal);

            doc.add(new Paragraph("\n"));
            doc.add(new Chunk(line));
            doc.add(new Paragraph("\n\n"));


            // --- Nota Importante ---
            Paragraph importantNote = new Paragraph("IMPORTANTE: Deberás presentar este comprobante (impreso o digital) al llegar a la cancha.", importantNoteFont);
            importantNote.setAlignment(Element.ALIGN_CENTER);
            doc.add(importantNote);

            // --- Pie de página (Número de página y fecha de generación) ---
            Paragraph footer = new Paragraph("Generado el " + new SimpleDateFormat("dd/MM/yyyy HH:mm").format(new Date()), headerFooterFont);
            footer.setAlignment(Element.ALIGN_RIGHT);
            footer.setSpacingBefore(20f);
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

    // Método helper para añadir filas clave-valor a la tabla con fuentes específicas
    private void addKeyValueRow(PdfPTable table, String label, String value, Font labelFont, Font valueFont) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, labelFont));
        labelCell.setBorder(Rectangle.NO_BORDER);
        labelCell.setPadding(4f);
        table.addCell(labelCell);

        PdfPCell valueCell = new PdfPCell(new Phrase(value, valueFont));
        valueCell.setBorder(Rectangle.NO_BORDER);
        valueCell.setPadding(4f);
        table.addCell(valueCell);
    }
}