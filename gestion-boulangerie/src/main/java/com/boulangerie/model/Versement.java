package com.boulangerie.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class Versement {
    public enum Statut { Payé, Partiel, EnAttente }

    private String id;
    private String numero;
    private FicheJournaliere fiche;
    private Utilisateur livreur;
    private Client client;
    private BigDecimal montantAttendu;
    private BigDecimal montantRemis;
    private BigDecimal montantEnregistre; // Sera mappé sur montant_remis en DB si nécessaire ou utilisé pour les écarts
    private String modePaiement;
    private String motifEcart;
    private LocalDate dateVersement;
    private Statut statut;
    private Utilisateur caissier;
    private LocalDateTime dateCreation;

    public Versement() {
        this.montantAttendu     = BigDecimal.ZERO;
        this.montantRemis       = BigDecimal.ZERO;
        this.montantEnregistre  = BigDecimal.ZERO;
        this.statut             = Statut.EnAttente;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getNumero() { return numero; }
    public void setNumero(String numero) { this.numero = numero; }
    public FicheJournaliere getFiche() { return fiche; }
    public void setFiche(FicheJournaliere fiche) { this.fiche = fiche; }
    public Utilisateur getLivreur() { return livreur; }
    public void setLivreur(Utilisateur livreur) { this.livreur = livreur; }
    public Client getClient() { return client; }
    public void setClient(Client client) { this.client = client; }
    public BigDecimal getMontantAttendu() { return montantAttendu; }
    public void setMontantAttendu(BigDecimal montantAttendu) { this.montantAttendu = montantAttendu; }
    public BigDecimal getMontantRemis() { return montantRemis; }
    public void setMontantRemis(BigDecimal montantRemis) { this.montantRemis = montantRemis; }
    public BigDecimal getMontantEnregistre() { return montantEnregistre; }
    public void setMontantEnregistre(BigDecimal montantEnregistre) { this.montantEnregistre = montantEnregistre; }
    public String getModePaiement() { return modePaiement; }
    public void setModePaiement(String modePaiement) { this.modePaiement = modePaiement; }
    public String getMotifEcart() { return motifEcart; }
    public void setMotifEcart(String motifEcart) { this.motifEcart = motifEcart; }
    public LocalDate getDateVersement() { return dateVersement; }
    public void setDateVersement(LocalDate dateVersement) { this.dateVersement = dateVersement; }
    public Statut getStatut() { return statut; }
    public void setStatut(Statut statut) { this.statut = statut; }
    public Utilisateur getCaissier() { return caissier; }
    public void setCaissier(Utilisateur caissier) { this.caissier = caissier; }
    public LocalDateTime getDateCreation() { return dateCreation; }
    public void setDateCreation(LocalDateTime dateCreation) { this.dateCreation = dateCreation; }

    public BigDecimal getEcart() {
        return montantRemis.subtract(montantEnregistre);
    }

    // Compatibilité temporaire si Facture est encore référencé ailleurs
    public Facture getFacture() { return null; }
    public void setFacture(Facture facture) { /* no-op */ }

    @Override public String toString() { return numero; }
}
