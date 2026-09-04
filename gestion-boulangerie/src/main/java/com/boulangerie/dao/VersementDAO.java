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

public class VersementDAO {
    private static final Logger log = LoggerFactory.getLogger(VersementDAO.class);
    private final DatabaseConnection db = DatabaseConnection.getInstance();

    public String genererNumero() {
        String prefix = "VRS-";
        String sql = "SELECT COUNT(*) FROM versement WHERE numero LIKE ?";
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, prefix + "%");
            ResultSet rs = ps.executeQuery();
            int count = rs.next() ? rs.getInt(1) : 0;
            return prefix + String.format("%06d", count + 1);
        } catch (SQLException e) {
            return prefix + System.currentTimeMillis();
        }
    }

    public String genererNumeroRecu() {
        String prefix = "R-";
        String sql = "SELECT COUNT(*) FROM recu WHERE numero LIKE ?";
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, prefix + "%");
            ResultSet rs = ps.executeQuery();
            int count = rs.next() ? rs.getInt(1) : 0;
            return prefix + LocalDate.now().getYear() + "-" + String.format("%04d", count + 1);
        } catch (SQLException e) {
            return prefix + System.currentTimeMillis();
        }
    }

    public List<Versement> findByDate(LocalDate date) {
        List<Versement> list = new ArrayList<>();
        String sql = """
            SELECT v.*,
                   f.numero AS fac_num,
                   cl.id AS cl_id, cl.nom AS cl_nom,
                   u.id AS liv_id, u.nom_complet AS liv_nom,
                   ca.id AS ca_id, ca.nom_complet AS ca_nom
            FROM versement v
            LEFT JOIN facture f ON v.facture_id = f.id
            LEFT JOIN client cl ON v.client_id = cl.id
            LEFT JOIN utilisateur u ON v.livreur_id = u.id
            LEFT JOIN utilisateur ca ON v.caissier_id = ca.id
            WHERE v.date_versement=?
            ORDER BY v.date_creation
            """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setDate(1, java.sql.Date.valueOf(date));
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(mapRow(rs));
        } catch (SQLException e) {
            log.error("findByDate versements", e);
        }
        return list;
    }

    public List<Versement> findByFacture(String factureId) {
        List<Versement> list = new ArrayList<>();
        String sql = """
            SELECT v.*,
                   f.numero AS fac_num,
                   cl.id AS cl_id, cl.nom AS cl_nom,
                   u.id AS liv_id, u.nom_complet AS liv_nom,
                   ca.id AS ca_id, ca.nom_complet AS ca_nom
            FROM versement v
            LEFT JOIN facture f ON v.facture_id = f.id
            LEFT JOIN client cl ON v.client_id = cl.id
            LEFT JOIN utilisateur u ON v.livreur_id = u.id
            LEFT JOIN utilisateur ca ON v.caissier_id = ca.id
            WHERE v.facture_id=?
            """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, factureId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(mapRow(rs));
        } catch (SQLException e) {
            log.error("findByFacture {}", factureId, e);
        }
        return list;
    }

    public String save(Versement v) {
        String sql = """
            INSERT INTO versement (id,numero,facture_id,livreur_id,client_id,
            montant_attendu,montant_remis,montant_enregistre,mode_paiement,
            motif_ecart,date_versement,statut,caissier_id)
            VALUES (UUID(),?,?,?,?,?,?,?,?,?,?,?,?)
            """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, v.getNumero());
            ps.setString(2, v.getFacture() != null ? v.getFacture().getId() : null);
            ps.setString(3, v.getLivreur() != null ? v.getLivreur().getId() : null);
            ps.setString(4, v.getClient() != null ? v.getClient().getId() : null);
            ps.setBigDecimal(5, v.getMontantAttendu());
            ps.setBigDecimal(6, v.getMontantRemis());
            ps.setBigDecimal(7, v.getMontantEnregistre());
            ps.setString(8, v.getModePaiement());
            ps.setString(9, v.getMotifEcart());
            ps.setDate(10, java.sql.Date.valueOf(v.getDateVersement()));
            ps.setString(11, v.getStatut().name());
            ps.setString(12, v.getCaissier() != null ? v.getCaissier().getId() : null);
            ps.executeUpdate();
            return findIdByNumero(v.getNumero(), c);
        } catch (SQLException e) {
            log.error("save versement", e);
            throw new RuntimeException(e);
        }
    }

    public void saveRecu(Recu r) {
        String sql = "INSERT INTO recu (id,numero,versement_id,genere_par) VALUES (UUID(),?,?,?)";
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, r.getNumero());
            ps.setString(2, r.getVersement().getId());
            ps.setString(3, r.getGenerePar());
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("saveRecu", e);
            throw new RuntimeException(e);
        }
    }

    // ── Rapprochement caisse ─────────────────────────────────────
    public BigDecimal getMontantAttenduJour(LocalDate date) {
        return getSomme("SELECT COALESCE(SUM(montant_attendu),0) FROM versement WHERE date_versement=?", date);
    }

    public BigDecimal getMontantRemisJour(LocalDate date) {
        return getSomme("SELECT COALESCE(SUM(montant_remis),0) FROM versement WHERE date_versement=?", date);
    }

    public BigDecimal getMontantEnregistreJour(LocalDate date) {
        return getSomme("SELECT COALESCE(SUM(montant_enregistre),0) FROM versement WHERE date_versement=?", date);
    }

    public BigDecimal getEcartsCaisseJour(LocalDate date) {
        return getSomme("SELECT COALESCE(SUM(montant_remis - montant_enregistre),0) FROM versement WHERE date_versement=?", date);
    }

    private BigDecimal getSomme(String sql, LocalDate date) {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setDate(1, java.sql.Date.valueOf(date));
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getBigDecimal(1) : BigDecimal.ZERO;
        } catch (SQLException e) {
            log.error("getSomme {}", sql, e);
            return BigDecimal.ZERO;
        }
    }

    // ── Clôture journalière ──────────────────────────────────────
    public void saveClotureJournaliere(ClotureJournaliere cl) {
        String sql = """
            INSERT INTO cloture_journaliere (id,date_cloture,montant_attendu,montant_remis,
            montant_enregistre,ecart_total,motif_ecart,taux_recouvrement,solde_cloture,valide_par,date_validation)
            VALUES (UUID(),?,?,?,?,?,?,?,?,?,NOW())
            ON DUPLICATE KEY UPDATE
            montant_attendu=VALUES(montant_attendu),montant_remis=VALUES(montant_remis),
            montant_enregistre=VALUES(montant_enregistre),ecart_total=VALUES(ecart_total),
            motif_ecart=VALUES(motif_ecart),taux_recouvrement=VALUES(taux_recouvrement),
            solde_cloture=VALUES(solde_cloture),valide_par=VALUES(valide_par),date_validation=NOW()
            """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setDate(1, java.sql.Date.valueOf(cl.getDateCloture()));
            ps.setBigDecimal(2, cl.getMontantAttendu());
            ps.setBigDecimal(3, cl.getMontantRemis());
            ps.setBigDecimal(4, cl.getMontantEnregistre());
            ps.setBigDecimal(5, cl.getEcartTotal());
            ps.setString(6, cl.getMotifEcart());
            ps.setBigDecimal(7, cl.getTauxRecouvrement());
            ps.setBigDecimal(8, cl.getSoldeCloture());
            ps.setString(9, cl.getValideParId());
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("saveClotureJournaliere", e);
            throw new RuntimeException(e);
        }
    }

    // ── Helpers ──────────────────────────────────────────────────
    private String findIdByNumero(String numero, Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT id FROM versement WHERE numero=?")) {
            ps.setString(1, numero);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getString("id") : null;
        }
    }

    private Versement mapRow(ResultSet rs) throws SQLException {
        Versement v = new Versement();
        v.setId(rs.getString("id"));
        v.setNumero(rs.getString("numero"));
        v.setMontantAttendu(rs.getBigDecimal("montant_attendu"));
        v.setMontantRemis(rs.getBigDecimal("montant_remis"));
        v.setMontantEnregistre(rs.getBigDecimal("montant_enregistre"));
        v.setModePaiement(rs.getString("mode_paiement"));
        v.setMotifEcart(rs.getString("motif_ecart"));
        Date dv = rs.getDate("date_versement");
        if (dv != null) v.setDateVersement(dv.toLocalDate());
        String st = rs.getString("statut");
        if (st != null) {
            try { v.setStatut(Versement.Statut.valueOf(st)); }
            catch (IllegalArgumentException e) { v.setStatut(Versement.Statut.EnAttente); }
        }
        Timestamp dc = rs.getTimestamp("date_creation");
        if (dc != null) v.setDateCreation(dc.toLocalDateTime());
        // Facture légère
        String facNum = rs.getString("fac_num");
        if (facNum != null) {
            Facture f = new Facture(); f.setId(rs.getString("facture_id")); f.setNumero(facNum);
            v.setFacture(f);
        }
        // Client
        String clId = rs.getString("cl_id");
        if (clId != null) {
            Client cl = new Client(); cl.setId(clId); cl.setNom(rs.getString("cl_nom"));
            v.setClient(cl);
        }
        // Livreur
        String livId = rs.getString("liv_id");
        if (livId != null) {
            Utilisateur u = new Utilisateur(); u.setId(livId); u.setNomComplet(rs.getString("liv_nom"));
            v.setLivreur(u);
        }
        // Caissier
        String caId = rs.getString("ca_id");
        if (caId != null) {
            Utilisateur ca = new Utilisateur(); ca.setId(caId); ca.setNomComplet(rs.getString("ca_nom"));
            v.setCaissier(ca);
        }
        return v;
    }
}
