package com.boulangerie.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class Produit {
    public enum Statut { Actif, Inactif }

    private String id;
    private String code;
    private String libelle;
    private Famille famille;
    private String unite;
    private Statut statut;
    private int seuilAlerte;
    private String description;
    private LocalDateTime dateCreation;
    private List<Tarif> tarifs = new ArrayList<>();

    public Produit() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getLibelle() { return libelle; }
    public void setLibelle(String libelle) { this.libelle = libelle; }
    public Famille getFamille() { return famille; }
    public void setFamille(Famille famille) { this.famille = famille; }
    public String getUnite() { return unite; }
    public void setUnite(String unite) { this.unite = unite; }
    public Statut getStatut() { return statut; }
    public void setStatut(Statut statut) { this.statut = statut; }
    public int getSeuilAlerte() { return seuilAlerte; }
    public void setSeuilAlerte(int seuilAlerte) { this.seuilAlerte = seuilAlerte; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public LocalDateTime getDateCreation() { return dateCreation; }
    public void setDateCreation(LocalDateTime dateCreation) { this.dateCreation = dateCreation; }
    public List<Tarif> getTarifs() { return tarifs; }
    public void setTarifs(List<Tarif> tarifs) { this.tarifs = tarifs; }

    public boolean isActif() { return Statut.Actif.equals(statut); }

    @Override public String toString() { return code + " - " + libelle; }
}
