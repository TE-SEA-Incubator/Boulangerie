package com.boulangerie.service;

import com.boulangerie.dao.DatabaseConnection;
import com.boulangerie.dao.ProduitDAO;
import com.boulangerie.model.Produit;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.*;

/**
 * Moteur d'analyse prédictive et de prévisions pour la boulangerie :
 * - Projection du chiffre d'affaires à 7 jours et fin de mois
 * - Prévision des volumes de production requis par produit (pains, viennoiseries)
 * - Estimation des encaissements et du risque d'impayés
 */
public class PrevisionService {

    public record PrevisionProduit(
        String produitId,
        String designation,
        BigDecimal prixUnitaire,
        int moyenneJour,
        int previDemain,
        int previSemaine,
        BigDecimal caEstimeDemain,
        String recommandation
    ) {}

    public record SynthesePrevision(
        BigDecimal caMoyenJour,
        BigDecimal caPrevu7Jours,
        BigDecimal caPrevuFinMois,
        int totalPiecesPrevuesDemain,
        BigDecimal encaissementsEstimes7Jours,
        List<PrevisionProduit> previsionsProduits
    ) {}

    public SynthesePrevision genererPrevisions() {
        ProduitDAO produitDAO = new ProduitDAO();
        List<Produit> produits = produitDAO.findAll(false);
        DatabaseConnection db = DatabaseConnection.getInstance();

        Map<String, Integer> sorties7Jours = new HashMap<>();
        Map<String, Integer> retours7Jours = new HashMap<>();
        BigDecimal totalCA7Jours = BigDecimal.ZERO;
        int joursActifs = 7;

        String sql = """
            SELECT ls.produit_id,
                   SUM(ls.quantite_sortie) AS sum_sorties,
                   SUM(ls.quantite_retournee) AS sum_retours,
                   SUM(ls.montant_ht) AS sum_ca
            FROM ligne_sortie ls
            JOIN fiche_journaliere fj ON ls.fiche_id = fj.id
            WHERE fj.date_fiche >= DATE_SUB(CURDATE(), INTERVAL 14 DAY)
            GROUP BY ls.produit_id
            """;

        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String pid = rs.getString("produit_id");
                int s = rs.getInt("sum_sorties");
                int r = rs.getInt("sum_retours");
                BigDecimal ca = rs.getBigDecimal("sum_ca");
                sorties7Jours.put(pid, s);
                retours7Jours.put(pid, r);
                if (ca != null) totalCA7Jours = totalCA7Jours.add(ca);
            }
        } catch (SQLException e) {
            // Ignoré si table vide
        }

        // Calcul des prévisions par produit
        List<PrevisionProduit> list = new ArrayList<>();
        int totalPiecesDemain = 0;

        for (Produit p : produits) {
            int totalS = sorties7Jours.getOrDefault(p.getId(), 0);
            int totalR = retours7Jours.getOrDefault(p.getId(), 0);
            int net = Math.max(0, totalS - totalR);

            // Moyenne journalière sur 7 jours
            int moyJour = (int) Math.round((double) net / 7.0);
            if (moyJour == 0 && net > 0) moyJour = 1;

            // Si aucune donnée passée, valeur par défaut réaliste selon le type
            if (moyJour == 0) {
                if (p.getLibelle().toLowerCase().contains("40")) moyJour = 120;
                else if (p.getLibelle().toLowerCase().contains("75")) moyJour = 80;
                else if (p.getLibelle().toLowerCase().contains("100")) moyJour = 60;
                else if (p.getLibelle().toLowerCase().contains("baguette") || p.getLibelle().toLowerCase().contains("pain")) moyJour = 50;
                else moyJour = 20;
            }

            // Prévision Demain = Moyenne * 1.05 (marge de sécurité de 5%)
            int dem = (int) Math.ceil(moyJour * 1.05);
            int sem = dem * 7;
            totalPiecesDemain += dem;

            BigDecimal pu = p.getPrixUnitaire() != null ? p.getPrixUnitaire() : BigDecimal.ZERO;
            BigDecimal caDem = pu.multiply(BigDecimal.valueOf(dem));

            String reco;
            if (totalR > totalS * 0.15 && totalS > 0) {
                reco = "Taux de retour élevé (>15%) : Ajuster la fournée à la baisse";
            } else if (dem > 100) {
                reco = "Forte demande attendue : Lancer 2 fournées (matin / midi)";
            } else {
                reco = "Fournée standard recommandée";
            }

            list.add(new PrevisionProduit(
                p.getId(),
                p.getLibelle(),
                pu,
                moyJour,
                dem,
                sem,
                caDem,
                reco
            ));
        }

        // Tri par CA estimé décroissant
        list.sort((a, b) -> b.caEstimeDemain().compareTo(a.caEstimeDemain()));

        BigDecimal caMoyenJour = totalCA7Jours.compareTo(BigDecimal.ZERO) > 0
            ? totalCA7Jours.divide(BigDecimal.valueOf(7), 0, RoundingMode.HALF_UP)
            : list.stream().map(PrevisionProduit::caEstimeDemain).reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal caPrevu7Jours = caMoyenJour.multiply(BigDecimal.valueOf(7));

        int joursRestantsMois = Math.max(1, LocalDate.now().lengthOfMonth() - LocalDate.now().getDayOfMonth());
        BigDecimal caPrevuFinMois = caMoyenJour.multiply(BigDecimal.valueOf(joursRestantsMois));

        // Taux de recouvrement estimé à 92%
        BigDecimal encaissements7Jours = caPrevu7Jours.multiply(BigDecimal.valueOf(0.92)).setScale(0, RoundingMode.HALF_UP);

        return new SynthesePrevision(
            caMoyenJour,
            caPrevu7Jours,
            caPrevuFinMois,
            totalPiecesDemain,
            encaissements7Jours,
            list
        );
    }
}
