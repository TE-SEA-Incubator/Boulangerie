package com.boulangerie.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public class Tarif {
    public enum TypeTarif { Standard, Externe, Interne, Carrefour, Specifique }
    public enum Statut { Actif, Inactif }

    private String id;
    private String produitId;
    private TypeTarif typeTarif;
    private BigDecimal montant;
    private LocalDate dateDebut;
    private LocalDate dateFin;
    private Statut statut;

    public Tarif() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getProduitId() { return produitId; }
    public void setProduitId(String produitId) { this.produitId = produitId; }
    public TypeTarif getTypeTarif() { return typeTarif; }
    public void setTypeTarif(TypeTarif typeTarif) { this.typeTarif = typeTarif; }
    public BigDecimal getMontant() { return montant; }
    public void setMontant(BigDecimal montant) { this.montant = montant; }
    public LocalDate getDateDebut() { return dateDebut; }
    public void setDateDebut(LocalDate dateDebut) { this.dateDebut = dateDebut; }
    public LocalDate getDateFin() { return dateFin; }
    public void setDateFin(LocalDate dateFin) { this.dateFin = dateFin; }
    public Statut getStatut() { return statut; }
    public void setStatut(Statut statut) { this.statut = statut; }

    public boolean isValideAujourd() {
        LocalDate today = LocalDate.now();
        boolean apresDebut = dateDebut == null || !today.isBefore(dateDebut);
        boolean avantFin   = dateFin == null  || !today.isAfter(dateFin);
        return Statut.Actif.equals(statut) && apresDebut && avantFin;
    }

    @Override public String toString() { return typeTarif + " : " + montant; }
}
