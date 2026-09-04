package com.boulangerie.service;

import com.boulangerie.dao.*;
import com.boulangerie.model.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Résolution du tarif applicable selon la règle de priorité CDC §8.2 :
 *
 *   (1) Tarif spécifique au client nominatif s'il existe et est valide à la date
 *   (2) Tarif de la catégorie du client (Externe / Interne / Carrefour)
 *   (3) Tarif Standard
 *
 * Une remise exceptionnelle active s'applique en complément et est toujours
 * affichée séparément pour rester traçable.
 */
public class TarifService {
    private final ProduitDAO              produitDAO  = new ProduitDAO();
    private final TarifClientDAO          tarifCliDAO = new TarifClientDAO();
    private final RemiseExceptionnelleDAO remiseDAO   = new RemiseExceptionnelleDAO();

    public record TarifResolu(
        BigDecimal prix,
        String     typeTarif,
        BigDecimal remisePct,
        BigDecimal prixApresRemise,
        String     remiseMotif
    ) {}

    /**
     * Résout le tarif applicable pour un produit, un client et une date.
     *
     * @param produitId  identifiant du produit
     * @param client     client (avec catégorie chargée)
     * @param quantite   quantité sortie (pour les remises avec quantite_min)
     * @param date       date de la sortie
     * @return TarifResolu contenant le prix net et les informations de traçabilité
     */
    public TarifResolu resoudre(String produitId, Client client, int quantite, LocalDate date) {

        // ── (1) Tarif spécifique client ───────────────────────────
        if (client.isNominatif() && client.getId() != null) {
            Optional<BigDecimal> specifique = tarifCliDAO.findPrixSpecifique(client.getId(), produitId, date);
            if (specifique.isPresent()) {
                BigDecimal prix = specifique.get();
                BigDecimal prixFinal = appliquerRemise(produitId, date, quantite, prix);
                BigDecimal remisePct = computeRemisePct(prix, prixFinal);
                String remiseMotif   = getRemiseMotif(produitId, date, quantite);
                return new TarifResolu(prix, "Specifique", remisePct, prixFinal, remiseMotif);
            }
        }

        // ── (2) Tarif de catégorie ────────────────────────────────
        List<Tarif> tarifs = produitDAO.findTarifs(produitId);
        String categNom = client.getCategorie() != null ? client.getCategorie().getNom() : null;

        if (categNom != null) {
            Tarif.TypeTarif typeCat;
            try { typeCat = Tarif.TypeTarif.valueOf(categNom); }
            catch (IllegalArgumentException e) { typeCat = null; }

            if (typeCat != null) {
                Tarif.TypeTarif finalType = typeCat;
                Optional<Tarif> tarifCat = tarifs.stream()
                    .filter(t -> t.getTypeTarif() == finalType && t.isValideAujourd())
                    .findFirst();
                if (tarifCat.isPresent()) {
                    BigDecimal prix = tarifCat.get().getMontant();
                    BigDecimal prixFinal = appliquerRemise(produitId, date, quantite, prix);
                    BigDecimal remisePct = computeRemisePct(prix, prixFinal);
                    String remiseMotif   = getRemiseMotif(produitId, date, quantite);
                    return new TarifResolu(prix, categNom, remisePct, prixFinal, remiseMotif);
                }
            }
        }

        // ── (3) Tarif Standard ────────────────────────────────────
        Optional<Tarif> standard = tarifs.stream()
            .filter(t -> t.getTypeTarif() == Tarif.TypeTarif.Standard && t.isValideAujourd())
            .findFirst();

        BigDecimal prix = standard.map(Tarif::getMontant).orElse(BigDecimal.ZERO);
        BigDecimal prixFinal = appliquerRemise(produitId, date, quantite, prix);
        BigDecimal remisePct = computeRemisePct(prix, prixFinal);
        String remiseMotif   = getRemiseMotif(produitId, date, quantite);
        return new TarifResolu(prix, "Standard", remisePct, prixFinal, remiseMotif);
    }

    // ── Helpers privés ───────────────────────────────────────────
    private BigDecimal appliquerRemise(String produitId, LocalDate date, int quantite, BigDecimal prixBase) {
        Optional<RemiseExceptionnelle> remise = remiseDAO.findApplicable(produitId, date, quantite);
        if (remise.isPresent() && remise.get().getPrixAccorde() != null) {
            return remise.get().getPrixAccorde();
        }
        return prixBase;
    }

    private String getRemiseMotif(String produitId, LocalDate date, int quantite) {
        return remiseDAO.findApplicable(produitId, date, quantite)
            .map(RemiseExceptionnelle::getMotif)
            .orElse(null);
    }

    private BigDecimal computeRemisePct(BigDecimal prixBase, BigDecimal prixFinal) {
        if (prixBase == null || prixBase.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
        if (prixFinal == null || prixFinal.compareTo(prixBase) == 0) return BigDecimal.ZERO;
        return prixBase.subtract(prixFinal)
            .divide(prixBase, 4, java.math.RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100))
            .setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
