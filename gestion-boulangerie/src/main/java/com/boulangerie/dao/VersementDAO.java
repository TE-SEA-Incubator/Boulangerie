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
                   fj.numero AS fj_num,
                   cl.id AS cl_id, cl.nom AS cl_nom,
                   u.id AS liv_id, u.nom_complet AS liv_nom,
                   ca.id AS ca_id, ca.nom_complet AS ca_nom
            FROM versement v
            LEFT JOIN fiche_journaliere fj ON v.fiche_id = fj.id
            LEFT JOIN client cl ON v.client_id = cl.id
            LEFT JOIN utilisateur u ON v.livreur_id = u.id
            LEFT JOIN utilisateur ca ON v.caissier_id = ca.id
            WHERE v.date_versement=?
            ORDER BY v.date_creation
            """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(date));
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(mapRow(rs));
        } catch (SQLException e) {
            log.error("findByDate versements", e);
        }
        return list;
    }

    public List<Versement> findByFiche(String ficheId) {
        List<Versement> list = new ArrayList<>();
        String sql = """
            SELECT v.*,
                   fj.numero AS fj_num,
                   cl.id AS cl_id, cl.nom AS cl_nom,
                   u.id AS liv_id, u.nom_complet AS liv_nom,
                   ca.id AS ca_id, ca.nom_complet AS ca_nom
            FROM versement v
            LEFT JOIN fiche_journaliere fj ON v.fiche_id = fj.id
            LEFT JOIN client cl ON v.client_id = cl.id
            LEFT JOIN utilisateur u ON v.livreur_id = u.id
            LEFT JOIN utilisateur ca ON v.caissier_id = ca.id
            WHERE v.fiche_id=?
            """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, ficheId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(mapRow(rs));
        } catch (SQLException e) {
            log.error("findByFiche {}", ficheId, e);
        }
        return list;
    }

    public List<Versement> findByClient(String clientId) {
        List<Versement> list = new ArrayList<>();
        String sql = """
            SELECT v.*,
                   fj.numero AS fj_num,
                   cl.id AS cl_id, cl.nom AS cl_nom,
                   u.id AS liv_id, u.nom_complet AS liv_nom,
                   ca.id AS ca_id, ca.nom_complet AS ca_nom
            FROM versement v
            LEFT JOIN fiche_journaliere fj ON v.fiche_id = fj.id
            LEFT JOIN client cl ON v.client_id = cl.id
            LEFT JOIN utilisateur u ON v.livreur_id = u.id
            LEFT JOIN utilisateur ca ON v.caissier_id = ca.id
            WHERE v.client_id=?
            ORDER BY v.date_versement DESC
            """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, clientId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(mapRow(rs));
        } catch (SQLException e) {
            log.error("findByClient {}", clientId, e);
        }
        return list;
    }

    public List<Versement> findByClientAndDate(String clientId, LocalDate date) {
        List<Versement> list = new ArrayList<>();
        String sql = """
            SELECT v.*,
                   fj.numero AS fj_num,
                   cl.id AS cl_id, cl.nom AS cl_nom,
                   u.id AS liv_id, u.nom_complet AS liv_nom,
                   ca.id AS ca_id, ca.nom_complet AS ca_nom
            FROM versement v
            LEFT JOIN fiche_journaliere fj ON v.fiche_id = fj.id
            LEFT JOIN client cl ON v.client_id = cl.id
            LEFT JOIN utilisateur u ON v.livreur_id = u.id
            LEFT JOIN utilisateur ca ON v.caissier_id = ca.id
            WHERE v.client_id=? AND v.date_versement=?
            ORDER BY v.date_creation
            """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, clientId);
            ps.setDate(2, Date.valueOf(date));
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(mapRow(rs));
        } catch (SQLException e) {
            log.error("findByClientAndDate {} {}", clientId, date, e);
        }
        return list;
    }

    public String save(Versement v) {
        String sql = """
            INSERT INTO versement (id,numero,fiche_id,livreur_id,client_id,
            montant_attendu,montant_remis,mode_paiement,
            motif_ecart,date_versement,statut,caissier_id)
            VALUES (UUID(),?,?,?,?,?,?,?,?,?,?,?)
            """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, v.getNumero());
            ps.setString(2, v.getFiche() != null ? v.getFiche().getId() : null);
            ps.setString(3, v.getLivreur() != null ? v.getLivreur().getId() : null);
            ps.setString(4, v.getClient() != null ? v.getClient().getId() : null);
            ps.setBigDecimal(5, v.getMontantAttendu());
            ps.setBigDecimal(6, v.getMontantRemis());
            ps.setString(7, v.getModePaiement());
            ps.setString(8, v.getMotifEcart());
            ps.setDate(9, Date.valueOf(v.getDateVersement()));
            ps.setString(10, v.getStatut().name());
            ps.setString(11, v.getCaissier() != null ? v.getCaissier().getId() : null);
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

    /** Pour compatibilité avec les services utilisant montantEnregistre */
    public BigDecimal getMontantEnregistreJour(LocalDate date) {
        return getMontantRemisJour(date);
    }

    public BigDecimal getEcartsCaisseJour(LocalDate date) {
        // Dans le schéma actuel ecart est montant_remis - montant_attendu
        return getSomme("SELECT COALESCE(SUM(ecart),0) FROM versement WHERE date_versement=?", date);
    }

    private BigDecimal getSomme(String sql, LocalDate date) {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(date));
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
            ecart_total,motif_ecart,taux_recouvrement,valide_par,date_validation)
            VALUES (UUID(),?,?,?,?,?,?,?,NOW())
            ON DUPLICATE KEY UPDATE
            montant_attendu=VALUES(montant_attendu),montant_remis=VALUES(montant_remis),
            ecart_total=VALUES(ecart_total),
            motif_ecart=VALUES(motif_ecart),taux_recouvrement=VALUES(taux_recouvrement),
            valide_par=VALUES(valide_par),date_validation=NOW()
            """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(cl.getDateCloture()));
            ps.setBigDecimal(2, cl.getMontantAttendu());
            ps.setBigDecimal(3, cl.getMontantRemis());
            ps.setBigDecimal(4, cl.getEcartTotal());
            ps.setString(5, cl.getMotifEcart());
            ps.setBigDecimal(6, cl.getTauxRecouvrement());
            ps.setString(7, cl.getValideParId());
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
        // On mappe montant_remis sur montantEnregistre pour la compatibilité
        v.setMontantEnregistre(rs.getBigDecimal("montant_remis"));
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
        // Fiche Journalière légère
        String fjId = rs.getString("fiche_id");
        if (fjId != null) {
            FicheJournaliere fj = new FicheJournaliere(); 
            fj.setId(fjId); 
            fj.setNumero(rs.getString("fj_num"));
            v.setFiche(fj);
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

    public Map<String, BigDecimal> findTotalVersementsByDate(LocalDate date) {
        Map<String, BigDecimal> map = new java.util.HashMap<>();
        String sql = "SELECT client_id, SUM(montant_remis) FROM versement WHERE date_versement=? AND client_id IS NOT NULL GROUP BY client_id";
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(date));
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                map.put(rs.getString(1), rs.getBigDecimal(2));
            }
        } catch (SQLException e) {
            log.error("findTotalVersementsByDate {}", date, e);
        }
        return map;
    }

    public void enregistrerOuMettreAJourVersement(String ficheId, String clientId, LocalDate date, BigDecimal montant, String caissierId) {
        List<Versement> existants = findByClientAndDate(clientId, date);
        if (!existants.isEmpty()) {
            Versement v = existants.get(0);
            String sql = "UPDATE versement SET montant_remis=?, montant_attendu=?, statut='Payé' WHERE id=?";
            try (Connection c = db.getConnection();
                 PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setBigDecimal(1, montant);
                ps.setBigDecimal(2, montant);
                ps.setString(3, v.getId());
                ps.executeUpdate();
            } catch (SQLException e) {
                log.error("update versement", e);
            }
        } else {
            Versement v = new Versement();
            v.setNumero(genererNumero());
            if (ficheId != null) {
                FicheJournaliere f = new FicheJournaliere();
                f.setId(ficheId);
                v.setFiche(f);
            }
            Client cl = new Client();
            cl.setId(clientId);
            v.setClient(cl);
            v.setMontantAttendu(montant);
            v.setMontantRemis(montant);
            v.setModePaiement("Espèces");
            v.setDateVersement(date);
            v.setStatut(Versement.Statut.Payé);
            if (caissierId != null) {
                Utilisateur u = new Utilisateur();
                u.setId(caissierId);
                v.setCaissier(u);
            }
            save(v);
        }
    }
}
