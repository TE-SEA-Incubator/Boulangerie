package com.boulangerie.model;

import java.time.LocalDateTime;

public class Recu {
    private String id;
    private String numero;
    private Versement versement;
    private LocalDateTime dateRecu;
    private String generePar;

    public Recu() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getNumero() { return numero; }
    public void setNumero(String numero) { this.numero = numero; }
    public Versement getVersement() { return versement; }
    public void setVersement(Versement versement) { this.versement = versement; }
    public LocalDateTime getDateRecu() { return dateRecu; }
    public void setDateRecu(LocalDateTime dateRecu) { this.dateRecu = dateRecu; }
    public String getGenerePar() { return generePar; }
    public void setGenerePar(String generePar) { this.generePar = generePar; }
}
