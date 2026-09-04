package com.boulangerie.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class Client {
    public enum Statut { Actif, Bloqué, Inactif }
    public enum TypeClient { Nominatif, Anonyme }

    private String id;
    private String code;
    private String nom;
    private String quartier;
    private String ville;
    private String telephone;
    private String email;
    private CategorieClient categorie;
    private boolean estAnonyme;
    private TypeClient typeClient;
    private Utilisateur livreurRattache;
    private int delaiPaiement;
    private BigDecimal plafondCredit;
    private BigDecimal soldePrecedent;
    private BigDecimal soldeActuel;
    private Statut statut;
    private String notes;
    private LocalDateTime dateCreation;
    private LocalDate derniereFactureDate;

    public Client() {
        this.delaiPaiement = 30;
        this.plafondCredit  = BigDecimal.ZERO;
        this.soldePrecedent = BigDecimal.ZERO;
        this.soldeActuel    = BigDecimal.ZERO;
        this.statut         = Statut.Actif;
        this.typeClient     = TypeClient.Nominatif;
    }

    // ── Getters / Setters ──────────────────────────────────────
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }
    public String getQuartier() { return quartier; }
    public void setQuartier(String quartier) { this.quartier = quartier; }
    public String getVille() { return ville; }
    public void setVille(String ville) { this.ville = ville; }
    public String getTelephone() { return telephone; }
    public void setTelephone(String telephone) { this.telephone = telephone; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public CategorieClient getCategorie() { return categorie; }
    public void setCategorie(CategorieClient categorie) { this.categorie = categorie; }
    public boolean isEstAnonyme() { return estAnonyme; }
    public void setEstAnonyme(boolean estAnonyme) { this.estAnonyme = estAnonyme; }
    public TypeClient getTypeClient() { return typeClient; }
    public void setTypeClient(TypeClient typeClient) { this.typeClient = typeClient; }
    public Utilisateur getLivreurRattache() { return livreurRattache; }
    public void setLivreurRattache(Utilisateur livreurRattache) { this.livreurRattache = livreurRattache; }
    public int getDelaiPaiement() { return delaiPaiement; }
    public void setDelaiPaiement(int delaiPaiement) { this.delaiPaiement = delaiPaiement; }
    public BigDecimal getPlafondCredit() { return plafondCredit; }
    public void setPlafondCredit(BigDecimal plafondCredit) { this.plafondCredit = plafondCredit; }
    public BigDecimal getSoldePrecedent() { return soldePrecedent; }
    public void setSoldePrecedent(BigDecimal soldePrecedent) { this.soldePrecedent = soldePrecedent; }
    public BigDecimal getSoldeActuel() { return soldeActuel; }
    public void setSoldeActuel(BigDecimal soldeActuel) { this.soldeActuel = soldeActuel; }
    public Statut getStatut() { return statut; }
    public void setStatut(Statut statut) { this.statut = statut; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public LocalDateTime getDateCreation() { return dateCreation; }
    public void setDateCreation(LocalDateTime dateCreation) { this.dateCreation = dateCreation; }

    public Integer getDelaiPaiementJours() { return delaiPaiement; }
    public LocalDate getDerniereFactureDate() { return derniereFactureDate; }
    public void setDerniereFactureDate(LocalDate d) { this.derniereFactureDate = d; }

    public boolean isBloque() { return Statut.Bloqué.equals(statut); }
    public boolean isNominatif() { return TypeClient.Nominatif.equals(typeClient); }

    @Override public String toString() { return code + " - " + nom; }
}
