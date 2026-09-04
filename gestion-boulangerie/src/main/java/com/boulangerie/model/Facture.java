package com.boulangerie.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class Facture {
    public enum Statut { EnAttente, Partielle, Payée, Annulée }

    private String id;
    private String numero;
    private LocalDate dateEmission;
    private Client client;
    private Utilisateur livreur;
    private BigDecimal montantHt;
    private BigDecimal tvaPct;
    private BigDecimal tvaMontant;
    private BigDecimal montantTtc;
    private Statut statut;
    private boolean estVerrouillee;
    private boolean estAnnulee;
    private String ficheId;
    private String modeReglement;
    private String notes;
    private String creePar;
    private LocalDateTime dateCreation;

    public Facture() {
        this.montantHt     = BigDecimal.ZERO;
        this.tvaPct        = BigDecimal.ZERO;
        this.tvaMontant    = BigDecimal.ZERO;
        this.montantTtc    = BigDecimal.ZERO;
        this.statut        = Statut.EnAttente;
        this.estVerrouillee = true;
        this.estAnnulee    = false;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getNumero() { return numero; }
    public void setNumero(String numero) { this.numero = numero; }
    public LocalDate getDateEmission() { return dateEmission; }
    public void setDateEmission(LocalDate dateEmission) { this.dateEmission = dateEmission; }
    public Client getClient() { return client; }
    public void setClient(Client client) { this.client = client; }
    public Utilisateur getLivreur() { return livreur; }
    public void setLivreur(Utilisateur livreur) { this.livreur = livreur; }
    public BigDecimal getMontantHt() { return montantHt; }
    public void setMontantHt(BigDecimal montantHt) { this.montantHt = montantHt; }
    public BigDecimal getTvaPct() { return tvaPct; }
    public void setTvaPct(BigDecimal tvaPct) { this.tvaPct = tvaPct; }
    public BigDecimal getTvaMontant() { return tvaMontant; }
    public void setTvaMontant(BigDecimal tvaMontant) { this.tvaMontant = tvaMontant; }
    public BigDecimal getMontantTtc() { return montantTtc; }
    public void setMontantTtc(BigDecimal montantTtc) { this.montantTtc = montantTtc; }
    public Statut getStatut() { return statut; }
    public void setStatut(Statut statut) { this.statut = statut; }
    public boolean isEstVerrouillee() { return estVerrouillee; }
    public void setEstVerrouillee(boolean estVerrouillee) { this.estVerrouillee = estVerrouillee; }
    public boolean isEstAnnulee() { return estAnnulee; }
    public void setEstAnnulee(boolean estAnnulee) { this.estAnnulee = estAnnulee; }
    public String getFicheId() { return ficheId; }
    public void setFicheId(String ficheId) { this.ficheId = ficheId; }
    public String getModeReglement() { return modeReglement; }
    public void setModeReglement(String modeReglement) { this.modeReglement = modeReglement; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getCreePar() { return creePar; }
    public void setCreePar(String creePar) { this.creePar = creePar; }
    public LocalDateTime getDateCreation() { return dateCreation; }
    public void setDateCreation(LocalDateTime dateCreation) { this.dateCreation = dateCreation; }

    public void calculerTva() {
        if (tvaPct.compareTo(BigDecimal.ZERO) > 0) {
            tvaMontant = montantHt.multiply(tvaPct).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        } else {
            tvaMontant = BigDecimal.ZERO;
        }
        montantTtc = montantHt.add(tvaMontant);
    }

    @Override public String toString() { return numero; }
}
