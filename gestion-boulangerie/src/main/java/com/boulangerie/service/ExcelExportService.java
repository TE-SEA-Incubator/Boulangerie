package com.boulangerie.service;

import com.boulangerie.model.FicheCaisseLigne;
import com.boulangerie.model.LigneSortie;
import com.boulangerie.util.FormatUtil;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

/**
 * Service d'exportation de fichiers tableur compatibles Microsoft Excel (.csv UTF-8 avec BOM).
 * Aligné avec les fiches de sorties et de facturation du dossier docx.
 */
public class ExcelExportService {

    public static void exporterFicheSortieExcel(LocalDate date, List<LigneSortie> lignes, File fichier) throws Exception {
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fichier), StandardCharsets.UTF_8))) {
            // BOM UTF-8 pour ouverture directe sous Excel avec accents corrects
            writer.write("\uFEFF");

            // En-tête du document
            writer.write("FICHE JOURNALIERE DES SORTIES ET RETOURS;DATE:;" + FormatUtil.date(date) + "\n\n");
            writer.write("N°;CLIENT / LIVREUR;PRODUIT;QTE SORTIE;QTE RETOURNEE;QTE NETTE;PRIX UNITAIRE (FCFA);TOTAL (FCFA)\n");

            int idx = 1;
            int totalSorties = 0;
            int totalRetours = 0;
            int totalNet = 0;
            java.math.BigDecimal totalMontant = java.math.BigDecimal.ZERO;

            for (LigneSortie l : lignes) {
                String clNom = l.getClient() != null ? l.getClient().getNom() : "";
                String prodLib = l.getProduit() != null ? l.getProduit().getLibelle() : "";
                writer.write(idx++ + ";"
                    + escape(clNom) + ";"
                    + escape(prodLib) + ";"
                    + l.getQuantiteSortie() + ";"
                    + l.getQuantiteRetournee() + ";"
                    + l.getQuantiteNette() + ";"
                    + (l.getTarifApplicable() != null ? l.getTarifApplicable().toString() : "0") + ";"
                    + (l.getMontantHt() != null ? l.getMontantHt().toString() : "0") + "\n");

                totalSorties += l.getQuantiteSortie();
                totalRetours += l.getQuantiteRetournee();
                totalNet += l.getQuantiteNette();
                if (l.getMontantHt() != null) totalMontant = totalMontant.add(l.getMontantHt());
            }

            writer.write("TOTAUX;;;" + totalSorties + ";" + totalRetours + ";" + totalNet + ";;" + totalMontant + "\n");
        }
    }

    public static void exporterFicheCaisseExcel(LocalDate date, List<FicheCaisseLigne> lignes, File fichier) throws Exception {
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fichier), StandardCharsets.UTF_8))) {
            // BOM UTF-8
            writer.write("\uFEFF");

            // Titre aligné avec FICHE DE FACTURATION (SORTIE 010826.xlsx)
            writer.write("FICHE DE FACTURATION ET CAISSE;DATE:;" + FormatUtil.date(date) + "\n\n");
            writer.write("N°;NOM LIVREUR / CLIENT;PRODUITS SORTIS;FACTURE DU JOUR (FCFA);ECART PRECEDENT (FCFA);TOTAL SOLDE (FCFA);VERSEMENT RECU (FCFA);RESTE DU (FCFA);STATUT\n");

            int idx = 1;
            java.math.BigDecimal totFac = java.math.BigDecimal.ZERO;
            java.math.BigDecimal totEcartPrec = java.math.BigDecimal.ZERO;
            java.math.BigDecimal totSolde = java.math.BigDecimal.ZERO;
            java.math.BigDecimal totVers = java.math.BigDecimal.ZERO;
            java.math.BigDecimal totReste = java.math.BigDecimal.ZERO;

            for (FicheCaisseLigne fl : lignes) {
                String nom = fl.getClient() != null ? fl.getClient().getNom() : "";
                writer.write(idx++ + ";"
                    + escape(nom) + ";"
                    + escape(fl.getResumeSorties()) + ";"
                    + fl.getMontantFacture() + ";"
                    + fl.getSoldePrecedent() + ";"
                    + fl.getTotalSolde() + ";"
                    + fl.getMontantVerse() + ";"
                    + fl.getReste() + ";"
                    + fl.getStatut() + "\n");

                totFac = totFac.add(fl.getMontantFacture());
                totEcartPrec = totEcartPrec.add(fl.getSoldePrecedent());
                totSolde = totSolde.add(fl.getTotalSolde());
                totVers = totVers.add(fl.getMontantVerse());
                totReste = totReste.add(fl.getReste());
            }

            writer.write("TOTAUX;;;" + totFac + ";" + totEcartPrec + ";" + totSolde + ";" + totVers + ";" + totReste + ";\n");
        }
    }

    private static String escape(String val) {
        if (val == null) return "";
        return "\"" + val.replace("\"", "\"\"") + "\"";
    }
}
