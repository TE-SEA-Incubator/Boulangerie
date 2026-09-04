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

public class FactureDAO {
    private static final Logger log = LoggerFactory.getLogger(FactureDAO.class);
    private final DatabaseConnection db = DatabaseConnection.getInstance();

    public String genererNumero(LocalDate date) {
        String prefix = "FAC-" + date.getYear() + "-";
        String sql = "SELECT COUNT(*) FROM facture WHERE numero LIKE ?";
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, prefix + "%");
            ResultSet rs = ps.executeQuery();
            int count = rs.next() ? rs.getInt(1) : 0;
            return prefix + String.format("%04d", count + 1);
        } catch (SQLException e) {
            return prefix + System.currentTimeMillis();
        }
    }

    public List<Facture> findAll() {
        return findByFilters(null, null, null, null);
    }

    public List<Facture> findByFilters(LocalDate du, LocalDate au, String clientId, String statut) {
        List<Facture> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
            SELECT f.*, cl.id AS cl_id, cl.code AS cl_code, cl.nom AS cl_nom,
                   u.id AS liv_id, u.nom_complet AS liv_nom
            FROM facture f
            LEFT JOIN client cl ON f.client_id = cl.id
            LEFT JOIN utilisateur u ON f.livreur_id = u.id
            WHERE 1=1
            """);
        List<Object> params = new ArrayList<>();
        if (du != null) { sql.append(" AND f.date_emission >= ?"); params.add(java.sql.Date.valueOf(du)); }
        if (au != null) { sql.append(" AND f.date_emission <= ?"); params.add(java.sql.Date.valueOf(au)); }
        if (clientId != null) { sql.append(" AND f.client_id=?"); params.add(clientId); }
        if (statut != null)   { sql.append(" AND f.statut=?"); params.add(toDbStatut(Facture.Statut.valueOf(statut))); }
        sql.append(" ORDER BY f.date_emission DESC, f.numero");
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) ps.setObject(i + 1, params.get(i));
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(mapRow(rs));
        } catch (SQLException e) {
            log.error("findByFilters factures", e);
        }
        return list;
    }

    public Optional<Facture> findById(String id) {
        String sql = """
            SELECT f.*, cl.id AS cl_id, cl.code AS cl_code, cl.nom AS cl_nom,
                   cl.solde_actuel AS cl_solde,
                   u.id AS liv_id, u.nom_complet AS liv_nom
            FROM facture f
            LEFT JOIN client cl ON f.client_id = cl.id
            LEFT JOIN utilisateur u ON f.livreur_id = u.id
            WHERE f.id=?
            """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return Optional.of(mapRow(rs));
        } catch (SQLException e) {
            log.error("findById facture {}", id, e);
        }
        return Optional.empty();
    }

    /** Évite de facturer deux fois la même fiche journalière. */
    public boolean existsForFiche(String ficheId) {
        String sql = "SELECT 1 FROM facture WHERE fiche_id=? LIMIT 1";
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, ficheId);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        } catch (SQLException e) {
            log.error("existsForFiche {}", ficheId, e);
            throw new RuntimeException(e);
        }
    }

    public String save(Facture f) {
        String sql = """
            INSERT INTO facture (id,numero,date_emission,client_id,livreur_id,montant_ht,tva_pct,
            tva_montant,montant_ttc,statut,est_verrouillee,est_annulee,fiche_id,mode_reglement,notes,cree_par)
            VALUES (UUID(),?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, f.getNumero());
            ps.setDate(2, java.sql.Date.valueOf(f.getDateEmission()));
            ps.setString(3, f.getClient() != null ? f.getClient().getId() : null);
            ps.setString(4, f.getLivreur() != null ? f.getLivreur().getId() : null);
            ps.setBigDecimal(5, f.getMontantHt());
            ps.setBigDecimal(6, f.getTvaPct());
            ps.setBigDecimal(7, f.getTvaMontant());
            ps.setBigDecimal(8, f.getMontantTtc());
            ps.setString(9, toDbStatut(f.getStatut()));
            ps.setBoolean(10, f.isEstVerrouillee());
            ps.setBoolean(11, f.isEstAnnulee());
            ps.setString(12, f.getFicheId());
            ps.setString(13, f.getModeReglement());
            ps.setString(14, f.getNotes());
            ps.setString(15, f.getCreePar());
            ps.executeUpdate();
            return findIdByNumero(f.getNumero(), c);
        } catch (SQLException e) {
            log.error("save facture", e);
            throw new RuntimeException(e);
        }
    }

    public void updateStatut(String factureId, Facture.Statut statut) {
        String sql = "UPDATE facture SET statut=? WHERE id=?";
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, toDbStatut(statut));
            ps.setString(2, factureId);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("updateStatut facture", e);
            throw new RuntimeException(e);
        }
    }

    // ── Avoirs ────────────────────────────────────────────────────
    public String genererNumeroAvoir(LocalDate date) {
        String prefix = "AV-" + date.getYear() + "-";
        String sql = "SELECT COUNT(*) FROM avoir WHERE numero LIKE ?";
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, prefix + "%");
            ResultSet rs = ps.executeQuery();
            int count = rs.next() ? rs.getInt(1) : 0;
            return prefix + String.format("%04d", count + 1);
        } catch (SQLException e) {
            return prefix + System.currentTimeMillis();
        }
    }

    public void saveAvoir(Avoir av) {
        String sql = "INSERT INTO avoir (id,numero,facture_id,date_avoir,montant,motif,cree_par) VALUES (UUID(),?,?,?,?,?,?)";
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, av.getNumero());
            ps.setString(2, av.getFactureId());
            ps.setDate(3, java.sql.Date.valueOf(av.getDateAvoir()));
            ps.setBigDecimal(4, av.getMontant());
            ps.setString(5, av.getMotif());
            ps.setString(6, av.getCreePar());
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("saveAvoir", e);
            throw new RuntimeException(e);
        }
    }

    // ── Stats dashboard ──────────────────────────────────────────
    public BigDecimal getCaJour(LocalDate date) {
        String sql = "SELECT COALESCE(SUM(montant_ttc),0) FROM facture WHERE date_emission=? AND statut!='Annulée'";
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setDate(1, java.sql.Date.valueOf(date));
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getBigDecimal(1) : BigDecimal.ZERO;
        } catch (SQLException e) {
            return BigDecimal.ZERO;
        }
    }

    public BigDecimal getCreancesEnCours() {
        String sql = "SELECT COALESCE(SUM(montant_ttc),0) FROM facture WHERE statut IN ('En attente','Partielle') AND est_annulee=0";
        try (Connection c = db.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getBigDecimal(1) : BigDecimal.ZERO;
        } catch (SQLException e) {
            return BigDecimal.ZERO;
        }
    }

    // ── Helpers ──────────────────────────────────────────────────
    private String findIdByNumero(String numero, Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT id FROM facture WHERE numero=?")) {
            ps.setString(1, numero);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getString("id") : null;
        }
    }

    private Facture mapRow(ResultSet rs) throws SQLException {
        Facture f = new Facture();
        f.setId(rs.getString("id"));
        f.setNumero(rs.getString("numero"));
        Date de = rs.getDate("date_emission");
        if (de != null) f.setDateEmission(de.toLocalDate());
        f.setMontantHt(rs.getBigDecimal("montant_ht"));
        f.setTvaPct(rs.getBigDecimal("tva_pct"));
        f.setTvaMontant(rs.getBigDecimal("tva_montant"));
        f.setMontantTtc(rs.getBigDecimal("montant_ttc"));
        String st = rs.getString("statut");
        if (st != null) f.setStatut(fromDbStatut(st));
        f.setEstVerrouillee(rs.getBoolean("est_verrouillee"));
        f.setEstAnnulee(rs.getBoolean("est_annulee"));
        f.setFicheId(rs.getString("fiche_id"));
        f.setModeReglement(rs.getString("mode_reglement"));
        f.setNotes(rs.getString("notes"));
        f.setCreePar(rs.getString("cree_par"));
        Timestamp dc = rs.getTimestamp("date_creation");
        if (dc != null) f.setDateCreation(dc.toLocalDateTime());
        // Client
        String clId = rs.getString("cl_id");
        if (clId != null) {
            Client cl = new Client();
            cl.setId(clId); cl.setCode(rs.getString("cl_code")); cl.setNom(rs.getString("cl_nom"));
            try { cl.setSoldeActuel(rs.getBigDecimal("cl_solde")); } catch (Exception ignore) {}
            f.setClient(cl);
        }
        // Livreur
        String livId = rs.getString("liv_id");
        if (livId != null) {
            Utilisateur liv = new Utilisateur();
            liv.setId(livId); liv.setNomComplet(rs.getString("liv_nom"));
            f.setLivreur(liv);
        }
        return f;
    }

    private static String toDbStatut(Facture.Statut statut) {
        return statut == Facture.Statut.EnAttente ? "En attente" : statut.name();
    }

    private static Facture.Statut fromDbStatut(String statut) {
        return "En attente".equals(statut) ? Facture.Statut.EnAttente : Facture.Statut.valueOf(statut);
    }
}
