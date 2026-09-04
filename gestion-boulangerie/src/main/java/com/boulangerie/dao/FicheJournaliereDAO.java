package com.boulangerie.dao;

import com.boulangerie.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Optional;

public class FicheJournaliereDAO {
    private static final Logger log = LoggerFactory.getLogger(FicheJournaliereDAO.class);
    private final DatabaseConnection db = DatabaseConnection.getInstance();

    // ── Générer numéro séquentiel ────────────────────────────────
    public String genererNumero(LocalDate date) {
        String prefix = "FJ-" + date.getYear() + "-";
        String sql = "SELECT COUNT(*) FROM fiche_journaliere WHERE numero LIKE ?";
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, prefix + "%");
            ResultSet rs = ps.executeQuery();
            int count = rs.next() ? rs.getInt(1) : 0;
            return prefix + String.format("%04d", count + 1);
        } catch (SQLException e) {
            log.error("genererNumero", e);
            return prefix + System.currentTimeMillis();
        }
    }

    // ── Lister par date ──────────────────────────────────────────
    public List<FicheJournaliere> findByDate(LocalDate date) {
        return findByFilters(date, date, null, null);
    }

    public List<FicheJournaliere> findByFilters(LocalDate du, LocalDate au, String livreurId, String statut) {
        List<FicheJournaliere> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
            SELECT fj.*, u.id AS liv_id, u.nom_complet AS liv_nom
            FROM fiche_journaliere fj
            JOIN utilisateur u ON fj.livreur_id = u.id
            WHERE 1=1
            """);
        List<Object> params = new ArrayList<>();
        if (du != null) { sql.append(" AND fj.date_fiche >= ?"); params.add(java.sql.Date.valueOf(du)); }
        if (au != null) { sql.append(" AND fj.date_fiche <= ?"); params.add(java.sql.Date.valueOf(au)); }
        if (livreurId != null) { sql.append(" AND fj.livreur_id=?"); params.add(livreurId); }
        if (statut != null)    { sql.append(" AND fj.statut=?"); params.add(statut); }
        sql.append(" ORDER BY fj.date_fiche DESC, fj.numero");
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) ps.setObject(i + 1, params.get(i));
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(mapRow(rs));
        } catch (SQLException e) {
            log.error("findByFilters fiches", e);
        }
        return list;
    }

    public Optional<FicheJournaliere> findById(String id) {
        String sql = """
            SELECT fj.*, u.id AS liv_id, u.nom_complet AS liv_nom
            FROM fiche_journaliere fj
            JOIN utilisateur u ON fj.livreur_id = u.id
            WHERE fj.id=?
            """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                FicheJournaliere fj = mapRow(rs);
                fj.setLignes(findLignes(id, c));
                return Optional.of(fj);
            }
        } catch (SQLException e) {
            log.error("findById fiche {}", id, e);
        }
        return Optional.empty();
    }

    // ── Créer fiche ──────────────────────────────────────────────
    public String save(FicheJournaliere fj) {
        String sql = """
            INSERT INTO fiche_journaliere (id,numero,date_fiche,livreur_id,statut,cree_par)
            VALUES (UUID(),?,?,?,?,?)
            """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, fj.getNumero());
            ps.setDate(2, java.sql.Date.valueOf(fj.getDateFiche()));
            ps.setString(3, fj.getLivreur().getId());
            ps.setString(4, fj.getStatut().name());
            ps.setString(5, fj.getCreePar());
            ps.executeUpdate();
            return findIdByNumero(fj.getNumero(), c);
        } catch (SQLException e) {
            log.error("save fiche", e);
            throw new RuntimeException(e);
        }
    }

    public void updateStatut(String ficheId, FicheJournaliere.Statut statut) {
        String sql = "UPDATE fiche_journaliere SET statut=? WHERE id=?";
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, statut.name());
            ps.setString(2, ficheId);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("updateStatut fiche", e);
            throw new RuntimeException(e);
        }
    }

    public void updateTotaux(String ficheId, BigDecimal sorties, BigDecimal retours, BigDecimal net) {
        String sql = "UPDATE fiche_journaliere SET total_sorties=?,total_retours=?,total_net=? WHERE id=?";
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setBigDecimal(1, sorties);
            ps.setBigDecimal(2, retours);
            ps.setBigDecimal(3, net);
            ps.setString(4, ficheId);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("updateTotaux fiche", e);
            throw new RuntimeException(e);
        }
    }

    // ── Lignes de sortie ─────────────────────────────────────────
    public String saveLigne(LigneSortie l) {
        String sql = """
            INSERT INTO ligne_sortie (id,fiche_id,client_id,produit_id,quantite_sortie,
            quantite_retournee,tarif_applicable,type_tarif,remise_pct,montant_ht,motif_retour)
            VALUES (UUID(),?,?,?,?,?,?,?,?,?,?)
            """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, l.getFicheId());
            ps.setString(2, l.getClient().getId());
            ps.setString(3, l.getProduit().getId());
            ps.setInt(4, l.getQuantiteSortie());
            ps.setInt(5, l.getQuantiteRetournee());
            ps.setBigDecimal(6, l.getTarifApplicable());
            ps.setString(7, l.getTypeTarif());
            ps.setBigDecimal(8, l.getRemisePct());
            ps.setBigDecimal(9, l.getMontantHt());
            ps.setString(10, l.getMotifRetour());
            ps.executeUpdate();
            return null;
        } catch (SQLException e) {
            log.error("saveLigne", e);
            throw new RuntimeException(e);
        }
    }

    public void updateLigne(LigneSortie l) {
        String sql = """
            UPDATE ligne_sortie SET quantite_sortie=?,quantite_retournee=?,
            tarif_applicable=?,remise_pct=?,montant_ht=?,motif_retour=? WHERE id=?
            """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, l.getQuantiteSortie());
            ps.setInt(2, l.getQuantiteRetournee());
            ps.setBigDecimal(3, l.getTarifApplicable());
            ps.setBigDecimal(4, l.getRemisePct());
            ps.setBigDecimal(5, l.getMontantHt());
            ps.setString(6, l.getMotifRetour());
            ps.setString(7, l.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("updateLigne", e);
            throw new RuntimeException(e);
        }
    }

    public void deleteLigne(String ligneId) {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM ligne_sortie WHERE id=?")) {
            ps.setString(1, ligneId);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("deleteLigne {}", ligneId, e);
            throw new RuntimeException(e);
        }
    }

    // ── Statistiques dashboard ───────────────────────────────────
    public BigDecimal getSortiesNettesJour(LocalDate date) {
        String sql = "SELECT COALESCE(SUM(total_net),0) FROM fiche_journaliere WHERE date_fiche=?";
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setDate(1, java.sql.Date.valueOf(date));
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getBigDecimal(1) : BigDecimal.ZERO;
        } catch (SQLException e) {
            log.error("getSortiesNettesJour", e);
            return BigDecimal.ZERO;
        }
    }

    // ── Helpers ──────────────────────────────────────────────────
    private List<LigneSortie> findLignes(String ficheId, Connection c) throws SQLException {
        List<LigneSortie> list = new ArrayList<>();
        String sql = """
            SELECT ls.*,
                   cl.id AS cl_id, cl.code AS cl_code, cl.nom AS cl_nom,
                   p.id AS p_id, p.code AS p_code, p.libelle AS p_lib
            FROM ligne_sortie ls
            JOIN client cl ON ls.client_id = cl.id
            JOIN produit p ON ls.produit_id = p.id
            WHERE ls.fiche_id=?
            """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, ficheId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                LigneSortie l = new LigneSortie();
                l.setId(rs.getString("id"));
                l.setFicheId(ficheId);
                l.setQuantiteSortie(rs.getInt("quantite_sortie"));
                l.setQuantiteRetournee(rs.getInt("quantite_retournee"));
                l.setTarifApplicable(rs.getBigDecimal("tarif_applicable"));
                l.setTypeTarif(rs.getString("type_tarif"));
                l.setRemisePct(rs.getBigDecimal("remise_pct"));
                l.setMontantHt(rs.getBigDecimal("montant_ht"));
                l.setMotifRetour(rs.getString("motif_retour"));
                Client cl = new Client();
                cl.setId(rs.getString("cl_id")); cl.setCode(rs.getString("cl_code")); cl.setNom(rs.getString("cl_nom"));
                l.setClient(cl);
                Produit p = new Produit();
                p.setId(rs.getString("p_id")); p.setCode(rs.getString("p_code")); p.setLibelle(rs.getString("p_lib"));
                l.setProduit(p);
                list.add(l);
            }
        }
        return list;
    }

    private String findIdByNumero(String numero, Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT id FROM fiche_journaliere WHERE numero=?")) {
            ps.setString(1, numero);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getString("id") : null;
        }
    }

    private FicheJournaliere mapRow(ResultSet rs) throws SQLException {
        FicheJournaliere fj = new FicheJournaliere();
        fj.setId(rs.getString("id"));
        fj.setNumero(rs.getString("numero"));
        Date df = rs.getDate("date_fiche");
        if (df != null) fj.setDateFiche(df.toLocalDate());
        fj.setStatut(FicheJournaliere.Statut.valueOf(rs.getString("statut").replace(" ","").replace("é","é")));
        fj.setTotalSorties(rs.getBigDecimal("total_sorties"));
        fj.setTotalRetours(rs.getBigDecimal("total_retours"));
        fj.setTotalNet(rs.getBigDecimal("total_net"));
        fj.setCreePar(rs.getString("cree_par"));
        Timestamp dc = rs.getTimestamp("date_creation");
        if (dc != null) fj.setDateCreation(dc.toLocalDateTime());
        Utilisateur liv = new Utilisateur();
        liv.setId(rs.getString("liv_id"));
        liv.setNomComplet(rs.getString("liv_nom"));
        fj.setLivreur(liv);
        return fj;
    }
}
