package com.example.reservafutbol.Servicio;

import com.example.reservafutbol.Modelo.Reserva;
import com.example.reservafutbol.Modelo.Complejo; // Importar Complejo
import com.example.reservafutbol.Modelo.User; // Importar User
import com.itextpdf.text.*;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;
import com.itextpdf.text.pdf.BaseFont; // Importar BaseFont
import com.itextpdf.text.pdf.draw.LineSeparator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.time.ZoneId;
import java.time.LocalDateTime; // Para LocalDateTime.now()
import java.util.Locale;

@Service
public class PdfGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(PdfGeneratorService.class);

    // Definir la zona horaria de Argentina
    private static final ZoneId ARGENTINA_ZONE_ID = ZoneId.of("America/Argentina/Buenos_Aires");

    public ByteArrayInputStream generarPDFReserva(Reserva reserva) throws DocumentException, IOException {
        log.info("Generando PDF para Reserva ID: {}", reserva.getId());
        Document doc = new Document(PageSize.A4, 50, 50, 50, 50); // Márgenes
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            doc.open();

            // --- Definición de Fuentes y Colores ---
            BaseColor primaryColor = new BaseColor(46, 116, 181); // Azul primario
            BaseColor primaryDarkColor = new BaseColor(30, 80, 130); // Azul oscuro
            BaseColor darkTextColor = new BaseColor(51, 51, 51);
            BaseColor lightTextColor = new BaseColor(102, 102, 102);
            BaseColor lineColor = new BaseColor(200, 200, 200);
            BaseColor headerBgColor = new BaseColor(240, 240, 240); // Color para fondo de encabezado

            // Asegurarse de que las fuentes estén disponibles. HELVETICA es una fuente estándar.
            BaseFont bfHelveticaBold = BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.WINANSI, BaseFont.EMBEDDED);
            // CORREGIDO: BaseBaseFont a BaseFont
            BaseFont bfHelvetica = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.EMBEDDED);

            Font logoTextFont = new Font(bfHelveticaBold, 28, Font.NORMAL, primaryColor);
            Font mainTitleFont = new Font(bfHelveticaBold, 22, Font.NORMAL, darkTextColor);
            Font sectionTitleFont = new Font(bfHelveticaBold, 14, Font.NORMAL, primaryDarkColor);
            Font headerFooterFont = new Font(bfHelvetica, 9, Font.ITALIC, lightTextColor);
            Font labelFont = new Font(bfHelveticaBold, 10, Font.NORMAL, darkTextColor);
            Font valueFont = new Font(bfHelvetica, 10, Font.NORMAL, BaseColor.BLACK);
            Font totalAmountLabelFont = new Font(bfHelveticaBold, 16, Font.NORMAL, darkTextColor);
            Font totalAmountValueFont = new Font(bfHelveticaBold, 18, Font.NORMAL, primaryColor);
            Font importantNoteFont = new Font(bfHelveticaBold, 10, Font.NORMAL, BaseColor.RED);
            Font smallInfoFont = new Font(bfHelvetica, 8, Font.ITALIC, lightTextColor);

            // --- Encabezado del documento ---
            PdfPTable headerTable = new PdfPTable(1);
            headerTable.setWidthPercentage(100);
            headerTable.setSpacingAfter(10f);

            PdfPCell headerCell = new PdfPCell();
            headerCell.setBorder(Rectangle.NO_BORDER);
            headerCell.setPadding(10f);
            headerCell.setBackgroundColor(headerBgColor);

            Paragraph logoParagraph = new Paragraph("¿DÓNDE JUEGO?", logoTextFont);
            logoParagraph.setAlignment(Element.ALIGN_CENTER);
            headerCell.addElement(logoParagraph);

            Paragraph titleParagraph = new Paragraph("Comprobante de Reserva", mainTitleFont);
            titleParagraph.setAlignment(Element.ALIGN_CENTER);
            headerCell.addElement(titleParagraph);

            headerTable.addCell(headerCell);
            doc.add(headerTable);

            // CORREGIDO: Usar LineSeparator directamente
            LineSeparator separator = new LineSeparator(); // Nueva instancia para evitar conflictos
            separator.setLineColor(lineColor);
            separator.setLineWidth(0.5f);
            doc.add(separator);
            doc.add(new Paragraph("\n\n"));


            // --- Formateadores ---
            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy 'a las' HH:mm 'hs'");
            NumberFormat currencyFormatter = NumberFormat.getCurrencyInstance(new Locale("es", "AR"));

            // --- Obtener datos de entidades relacionadas (para evitar LazyInit si no se hizo en service) ---
            Complejo complejo = reserva.getComplejo();
            User usuario = reserva.getUsuario();


            // --- Sección de Datos de la Reserva ---
            doc.add(new Paragraph("Detalles de la Reserva", sectionTitleFont));
            doc.add(new Paragraph("\n"));

            PdfPTable tablaDetalles = new PdfPTable(2);
            tablaDetalles.setWidthPercentage(100);
            tablaDetalles.setWidths(new float[]{1.5f, 3f});
            tablaDetalles.setSpacingBefore(5f);
            tablaDetalles.getDefaultCell().setBorder(Rectangle.NO_BORDER);
            tablaDetalles.getDefaultCell().setPadding(4f);

            addKeyValueRow(tablaDetalles, "ID Reserva:", String.valueOf(reserva.getId()), labelFont, valueFont);
            addKeyValueRow(tablaDetalles, "Complejo:", complejo != null ? complejo.getNombre() : "N/A", labelFont, valueFont);
            addKeyValueRow(tablaDetalles, "Tipo de Cancha:", reserva.getTipoCanchaReservada() != null ? reserva.getTipoCanchaReservada() : "N/A", labelFont, valueFont);
            addKeyValueRow(tablaDetalles, "Cancha Asignada:", reserva.getNombreCanchaAsignada() != null ? reserva.getNombreCanchaAsignada() : "PENDIENTE", labelFont, valueFont);

            if (complejo != null) {
                addKeyValueRow(tablaDetalles, "Ubicación Complejo:", complejo.getUbicacion() != null ? complejo.getUbicacion() : "N/A", labelFont, valueFont);
                addKeyValueRow(tablaDetalles, "Teléfono Complejo:", complejo.getTelefono() != null ? complejo.getTelefono() : "N/A", labelFont, valueFont);
            }

            if (reserva.getFechaHora() != null) {
                addKeyValueRow(tablaDetalles, "Fecha y Hora:", reserva.getFechaHora().atZone(ARGENTINA_ZONE_ID).format(dateFormatter), labelFont, valueFont);
            } else {
                addKeyValueRow(tablaDetalles, "Fecha y Hora:", "N/A", labelFont, valueFont);
            }
            doc.add(tablaDetalles);

            doc.add(new Paragraph("\n"));
            doc.add(separator); // CORREGIDO: Usar LineSeparator directamente
            doc.add(new Paragraph("\n\n"));

            // --- Sección de Datos del Cliente ---
            doc.add(new Paragraph("Datos del Cliente", sectionTitleFont));
            doc.add(new Paragraph("\n"));

            PdfPTable tablaCliente = new PdfPTable(2);
            tablaCliente.setWidthPercentage(100);
            tablaCliente.setWidths(new float[]{1.5f, 3f});
            tablaCliente.setSpacingBefore(5f);
            tablaCliente.getDefaultCell().setBorder(Rectangle.NO_BORDER);
            tablaCliente.getDefaultCell().setPadding(4f);

            addKeyValueRow(tablaCliente, "Nombre Cliente:", reserva.getCliente() != null ? reserva.getCliente() : "N/A", labelFont, valueFont);
            addKeyValueRow(tablaCliente, "DNI:", reserva.getDni() != null ? reserva.getDni() : "N/A", labelFont, valueFont);
            addKeyValueRow(tablaCliente, "Email Cliente:", usuario != null && usuario.getUsername() != null ? usuario.getUsername() : reserva.getUserEmail(), labelFont, valueFont);
            addKeyValueRow(tablaCliente, "Teléfono Cliente:", reserva.getTelefono() != null ? reserva.getTelefono() : "N/A", labelFont, valueFont);
            doc.add(tablaCliente);

            doc.add(new Paragraph("\n"));
            doc.add(separator); // CORREGIDO: Usar LineSeparator directamente
            doc.add(new Paragraph("\n\n"));

            // --- Sección de Información de Pago ---
            doc.add(new Paragraph("Información de Pago", sectionTitleFont));
            doc.add(new Paragraph("\n"));

            PdfPTable tablaPago = new PdfPTable(2);
            tablaPago.setWidthPercentage(100);
            tablaPago.setWidths(new float[]{1.5f, 3f});
            tablaPago.setSpacingBefore(5f);
            tablaPago.getDefaultCell().setBorder(Rectangle.NO_BORDER);
            tablaPago.getDefaultCell().setPadding(4f);

            String estadoTexto;
            if (Boolean.TRUE.equals(reserva.getPagada())) {
                estadoTexto = "Pagada";
            } else if ("pendiente_pago_efectivo".equalsIgnoreCase(reserva.getEstado())) {
                estadoTexto = "Pendiente de Pago (Efectivo)";
            } else if ("pendiente_pago_mp".equalsIgnoreCase(reserva.getEstado())) {
                estadoTexto = "Pendiente de Pago (Mercado Pago)";
            } else if ("cancelada".equalsIgnoreCase(reserva.getEstado())) {
                estadoTexto = "Cancelada";
            } else if ("rechazada_pago_mp".equalsIgnoreCase(reserva.getEstado())) {
                estadoTexto = "Rechazada (Mercado Pago)";
            }
            else {
                estadoTexto = "Pendiente";
            }

            addKeyValueRow(tablaPago, "Estado de Pago:", estadoTexto, labelFont, valueFont);
            addKeyValueRow(tablaPago, "Método Seleccionado:", reserva.getMetodoPago() != null ? reserva.getMetodoPago().toUpperCase() : "No especificado", labelFont, valueFont);

            if (reserva.getMercadoPagoPaymentId() != null && !reserva.getMercadoPagoPaymentId().isEmpty()) {
                addKeyValueRow(tablaPago, "ID Transacción MP:", reserva.getMercadoPagoPaymentId(), labelFont, valueFont);
            }
            if (reserva.getFechaPago() != null) {
                addKeyValueRow(tablaPago, "Fecha de Pago:", reserva.getFechaPago().atZone(ARGENTINA_ZONE_ID).format(dateFormatter), labelFont, valueFont);
            }
            doc.add(tablaPago);

            doc.add(new Paragraph("\n"));
            doc.add(separator); // CORREGIDO: Usar LineSeparator directamente
            doc.add(new Paragraph("\n\n"));


            // --- Monto Total (resaltado y centrado) ---
            PdfPTable tablaMontoTotal = new PdfPTable(1);
            tablaMontoTotal.setWidthPercentage(100);
            tablaMontoTotal.setSpacingBefore(10f);
            tablaMontoTotal.setSpacingAfter(20f);

            PdfPCell totalCell = new PdfPCell();
            totalCell.setBorder(Rectangle.NO_BORDER);
            totalCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            totalCell.setPadding(8f);

            Paragraph totalLabel = new Paragraph("Monto Total:", totalAmountLabelFont);
            totalLabel.setAlignment(Element.ALIGN_CENTER);
            totalCell.addElement(totalLabel);

            Paragraph totalValue = new Paragraph(reserva.getPrecio() != null ? currencyFormatter.format(reserva.getPrecio()) : "N/A", totalAmountValueFont);
            totalValue.setAlignment(Element.ALIGN_CENTER);
            totalCell.addElement(totalValue);

            tablaMontoTotal.addCell(totalCell);
            doc.add(tablaMontoTotal);

            doc.add(new Paragraph("\n"));
            doc.add(separator); // CORREGIDO: Usar LineSeparator directamente
            doc.add(new Paragraph("\n\n"));


            // --- Instrucciones/Notas Importantes según el estado ---
            if (Boolean.TRUE.equals(reserva.getPagada())) {
                Paragraph paidNote = new Paragraph("¡Gracias por tu reserva y pago! Tu cancha está confirmada.", new Font(bfHelveticaBold, 10, Font.NORMAL, new BaseColor(40, 167, 69)));
                paidNote.setAlignment(Element.ALIGN_CENTER);
                doc.add(paidNote);
            } else if ("pendiente_pago_efectivo".equalsIgnoreCase(reserva.getEstado())) {
                Paragraph efectivoNote = new Paragraph("Tu reserva ha sido creada y está PENDIENTE DE PAGO.", importantNoteFont);
                efectivoNote.setAlignment(Element.ALIGN_CENTER);
                doc.add(efectivoNote);
                doc.add(new Paragraph("\n"));
                Paragraph instruccionEfectivo = new Paragraph("Instrucciones: Por favor, abona el monto total (" + (reserva.getPrecio() != null ? currencyFormatter.format(reserva.getPrecio()) : "N/A") + ") directamente en el complejo ANTES de usar la cancha. Tu reserva está sujeta a confirmación de pago.", smallInfoFont);
                instruccionEfectivo.setAlignment(Element.ALIGN_CENTER);
                doc.add(instruccionEfectivo);
            } else if ("pendiente_pago_mp".equalsIgnoreCase(reserva.getEstado())) {
                Paragraph mpNote = new Paragraph("Tu reserva está PENDIENTE DE PAGO en Mercado Pago.", importantNoteFont);
                mpNote.setAlignment(Element.ALIGN_CENTER);
                doc.add(mpNote);
                doc.add(new Paragraph("\n"));
                Paragraph instruccionMp = new Paragraph("Instrucciones: Por favor, completa el pago a través del enlace de Mercado Pago proporcionado en tu dashboard o correo electrónico. Tu reserva no estará confirmada hasta recibir el pago.", smallInfoFont);
                instruccionMp.setAlignment(Element.ALIGN_CENTER);
                doc.add(instruccionMp);
            } else if ("rechazada_pago_mp".equalsIgnoreCase(reserva.getEstado())) {
                Paragraph rejectedNote = new Paragraph("AVISO: El pago de tu reserva fue RECHAZADO por Mercado Pago. Tu reserva no está confirmada.", importantNoteFont);
                rejectedNote.setAlignment(Element.ALIGN_CENTER);
                doc.add(rejectedNote);
            } else if ("cancelada".equalsIgnoreCase(reserva.getEstado())) {
                Paragraph cancelledNote = new Paragraph("Esta reserva ha sido CANCELADA.", importantNoteFont);
                cancelledNote.setAlignment(Element.ALIGN_CENTER);
                doc.add(cancelledNote);
            } else {
                Paragraph genericNote = new Paragraph("Esta reserva está pendiente de confirmación. Revisa el estado en tu dashboard.", importantNoteFont);
                genericNote.setAlignment(Element.ALIGN_CENTER);
                doc.add(genericNote);
            }

            doc.add(new Paragraph("\n"));
            Paragraph finalImportantNote = new Paragraph("IMPORTANTE: Deberás presentar este comprobante (impreso o digital) al llegar al complejo.", importantNoteFont); // Usar otra variable para no conflictuar
            finalImportantNote.setAlignment(Element.ALIGN_CENTER);
            doc.add(finalImportantNote);

            // --- Pie de página (Número de página y fecha de generación) ---
            doc.add(new Paragraph("\n\n\n")); // Espacio para el pie de página

            // Usa LocalDateTime.now(ARGENTINA_ZONE_ID) para la hora de generación del PDF
            Paragraph footer = new Paragraph("Comprobante generado el " + LocalDateTime.now(ARGENTINA_ZONE_ID).format(dateFormatter) + " | ID de Reserva: " + reserva.getId(), headerFooterFont);
            footer.setAlignment(Element.ALIGN_CENTER);
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