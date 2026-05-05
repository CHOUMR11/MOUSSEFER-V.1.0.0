package com.moussefer.payment.service;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.moussefer.payment.entity.Payment;
import com.moussefer.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentAsyncHandler {

    private final PaymentRepository paymentRepository;
    private final InvoiceStorageService invoiceStorageService;

    @Async
    @Transactional
    public void generateAndStoreInvoice(String paymentId) {
        Payment payment = paymentRepository.findById(paymentId).orElse(null);
        if (payment == null) {
            log.error("Payment not found for invoice generation: {}", paymentId);
            return;
        }

        try {
            byte[] pdfBytes = generatePdf(payment);
            String fileName = "invoice_" + payment.getReservationId() + ".pdf";
            String invoiceUrl = invoiceStorageService.uploadIfEnabled(fileName, pdfBytes);
            if (invoiceUrl != null) {
                payment.setInvoiceUrl(invoiceUrl);
                paymentRepository.save(payment);
                log.info("Invoice generated and stored for payment: {}", paymentId);
            } else {
                log.warn("Invoice generation skipped (MinIO disabled or upload failed) for payment: {}", paymentId);
            }
        } catch (Exception e) {
            log.error("Failed to generate invoice for payment {}: {}", paymentId, e.getMessage());
        }
    }

    private byte[] generatePdf(Payment payment) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter writer = new PdfWriter(baos);
        PdfDocument pdf = new PdfDocument(writer);
        Document document = new Document(pdf);

        document.add(new Paragraph("Moussefer - Facture de réservation")
                .setBold().setFontSize(18));
        document.add(new Paragraph("Réservation n°: " + payment.getReservationId()));
        document.add(new Paragraph("Date: " + payment.getCreatedAt().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))));
        document.add(new Paragraph(" "));
        Table table = new Table(2);
        table.addCell("Description");
        table.addCell("Montant");
        table.addCell("Trajet");
        table.addCell(String.format("%.2f %s", payment.getAmount(), payment.getCurrency().toUpperCase()));
        if (payment.getPromoCode() != null && !payment.getPromoCode().isBlank()) {
            table.addCell("Code promo");
            table.addCell(payment.getPromoCode());
        }
        document.add(table);
        document.add(new Paragraph(" "));
        document.add(new Paragraph("Merci pour votre confiance."));
        document.close();
        return baos.toByteArray();
    }
}