package com.boulangerie.service;

import com.boulangerie.dao.VersementDAO;
import com.boulangerie.model.*;
import com.boulangerie.util.FormatUtil;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.*;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Service d'export PDF avec iText 7.
 * Couvre : Facture, Liste factures, Recouvrement, Soldes clients, Audit.
 */
public class PdfService {
    private static final Logger log = LoggerFactory.getLogger(PdfService.class);

    // Couleurs marque boulangerie
    private static final DeviceRgb BLEU       = new DeviceRgb(0x1A, 0x73, 0xE8);
    private static final DeviceRgb VERT       = new DeviceRgb(0x0F, 0x9D, 0x58);
    private static final DeviceRgb ROUGE      = new DeviceRgb(0xD9, 0x30, 0x25);
    private static final DeviceRgb GRIS_FOND  = new DeviceRgb(0xF4, 0xF6, 0xFA);
    private static final DeviceRgb GRIS_TEXTE = new DeviceRgb(0x5F, 0x63, 0x68);
    private static final DeviceRgb MARRON     = new DeviceRgb(0x6B, 0x3A, 0x2A);

    // ── Facture individuelle ─────────────────────────────────────
    public static void exporterFacture(Facture facture, List<LigneSortie> lignes, String cheminPdf) throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(cheminPdf));
             Document doc = new Document(pdfDoc, PageSize.A4)) {

            doc.setMargins(40, 50, 40, 50);

            PdfFont fontBold = getFont(true);
            PdfFont fontNorm = getFont(false);

            // ── En-tête entreprise ────────────────────────────────
            Table header = new Table(UnitValue.createPercentArray(new float[]{50, 50})).useAllAvailableWidth();
            Cell logoCell = new Cell().add(new Paragraph("🥖 BOULANGERIE")
                .setFont(fontBold).setFontSize(18).setFontColor(MARRON))
                .add(new Paragraph("Qualité & Tradition").setFont(fontNorm).setFontSize(10).setFontColor(GRIS_TEXTE))
                .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER);

            String clientNom = facture.getClient() != null ? facture.getClient().getNom() : "Client anonyme";
            String livreurNom = facture.getLivreur() != null ? facture.getLivreur().getNomComplet() : "—";
            Cell infoCell = new Cell()
                .add(new Paragraph("FACTURE").setFont(fontBold).setFontSize(20).setTextAlignment(TextAlignment.RIGHT))
                .add(new Paragraph("N° " + facture.getNumero()).setFont(fontNorm).setFontSize(11).setTextAlignment(TextAlignment.RIGHT))
                .add(new Paragraph("Date : " + FormatUtil.date(facture.getDateEmission())).setFont(fontNorm).setFontSize(10).setTextAlignment(TextAlignment.RIGHT))
                .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER);

            header.addCell(logoCell);
            header.addCell(infoCell);
            doc.add(header);
            doc.add(new LineSeparator(new com.itextpdf.kernel.pdf.canvas.draw.SolidLine()));
            doc.add(new Paragraph("\n").setFontSize(4));

            // ── Info client ───────────────────────────────────────
            Table infoTable = new Table(UnitValue.createPercentArray(new float[]{50, 50})).useAllAvailableWidth();
            infoTable.addCell(infoCell("Client :", clientNom, fontBold, fontNorm));
            infoTable.addCell(infoCell("Livreur :", livreurNom, fontBold, fontNorm));
            infoTable.addCell(infoCell("Mode de règlement :",
                facture.getModeReglement() != null ? facture.getModeReglement() : "Comptant", fontBold, fontNorm));
            infoTable.addCell(infoCell("Vente :", "Vente comptoir", fontBold, fontNorm));
            doc.add(infoTable);
            doc.add(new Paragraph("\n").setFontSize(6));

            // ── Tableau des lignes ────────────────────────────────
            Table lignesTable = new Table(UnitValue.createPercentArray(new float[]{40, 15, 20, 25}))
                .useAllAvailableWidth();

            // En-tête tableau
            for (String col : new String[]{"Désignation", "Qté nette", "Tarif unitaire (HT)", "Montant (HT)"}) {
                lignesTable.addHeaderCell(new Cell()
                    .add(new Paragraph(col).setFont(fontBold).setFontSize(10).setFontColor(ColorConstants.WHITE))
                    .setBackgroundColor(BLEU)
                    .setTextAlignment(TextAlignment.CENTER));
            }

            // Lignes filtrées pour ce client
            if (lignes != null) {
                for (LigneSortie l : lignes) {
                    if (facture.getClient() != null && l.getClient() != null
                            && !facture.getClient().getId().equals(l.getClient().getId())) continue;
                    if (l.getQuantiteNette() == 0) continue;

                    lignesTable.addCell(new Cell().add(new Paragraph(
                        l.getProduit() != null ? l.getProduit().getLibelle() : "—").setFont(fontNorm).setFontSize(10)));
                    lignesTable.addCell(new Cell().add(new Paragraph(String.valueOf(l.getQuantiteNette()))
                        .setFont(fontNorm).setFontSize(10)).setTextAlignment(TextAlignment.CENTER));
                    lignesTable.addCell(new Cell().add(new Paragraph(FormatUtil.montant(l.getTarifApplicable()))
                        .setFont(fontNorm).setFontSize(10)).setTextAlignment(TextAlignment.RIGHT));
                    lignesTable.addCell(new Cell().add(new Paragraph(FormatUtil.montant(l.getMontantHt()))
                        .setFont(fontNorm).setFontSize(10)).setTextAlignment(TextAlignment.RIGHT));
                }
            }

            // Remise si applicable
            if (facture.getNotes() != null && !facture.getNotes().isBlank()) {
                lignesTable.addCell(new Cell(1, 4).add(new Paragraph("Note : " + facture.getNotes())
                    .setFont(fontNorm).setFontSize(9).setFontColor(GRIS_TEXTE)));
            }

            doc.add(lignesTable);
            doc.add(new Paragraph("\n").setFontSize(4));

            // ── Totaux ────────────────────────────────────────────
            Table totauxTable = new Table(UnitValue.createPercentArray(new float[]{65, 35})).useAllAvailableWidth();
            totauxTable.addCell(new Cell().setBorder(com.itextpdf.layout.borders.Border.NO_BORDER));
            Table totaux = new Table(UnitValue.createPercentArray(new float[]{60, 40})).useAllAvailableWidth();
            addTotalRow(totaux, "Total HT",      FormatUtil.montant(facture.getMontantHt()), fontNorm, false);
            addTotalRow(totaux, "TVA (" + facture.getTvaPct() + "%)", FormatUtil.montant(facture.getTvaMontant()), fontNorm, false);
            addTotalRow(totaux, "Total TTC",     FormatUtil.montant(facture.getMontantTtc()), fontBold, true);
            totauxTable.addCell(new Cell().add(totaux).setBorder(com.itextpdf.layout.borders.Border.NO_BORDER));
            doc.add(totauxTable);

            // ── Pied de page ──────────────────────────────────────
            doc.add(new LineSeparator(new com.itextpdf.kernel.pdf.canvas.draw.SolidLine()));
            doc.add(new Paragraph(
                "Période : " + FormatUtil.date(facture.getDateEmission()) +
                " au " + FormatUtil.date(facture.getDateEmission()) +
                "   |   Utilisateur : " + (facture.getCreePar() != null ? facture.getCreePar() : "—") +
                "   |   Généré le : " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) +
                "   |   Page 1 / 1")
                .setFont(fontNorm).setFontSize(8).setFontColor(GRIS_TEXTE)
                .setTextAlignment(TextAlignment.CENTER));

            log.info("PDF facture exporté : {}", cheminPdf);
        }
    }

    // ── Liste des factures ───────────────────────────────────────
    public static void exporterListeFactures(List<Facture> factures, String cheminPdf) throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(cheminPdf));
             Document doc = new Document(pdfDoc, PageSize.A4.rotate())) {

            doc.setMargins(30, 30, 30, 30);
            PdfFont fontBold = getFont(true);
            PdfFont fontNorm = getFont(false);

            ajouterTitrePage(doc, "Liste des Factures", fontBold, fontNorm);

            Table table = new Table(UnitValue.createPercentArray(new float[]{15, 12, 22, 18, 14, 10, 12, 12}))
                .useAllAvailableWidth();

            for (String col : new String[]{"N° Facture","Date","Client","Livreur","Montant HT","TVA","TTC","Statut"}) {
                table.addHeaderCell(headerCell(col, fontBold));
            }

            for (Facture f : factures) {
                table.addCell(dataCell(f.getNumero(), fontNorm));
                table.addCell(dataCell(FormatUtil.date(f.getDateEmission()), fontNorm));
                table.addCell(dataCell(f.getClient() != null ? f.getClient().getNom() : "Anonyme", fontNorm));
                table.addCell(dataCell(f.getLivreur() != null ? f.getLivreur().getNomComplet() : "—", fontNorm));
                table.addCell(dataCellRight(FormatUtil.montant(f.getMontantHt()), fontNorm));
                table.addCell(dataCellRight(FormatUtil.montant(f.getTvaMontant()), fontNorm));
                table.addCell(dataCellRight(FormatUtil.montant(f.getMontantTtc()), fontBold));
                table.addCell(dataCell(f.getStatut().name(), fontNorm));
            }
            doc.add(table);
            ajouterPiedPage(doc, fontNorm);
        }
    }

    // ── Recouvrement mensuel ─────────────────────────────────────
    public static void exporterRecouvrement(VersementDAO versementDAO, LocalDate du, LocalDate au, String cheminPdf) throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(cheminPdf));
             Document doc = new Document(pdfDoc, PageSize.A4)) {

            doc.setMargins(40, 50, 40, 50);
            PdfFont fontBold = getFont(true);
            PdfFont fontNorm = getFont(false);

            ajouterTitrePage(doc, "Rapport de Recouvrement\nPériode : "
                + FormatUtil.date(du) + " — " + FormatUtil.date(au), fontBold, fontNorm);

            // Résumé global
            java.math.BigDecimal attendu    = versementDAO.getMontantAttenduJour(au);
            java.math.BigDecimal remis      = versementDAO.getMontantRemisJour(au);
            java.math.BigDecimal enregistre = versementDAO.getMontantEnregistreJour(au);
            java.math.BigDecimal ecart      = remis.subtract(enregistre);
            java.math.BigDecimal taux = attendu.compareTo(java.math.BigDecimal.ZERO) > 0
                ? enregistre.divide(attendu, 4, java.math.RoundingMode.HALF_UP)
                    .multiply(java.math.BigDecimal.valueOf(100)).setScale(2, java.math.RoundingMode.HALF_UP)
                : java.math.BigDecimal.ZERO;

            Table resume = new Table(UnitValue.createPercentArray(new float[]{40, 30, 30})).useAllAvailableWidth();
            resume.addHeaderCell(headerCell("Objectif (TTC)", fontBold));
            resume.addHeaderCell(headerCell("Réalisé (TTC)", fontBold));
            resume.addHeaderCell(headerCell("Taux de recouvrement", fontBold));
            resume.addCell(dataCellRight(FormatUtil.montant(attendu), fontNorm));
            resume.addCell(dataCellRight(FormatUtil.montant(enregistre), fontNorm));
            Cell tauxCell = new Cell().add(new Paragraph(taux + " %")
                .setFont(fontBold).setFontSize(14)
                .setFontColor(taux.compareTo(java.math.BigDecimal.valueOf(80)) >= 0 ? VERT : ROUGE)
                .setTextAlignment(TextAlignment.CENTER));
            resume.addCell(tauxCell);
            doc.add(resume);

            doc.add(new Paragraph("\n").setFontSize(6));
            doc.add(new Paragraph("Écart total : " + FormatUtil.montant(ecart))
                .setFont(fontBold).setFontSize(12)
                .setFontColor(ecart.compareTo(java.math.BigDecimal.ZERO) < 0 ? ROUGE : VERT));

            ajouterPiedPage(doc, fontNorm);
        }
    }

    // ── Soldes clients ───────────────────────────────────────────
    public static void exporterSoldesClients(List<Client> clients, String cheminPdf) throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(cheminPdf));
             Document doc = new Document(pdfDoc, PageSize.A4.rotate())) {

            doc.setMargins(30, 30, 30, 30);
            PdfFont fontBold = getFont(true);
            PdfFont fontNorm = getFont(false);

            ajouterTitrePage(doc, "État des Soldes Clients — " + FormatUtil.date(LocalDate.now()), fontBold, fontNorm);

            Table table = new Table(UnitValue.createPercentArray(new float[]{12, 25, 15, 15, 16, 17}))
                .useAllAvailableWidth();

            for (String col : new String[]{"Code","Nom","Catégorie","Délai (j)","Solde précédent","Solde actuel"}) {
                table.addHeaderCell(headerCell(col, fontBold));
            }

            java.math.BigDecimal totalSolde = java.math.BigDecimal.ZERO;
            for (Client cl : clients) {
                if (!cl.isNominatif()) continue;
                table.addCell(dataCell(cl.getCode(), fontNorm));
                table.addCell(dataCell(cl.getNom(), fontNorm));
                table.addCell(dataCell(cl.getCategorie() != null ? cl.getCategorie().getNom() : "—", fontNorm));
                table.addCell(dataCellRight(String.valueOf(cl.getDelaiPaiement()), fontNorm));
                table.addCell(dataCellRight(FormatUtil.montant(cl.getSoldePrecedent()), fontNorm));
                Cell soldeCell = new Cell().add(new Paragraph(FormatUtil.montant(cl.getSoldeActuel()))
                    .setFont(fontBold)
                    .setFontColor(cl.getSoldeActuel().compareTo(java.math.BigDecimal.ZERO) > 0 ? ROUGE : VERT)
                    .setTextAlignment(TextAlignment.RIGHT));
                table.addCell(soldeCell);
                totalSolde = totalSolde.add(cl.getSoldeActuel());
            }

            // Ligne total
            table.addFooterCell(new Cell(1, 5)
                .add(new Paragraph("TOTAL").setFont(fontBold))
                .setBackgroundColor(GRIS_FOND));
            table.addFooterCell(new Cell()
                .add(new Paragraph(FormatUtil.montant(totalSolde)).setFont(fontBold)
                    .setFontColor(totalSolde.compareTo(java.math.BigDecimal.ZERO) > 0 ? ROUGE : VERT)
                    .setTextAlignment(TextAlignment.RIGHT))
                .setBackgroundColor(GRIS_FOND));

            doc.add(table);
            ajouterPiedPage(doc, fontNorm);
        }
    }

    // ── Journal d'audit ──────────────────────────────────────────
    public static void exporterAudit(List<JournalAudit> audits, String cheminPdf) throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(cheminPdf));
             Document doc = new Document(pdfDoc, PageSize.A4.rotate())) {

            doc.setMargins(30, 30, 30, 30);
            PdfFont fontBold = getFont(true);
            PdfFont fontNorm = getFont(false);

            ajouterTitrePage(doc, "Journal d'Audit — Exporté le " + FormatUtil.dateHeure(LocalDateTime.now()),
                fontBold, fontNorm);

            Table table = new Table(UnitValue.createPercentArray(new float[]{18, 14, 10, 12, 12, 34}))
                .useAllAvailableWidth();

            for (String col : new String[]{"Date/Heure","Entité","Action","Utilisateur","ID Entité","Détails"}) {
                table.addHeaderCell(headerCell(col, fontBold));
            }

            for (JournalAudit a : audits) {
                table.addCell(dataCell(FormatUtil.dateHeure(a.getDateAction()), fontNorm));
                table.addCell(dataCell(a.getEntite(), fontNorm));
                table.addCell(dataCell(a.getAction(), fontNorm));
                table.addCell(dataCell(a.getLoginUtilisateur() != null ? a.getLoginUtilisateur() : "—", fontNorm));
                String id = a.getEntiteId() != null && a.getEntiteId().length() > 8
                    ? a.getEntiteId().substring(0, 8) + "…" : (a.getEntiteId() != null ? a.getEntiteId() : "—");
                table.addCell(dataCell(id, fontNorm));
                String det = a.getDetails() != null && a.getDetails().length() > 90
                    ? a.getDetails().substring(0, 90) + "…" : (a.getDetails() != null ? a.getDetails() : "—");
                table.addCell(dataCell(det, fontNorm));
            }

            doc.add(table);
            doc.add(new Paragraph("\nNote : Ce journal est en lecture seule. Aucune modification n'est possible.")
                .setFont(fontNorm).setFontSize(8).setFontColor(ROUGE));
            ajouterPiedPage(doc, fontNorm);
        }
    }

    // ── État journalier ──────────────────────────────────────────
    public static void exporterEtatJournalier(List<FicheJournaliere> fiches, String cheminPdf) throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(cheminPdf));
             Document doc = new Document(pdfDoc, PageSize.A4.rotate())) {

            doc.setMargins(30, 30, 30, 30);
            PdfFont fontBold = getFont(true);
            PdfFont fontNorm = getFont(false);

            ajouterTitrePage(doc, "État Journalier des Sorties/Retours — " + FormatUtil.date(LocalDate.now()),
                fontBold, fontNorm);

            Table table = new Table(UnitValue.createPercentArray(new float[]{15, 12, 22, 12, 12, 14, 14, 14}))
                .useAllAvailableWidth();
            for (String col : new String[]{"N° Fiche","Date","Livreur","État","Nb lignes","Total sorties","Total retours","Total net"}) {
                table.addHeaderCell(headerCell(col, fontBold));
            }

            java.math.BigDecimal totNet = java.math.BigDecimal.ZERO;
            for (FicheJournaliere f : fiches) {
                table.addCell(dataCell(f.getNumero(), fontNorm));
                table.addCell(dataCell(FormatUtil.date(f.getDateFiche()), fontNorm));
                table.addCell(dataCell(f.getLivreur() != null ? f.getLivreur().getNomComplet() : "—", fontNorm));
                table.addCell(dataCell(f.getStatut().name(), fontNorm));
                table.addCell(dataCellRight(String.valueOf(f.getNbLignes()), fontNorm));
                table.addCell(dataCellRight(FormatUtil.montant(f.getTotalSorties()), fontNorm));
                table.addCell(dataCellRight(FormatUtil.montant(f.getTotalRetours()), fontNorm));
                table.addCell(dataCellRight(FormatUtil.montant(f.getTotalNet()), fontBold));
                totNet = totNet.add(f.getTotalNet());
            }

            doc.add(table);
            doc.add(new Paragraph("\nTotal net du jour : " + FormatUtil.montant(totNet) + " FCFA")
                .setFont(fontBold).setFontSize(13));
            ajouterPiedPage(doc, fontNorm);
        }
    }

    // ── Reçu de versement ────────────────────────────────────────
    public static void exporterRecu(Versement versement, String cheminPdf) throws Exception {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfWriter(cheminPdf));
             Document doc = new Document(pdfDoc, new PageSize(PageSize.A5))) {

            doc.setMargins(30, 40, 30, 40);
            PdfFont fontBold = getFont(true);
            PdfFont fontNorm = getFont(false);

            doc.add(new Paragraph("🥖 BOULANGERIE").setFont(fontBold).setFontSize(18)
                .setFontColor(MARRON).setTextAlignment(TextAlignment.CENTER));
            doc.add(new Paragraph("Reçu de versement").setFont(fontBold).setFontSize(14)
                .setTextAlignment(TextAlignment.CENTER));
            doc.add(new LineSeparator(new com.itextpdf.kernel.pdf.canvas.draw.SolidLine()));
            doc.add(new Paragraph("\n").setFontSize(4));

            String[][] lignes = {
                {"N° Reçu :",      versement.getNumero()},
                {"Date :",         FormatUtil.date(versement.getDateVersement())},
                {"Facture :",      versement.getFacture() != null ? versement.getFacture().getNumero() : "—"},
                {"Client :",       versement.getClient() != null ? versement.getClient().getNom() : "—"},
                {"Livreur :",      versement.getLivreur() != null ? versement.getLivreur().getNomComplet() : "—"},
                {"Montant reçu :", FormatUtil.montant(versement.getMontantRemis()) + " FCFA"},
                {"Mode paiement :", versement.getModePaiement() != null ? versement.getModePaiement() : "Espèces"},
            };

            Table infoTable = new Table(UnitValue.createPercentArray(new float[]{45, 55})).useAllAvailableWidth();
            for (String[] row : lignes) {
                infoTable.addCell(new Cell().add(new Paragraph(row[0]).setFont(fontBold).setFontSize(11))
                    .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER));
                infoTable.addCell(new Cell().add(new Paragraph(row[1]).setFont(fontNorm).setFontSize(11))
                    .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER));
            }
            doc.add(infoTable);

            doc.add(new Paragraph("\n").setFontSize(8));
            doc.add(new Paragraph("Merci pour votre confiance !").setFont(fontBold)
                .setFontSize(12).setTextAlignment(TextAlignment.CENTER).setFontColor(BLEU));
            doc.add(new Paragraph("\n\nSignature : ________________________________")
                .setFont(fontNorm).setFontSize(11));
        }
    }

    // ── Helpers privés ───────────────────────────────────────────
    private static PdfFont getFont(boolean bold) {
        try {
            return bold
                ? PdfFontFactory.createFont(com.itextpdf.io.font.constants.StandardFonts.HELVETICA_BOLD)
                : PdfFontFactory.createFont(com.itextpdf.io.font.constants.StandardFonts.HELVETICA);
        } catch (IOException e) {
            throw new RuntimeException("Erreur chargement police PDF", e);
        }
    }

    private static void ajouterTitrePage(Document doc, String titre, PdfFont fontBold, PdfFont fontNorm) throws IOException {
        doc.add(new Paragraph("🥖 BOULANGERIE — " + titre)
            .setFont(fontBold).setFontSize(14).setFontColor(MARRON)
            .setTextAlignment(TextAlignment.CENTER));
        doc.add(new LineSeparator(new com.itextpdf.kernel.pdf.canvas.draw.SolidLine()));
        doc.add(new Paragraph("\n").setFontSize(4));
    }

    private static void ajouterPiedPage(Document doc, PdfFont fontNorm) {
        doc.add(new LineSeparator(new com.itextpdf.kernel.pdf.canvas.draw.SolidLine()));
        doc.add(new Paragraph("Généré le " + LocalDateTime.now()
            .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) + " — Page 1")
            .setFont(fontNorm).setFontSize(8).setFontColor(GRIS_TEXTE).setTextAlignment(TextAlignment.RIGHT));
    }

    private static Cell headerCell(String text, PdfFont font) {
        return new Cell().add(new Paragraph(text).setFont(font).setFontSize(9).setFontColor(ColorConstants.WHITE))
            .setBackgroundColor(BLEU).setTextAlignment(TextAlignment.CENTER);
    }

    private static Cell dataCell(String text, PdfFont font) {
        return new Cell().add(new Paragraph(text != null ? text : "—").setFont(font).setFontSize(9));
    }

    private static Cell dataCellRight(String text, PdfFont font) {
        return new Cell().add(new Paragraph(text != null ? text : "—").setFont(font).setFontSize(9)
            .setTextAlignment(TextAlignment.RIGHT));
    }

    private static Cell infoCell(String label, String value, PdfFont fontBold, PdfFont fontNorm) {
        return new Cell()
            .add(new Paragraph(label).setFont(fontBold).setFontSize(9).setFontColor(GRIS_TEXTE))
            .add(new Paragraph(value).setFont(fontNorm).setFontSize(10))
            .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER);
    }

    private static void addTotalRow(Table table, String label, String value, PdfFont font, boolean highlight) {
        DeviceRgb bg = highlight ? GRIS_FOND : null;
        Cell lCell = new Cell().add(new Paragraph(label).setFont(font).setFontSize(10));
        Cell vCell = new Cell().add(new Paragraph(value).setFont(font).setFontSize(10)
            .setTextAlignment(TextAlignment.RIGHT));
        if (bg != null) { lCell.setBackgroundColor(bg); vCell.setBackgroundColor(bg); }
        table.addCell(lCell);
        table.addCell(vCell);
    }
}
