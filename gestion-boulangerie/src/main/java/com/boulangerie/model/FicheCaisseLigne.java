package com.boulangerie.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Représente une ligne du journal de caisse / rapprochement journalier,
 * directement alignée avec la "FICHE DE FACTURATION" des fichiers Excel de la boulangerie.
 */
public class FicheCaisseLigne {
    private Client client;
    private final List<LigneCommande> sorties = new ArrayList<>();
    private String resumeSorties = "";
    private int totalQuantite = 0;
    private BigDecimal montantFacture = BigDecimal.ZERO;  // Sorties du jour (Montant Attendu)
    private BigDecimal soldePrecedent = BigDecimal.ZERO;  // Écart / Solde antérieur
    private BigDecimal manquant = BigDecimal.ZERO;        // Manquant éventuel
    private BigDecimal totalSolde = BigDecimal.ZERO;      // Facture + Écart précédent + Manquant
    private BigDecimal montantVerse = BigDecimal.ZERO;    // Versement remis à la caisse
    private BigDecimal reste = BigDecimal.ZERO;           // Nouveau solde / Reste dû
    private String statut = "Non réglé";
    private Versement versement;

    public FicheCaisseLigne() {}

    public FicheCaisseLigne(Client client) {
        this.client = client;
        if (client != null && client.getSoldeActuel() != null) {
            this.soldePrecedent = client.getSoldeActuel();
        }
        recalculer();
    }

    public void ajouterSortie(LigneCommande l) {
        sorties.add(l);
        totalQuantite += l.getQuantiteNette();
        montantFacture = montantFacture.add(l.getMontantHt() != null ? l.getMontantHt() : BigDecimal.ZERO);
        recalculer();
    }

    public void recalculer() {
        // Construction du résumé des produits sortis
        StringBuilder sb = new StringBuilder();
        for (LigneCommande l : sorties) {
            if (sb.length() > 0) sb.append(", ");
            String nomProd = l.getProduit() != null ? l.getProduit().getLibelle() : "Produit";
            sb.append(nomProd).append(" (").append(l.getQuantiteNette()).append(")");
        }
        this.resumeSorties = sb.toString();

        BigDecimal manq = manquant != null ? manquant : BigDecimal.ZERO;
        BigDecimal sp = soldePrecedent != null ? soldePrecedent : BigDecimal.ZERO;
        BigDecimal fac = montantFacture != null ? montantFacture : BigDecimal.ZERO;
        this.totalSolde = fac.add(sp).add(manq);

        BigDecimal vers = montantVerse != null ? montantVerse : BigDecimal.ZERO;
        this.reste = totalSolde.subtract(vers);

        if (totalSolde.compareTo(BigDecimal.ZERO) == 0 && vers.compareTo(BigDecimal.ZERO) == 0) {
            this.statut = "Néant";
        } else if (reste.compareTo(BigDecimal.ZERO) == 0) {
            this.statut = "Soldé";
        } else if (reste.compareTo(BigDecimal.ZERO) < 0) {
            this.statut = "Excédent";
        } else if (vers.compareTo(BigDecimal.ZERO) > 0) {
            this.statut = "Partiel";
        } else {
            this.statut = "Non versé";
        }
    }

    // Getters & Setters
    public Client getClient() { return client; }
    public void setClient(Client client) { this.client = client; recalculer(); }

    public List<LigneCommande> getSorties() { return sorties; }

    public String getResumeSorties() { return resumeSorties; }
    public void setResumeSorties(String resumeSorties) { this.resumeSorties = resumeSorties; }

    public int getTotalQuantite() { return totalQuantite; }

    public BigDecimal getMontantFacture() { return montantFacture; }
    public void setMontantFacture(BigDecimal montantFacture) { this.montantFacture = montantFacture; recalculer(); }

    public BigDecimal getSoldePrecedent() { return soldePrecedent; }
    public void setSoldePrecedent(BigDecimal soldePrecedent) { this.soldePrecedent = soldePrecedent; recalculer(); }

    public BigDecimal getManquant() { return manquant; }
    public void setManquant(BigDecimal manquant) { this.manquant = manquant; recalculer(); }

    public BigDecimal getTotalSolde() { return totalSolde; }

    public BigDecimal getMontantVerse() { return montantVerse; }
    public void setMontantVerse(BigDecimal montantVerse) { this.montantVerse = montantVerse; recalculer(); }

    public BigDecimal getReste() { return reste; }

    public String getStatut() { return statut; }

    public Versement getVersement() { return versement; }
    public void setVersement(Versement versement) {
        this.versement = versement;
        if (versement != null) {
            this.montantVerse = versement.getMontantRemis();
            recalculer();
        }
    }
}
