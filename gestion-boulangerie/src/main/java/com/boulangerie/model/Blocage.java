package com.boulangerie.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class Blocage {
    public enum Statut { Actif, Levé }

    private String id;
    private String clientId;
    private LocalDate dateBlocage;
    private String motif;
    private BigDecimal montantDette;
    private Statut statut;
    private String levePar;
    private LocalDateTime dateLevee;

    public Blocage() { this.statut = Statut.Actif; }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public LocalDate getDateBlocage() { return dateBlocage; }
    public void setDateBlocage(LocalDate dateBlocage) { this.dateBlocage = dateBlocage; }
    public String getMotif() { return motif; }
    public void setMotif(String motif) { this.motif = motif; }
    public BigDecimal getMontantDette() { return montantDette; }
    public void setMontantDette(BigDecimal montantDette) { this.montantDette = montantDette; }
    public Statut getStatut() { return statut; }
    public void setStatut(Statut statut) { this.statut = statut; }
    public String getLevePar() { return levePar; }
    public void setLevePar(String levePar) { this.levePar = levePar; }
    public LocalDateTime getDateLevee() { return dateLevee; }
    public void setDateLevee(LocalDateTime dateLevee) { this.dateLevee = dateLevee; }
}
