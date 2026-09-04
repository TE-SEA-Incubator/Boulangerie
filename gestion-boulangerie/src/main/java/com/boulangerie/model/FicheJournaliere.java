package com.boulangerie.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class FicheJournaliere {
    public enum Statut { Brouillon, EnCours, Complétée, Clôturée }

    private String id;
    private String numero;
    private LocalDate dateFiche;
    private Utilisateur livreur;
    private Statut statut;
    private BigDecimal totalSorties;
    private BigDecimal totalRetours;
    private BigDecimal totalNet;
    private String creePar;
    private LocalDateTime dateCreation;
    private LocalDateTime dateCloture;
    private List<LigneSortie> lignes = new ArrayList<>();

    public FicheJournaliere() {
        this.statut       = Statut.Brouillon;
        this.totalSorties = BigDecimal.ZERO;
        this.totalRetours = BigDecimal.ZERO;
        this.totalNet     = BigDecimal.ZERO;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getNumero() { return numero; }
    public void setNumero(String numero) { this.numero = numero; }
    public LocalDate getDateFiche() { return dateFiche; }
    public void setDateFiche(LocalDate dateFiche) { this.dateFiche = dateFiche; }
    public Utilisateur getLivreur() { return livreur; }
    public void setLivreur(Utilisateur livreur) { this.livreur = livreur; }
    public Statut getStatut() { return statut; }
    public void setStatut(Statut statut) { this.statut = statut; }
    public BigDecimal getTotalSorties() { return totalSorties; }
    public void setTotalSorties(BigDecimal totalSorties) { this.totalSorties = totalSorties; }
    public BigDecimal getTotalRetours() { return totalRetours; }
    public void setTotalRetours(BigDecimal totalRetours) { this.totalRetours = totalRetours; }
    public BigDecimal getTotalNet() { return totalNet; }
    public void setTotalNet(BigDecimal totalNet) { this.totalNet = totalNet; }
    public String getCreePar() { return creePar; }
    public void setCreePar(String creePar) { this.creePar = creePar; }
    public LocalDateTime getDateCreation() { return dateCreation; }
    public void setDateCreation(LocalDateTime dateCreation) { this.dateCreation = dateCreation; }
    public LocalDateTime getDateCloture() { return dateCloture; }
    public void setDateCloture(LocalDateTime dateCloture) { this.dateCloture = dateCloture; }
    public List<LigneSortie> getLignes() { return lignes; }
    public void setLignes(List<LigneSortie> lignes) { this.lignes = lignes; }

    public void recalculerTotaux() {
        totalSorties = BigDecimal.ZERO;
        totalRetours = BigDecimal.ZERO;
        for (LigneSortie l : lignes) {
            BigDecimal montantSortie  = l.getTarifApplicable().multiply(BigDecimal.valueOf(l.getQuantiteSortie()));
            BigDecimal montantRetour  = l.getTarifApplicable().multiply(BigDecimal.valueOf(l.getQuantiteRetournee()));
            totalSorties = totalSorties.add(montantSortie);
            totalRetours = totalRetours.add(montantRetour);
        }
        totalNet = totalSorties.subtract(totalRetours);
    }

    public int getNbLignes() { return lignes.size(); }

    @Override public String toString() { return numero + " - " + dateFiche; }
}
