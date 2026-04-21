package com.moussefer.voyage.service;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.moussefer.voyage.entity.ReservationVoyage;
import com.moussefer.voyage.entity.Voyage;
import com.moussefer.voyage.repository.ReservationVoyageRepository;
import com.moussefer.voyage.repository.VoyageRepository;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
@Slf4j
public class InvoiceService {

    private final ReservationVoyageRepository reservationRepository;
    private final VoyageRepository voyageRepository;
    private final MinioClient minioClient;

    @Value("${minio.bucket:invoices}")
    private String bucketName;

    @Value("${minio.endpoint:http://localhost:9000}")
    private String minioEndpoint;

    @Async
    @Transactional
    public void generateAndStoreInvoice(String reservationId) {
        ReservationVoyage reservation = reservationRepository.findById(reservationId).orElse(null);
        if (reservation == null) {
            log.error("Reservation not found for invoice generation: {}", reservationId);
            return;
        }
        Voyage voyage = voyageRepository.findById(reservation.getVoyageId()).orElse(null);
        if (voyage == null) {
            log.warn("Voyage not found for reservation {}, skipping invoice", reservationId);
            return;
        }

        try {
            byte[] pdfBytes = generatePdf(reservation, voyage);
            String fileName = "voyage_invoice_" + reservation.getId() + ".pdf";
            uploadToMinio(fileName, pdfBytes);
            String invoiceUrl = getInvoiceUrl(fileName);
            reservation.setInvoiceUrl(invoiceUrl);
            reservationRepository.save(reservation);
            log.info("Invoice generated for reservation: {}", reservationId);
        } catch (Exception e) {
            log.error("Failed to generate invoice for reservation {}: {}", reservationId, e.getMessage());
        }
    }

    private byte[] generatePdf(ReservationVoyage reservation, Voyage voyage) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter writer = new PdfWriter(baos);
        PdfDocument pdf = new PdfDocument(writer);
        Document document = new Document(pdf);

        document.add(new Paragraph("Moussefer - Facture de voyage organisé")
                .setBold().setFontSize(18));
        document.add(new Paragraph("Réservation n°: " + reservation.getId()));
        document.add(new Paragraph("Voyage: " + voyage.getDepartureCity() + " → " + voyage.getArrivalCity()));
        document.add(new Paragraph("Date: " + voyage.getDepartureDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))));
        document.add(new Paragraph(" "));
        Table table = new Table(2);
        table.addCell("Description");
        table.addCell("Montant");
        table.addCell(reservation.getSeatsReserved() + " place(s) x " + voyage.getPricePerSeat() + " €");
        table.addCell(String.format("%.2f €", reservation.getTotalPrice()));
        document.add(table);
        document.add(new Paragraph(" "));
        document.add(new Paragraph("Merci pour votre confiance."));
        document.close();
        return baos.toByteArray();
    }

    private void uploadToMinio(String fileName, byte[] data) throws Exception {
        boolean bucketExists = minioClient.bucketExists(
                io.minio.BucketExistsArgs.builder().bucket(bucketName).build());
        if (!bucketExists) {
            minioClient.makeBucket(
                    io.minio.MakeBucketArgs.builder().bucket(bucketName).build());
        }
        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(bucketName)
                        .object(fileName)
                        .stream(new ByteArrayInputStream(data), data.length, -1)
                        .contentType("application/pdf")
                        .build());
    }

    private String getInvoiceUrl(String fileName) {
        // Use configurable endpoint (from application.yml or environment)
        return minioEndpoint + "/" + bucketName + "/" + fileName;
    }
}