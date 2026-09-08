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
        // Format: sortie jjmmaa
        String dd = String.format("%02d", date.getDayOfMonth());
        String mm = String.format("%02d", date.getMonthValue());
        String yy = String.valueOf(date.getYear()).substring(2);
        return "sortie " + dd + mm + yy;
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
        if (du != null) { sql.append(" AND fj.date_fiche >= ?"); params.add(Date.valueOf(du)); }
        if (au != null) { sql.append(" AND fj.date_fiche <= ?"); params.add(Date.valueOf(au)); }
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
            ps.setDate(2, Date.valueOf(fj.getDateFiche()));
            ps.setString(3, fj.getLivreur() != null ? fj.getLivreur().getId() : null);
            ps.setString(4, toDbStatut(fj.getStatut()));
            ps.setString(5, fj.getCreePar());
            ps.executeUpdate();
            String id = findIdByNumero(fj.getNumero(), c);
            if (id != null) fj.setId(id);
            return id;
        } catch (SQLException e) {
            log.error("save fiche", e);
            throw new RuntimeException(e);
        }
    }

    public void updateStatut(String ficheId, FicheJournaliere.Statut statut) {
        String sql = "UPDATE fiche_journaliere SET statut=? WHERE id=?";
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, toDbStatut(statut));
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
    public String saveLigne(LigneCommande l) {
        String sql = """
            INSERT INTO ligne_commande (id,fiche_id,client_id,produit_id,quantite_sortie,
            quantite_retournee,prix_unitaire,tarif_applicable,type_tarif,remise_pct,montant_ht,motif_retour)
            VALUES (UUID(),?,?,?,?,?,?,?,?,?,?,?)
            """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, l.getFicheId());
            ps.setString(2, l.getClient() != null ? l.getClient().getId() : null);
            ps.setString(3, l.getProduit() != null ? l.getProduit().getId() : null);
            ps.setInt(4, l.getQuantiteSortie());
            ps.setInt(5, l.getQuantiteRetournee());
            BigDecimal pu = l.getTarifApplicable() != null ? l.getTarifApplicable() : l.getPrixUnitaire();
            if (pu == null) pu = BigDecimal.ZERO;
            ps.setBigDecimal(6, pu);
            ps.setBigDecimal(7, pu);
            ps.setString(8, l.getTypeTarif() != null ? l.getTypeTarif() : "Standard");
            ps.setBigDecimal(9, l.getRemisePct() != null ? l.getRemisePct() : BigDecimal.ZERO);
            ps.setBigDecimal(10, l.getMontantHt() != null ? l.getMontantHt() : BigDecimal.ZERO);
            ps.setString(11, l.getMotifRetour());
            ps.executeUpdate();
            return null;
        } catch (SQLException e) {
            log.error("saveLigne", e);
            throw new RuntimeException(e);
        }
    }

    public void updateLigne(LigneCommande l) {
        String sql = """
            UPDATE ligne_commande SET quantite_sortie=?,quantite_retournee=?,
            prix_unitaire=?,tarif_applicable=?,remise_pct=?,montant_ht=?,motif_retour=? WHERE id=?
            """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, l.getQuantiteSortie());
            ps.setInt(2, l.getQuantiteRetournee());
            BigDecimal pu = l.getTarifApplicable() != null ? l.getTarifApplicable() : l.getPrixUnitaire();
            if (pu == null) pu = BigDecimal.ZERO;
            ps.setBigDecimal(3, pu);
            ps.setBigDecimal(4, pu);
            ps.setBigDecimal(5, l.getRemisePct() != null ? l.getRemisePct() : BigDecimal.ZERO);
            ps.setBigDecimal(6, l.getMontantHt() != null ? l.getMontantHt() : BigDecimal.ZERO);
            ps.setString(7, l.getMotifRetour());
            ps.setString(8, l.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("updateLigne", e);
            throw new RuntimeException(e);
        }
    }

    public void deleteLigne(String ligneId) {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM ligne_commande WHERE id=?")) {
            ps.setString(1, ligneId);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("deleteLigne {}", ligneId, e);
            throw new RuntimeException(e);
        }
    }

    public List<LigneCommande> findLignesByDate(LocalDate date) {
        List<LigneCommande> list = new ArrayList<>();
        String sql = """
            SELECT ls.*,
                   cl.id AS cl_id, cl.code AS cl_code, cl.nom AS cl_nom, cl.adresse AS cl_adr, cl.solde_actuel AS cl_solde,
                   p.id AS p_id, p.code AS p_code, p.libelle AS p_lib, p.prix_unitaire AS p_prix,
                   fj.id AS fj_id, fj.numero AS fj_num, fj.date_fiche AS fj_date,
                   u.id AS liv_id, u.nom_complet AS liv_nom
            FROM ligne_commande ls
            JOIN fiche_journaliere fj ON ls.fiche_id = fj.id
            JOIN client cl ON ls.client_id = cl.id
            JOIN produit p ON ls.produit_id = p.id
            LEFT JOIN utilisateur u ON fj.livreur_id = u.id
            WHERE fj.date_fiche = ?
            ORDER BY cl.nom, p.libelle
            """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(date));
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                LigneCommande l = new LigneCommande();
                l.setId(rs.getString("id"));
                l.setFicheId(rs.getString("fiche_id"));
                l.setQuantiteSortie(rs.getInt("quantite_sortie"));
                l.setQuantiteRetournee(rs.getInt("quantite_retournee"));
                BigDecimal tarif = rs.getBigDecimal("tarif_applicable");
                if (tarif == null) tarif = rs.getBigDecimal("prix_unitaire");
                l.setTarifApplicable(tarif);
                l.setTypeTarif(rs.getString("type_tarif"));
                l.setRemisePct(rs.getBigDecimal("remise_pct"));
                l.setMontantHt(rs.getBigDecimal("montant_ht"));
                l.setMotifRetour(rs.getString("motif_retour"));

                Client cl = new Client();
                cl.setId(rs.getString("cl_id"));
                cl.setCode(rs.getString("cl_code"));
                cl.setNom(rs.getString("cl_nom"));
                cl.setAdresse(rs.getString("cl_adr"));
                cl.setSoldeActuel(rs.getBigDecimal("cl_solde"));
                l.setClient(cl);

                Produit p = new Produit();
                p.setId(rs.getString("p_id"));
                p.setCode(rs.getString("p_code"));
                p.setLibelle(rs.getString("p_lib"));
                p.setPrixUnitaire(rs.getBigDecimal("p_prix"));
                l.setProduit(p);

                list.add(l);
            }
        } catch (SQLException e) {
            log.error("findLignesByDate {}", date, e);
        }
        return list;
    }

    public FicheJournaliere getOrCreateFicheJour(LocalDate date, String creePar) {
        List<FicheJournaliere> fiches = findByFilters(date, date, null, null);
        if (!fiches.isEmpty()) {
            return findById(fiches.get(0).getId()).orElse(fiches.get(0));
        }
        FicheJournaliere f = new FicheJournaliere();
        f.setDateFiche(date);
        f.setNumero(genererNumero(date));
        // On ne rattache plus à un livreur spécifique au niveau de la fiche si c'est global
        // On peut mettre un livreur par défaut ou null si la DB le permet (ici NOT NULL dans le schéma initial)
        // Vérifions le schéma : livreur_id VARCHAR(36) NOT NULL. 
        // Si c'est global, on devrait peut-être changer le schéma ou mettre l'admin.
        
        // Récupérer un utilisateur par défaut (admin ou le premier venu)
        Utilisateur defaultUser = new UtilisateurDAO().findAll().stream().findFirst().orElse(null);
        f.setLivreur(defaultUser);
        
        f.setStatut(FicheJournaliere.Statut.EnCours);
        f.setCreePar(creePar);
        String id = save(f);
        f.setId(id);
        return f;
    }

    public void recalculerTotauxFiche(String ficheId) {
        String sql = """
            UPDATE fiche_journaliere
            SET total_sorties = COALESCE((SELECT SUM(quantite_sortie * tarif_applicable) FROM ligne_commande WHERE fiche_id=?), 0),
                total_retours = COALESCE((SELECT SUM(quantite_retournee * tarif_applicable) FROM ligne_commande WHERE fiche_id=?), 0),
                total_net     = COALESCE((SELECT SUM(montant_ht) FROM ligne_commande WHERE fiche_id=?), 0)
            WHERE id=?
            """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, ficheId);
            ps.setString(2, ficheId);
            ps.setString(3, ficheId);
            ps.setString(4, ficheId);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("recalculerTotauxFiche {}", ficheId, e);
        }
    }

    public void deleteFiche(String ficheId) {
        try (Connection c = db.getConnection()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement ps = c.prepareStatement("DELETE FROM ligne_commande WHERE fiche_id=?")) {
                    ps.setString(1, ficheId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement("DELETE FROM fiche_journaliere WHERE id=?")) {
                    ps.setString(1, ficheId);
                    ps.executeUpdate();
                }
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            log.error("deleteFiche {}", ficheId, e);
            throw new RuntimeException(e);
        }
    }

    public List<LocalDate> findDatesSortieByClient(String clientId) {
        List<LocalDate> list = new ArrayList<>();
        String sql = """
            SELECT DISTINCT fj.date_fiche
            FROM ligne_commande lc
            JOIN fiche_journaliere fj ON lc.fiche_id = fj.id
            WHERE lc.client_id = ?
            ORDER BY fj.date_fiche DESC
            """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, clientId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Date d = rs.getDate(1);
                if (d != null) list.add(d.toLocalDate());
            }
        } catch (SQLException e) {
            log.error("findDatesSortieByClient {}", clientId, e);
        }
        return list;
    }

    public List<LigneCommande> findLignesByClientAndDate(String clientId, LocalDate date) {
        List<LigneCommande> list = new ArrayList<>();
        String sql = """
            SELECT lc.*,
                   p.id AS p_id, p.code AS p_code, p.libelle AS p_lib, p.prix_unitaire AS p_prix
            FROM ligne_commande lc
            JOIN fiche_journaliere fj ON lc.fiche_id = fj.id
            JOIN produit p ON lc.produit_id = p.id
            WHERE lc.client_id = ? AND fj.date_fiche = ?
            ORDER BY p.libelle
            """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, clientId);
            ps.setDate(2, Date.valueOf(date));
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                LigneCommande l = new LigneCommande();
                l.setId(rs.getString("id"));
                l.setFicheId(rs.getString("fiche_id"));
                l.setQuantiteSortie(rs.getInt("quantite_sortie"));
                l.setQuantiteRetournee(rs.getInt("quantite_retournee"));
                BigDecimal tarif = rs.getBigDecimal("tarif_applicable");
                if (tarif == null) tarif = rs.getBigDecimal("prix_unitaire");
                l.setTarifApplicable(tarif);
                l.setTypeTarif(rs.getString("type_tarif"));
                l.setRemisePct(rs.getBigDecimal("remise_pct"));
                l.setMontantHt(rs.getBigDecimal("montant_ht"));
                l.setMotifRetour(rs.getString("motif_retour"));
                Produit p = new Produit();
                p.setId(rs.getString("p_id"));
                p.setCode(rs.getString("p_code"));
                p.setLibelle(rs.getString("p_lib"));
                p.setPrixUnitaire(rs.getBigDecimal("p_prix"));
                l.setProduit(p);
                list.add(l);
            }
        } catch (SQLException e) {
            log.error("findLignesByClientAndDate {} {}", clientId, date, e);
        }
        return list;
    }

    // ── Statistiques dashboard ───────────────────────────────────
    public BigDecimal getSortiesNettesJour(LocalDate date) {
        String sql = "SELECT COALESCE(SUM(total_net),0) FROM fiche_journaliere WHERE date_fiche=?";
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(date));
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getBigDecimal(1) : BigDecimal.ZERO;
        } catch (SQLException e) {
            log.error("getSortiesNettesJour", e);
            return BigDecimal.ZERO;
        }
    }

    // ── Helpers ──────────────────────────────────────────────────
    private List<LigneCommande> findLignes(String ficheId, Connection c) throws SQLException {
        List<LigneCommande> list = new ArrayList<>();
        String sql = """
            SELECT ls.*,
                   cl.id AS cl_id, cl.code AS cl_code, cl.nom AS cl_nom,
                   p.id AS p_id, p.code AS p_code, p.libelle AS p_lib
            FROM ligne_commande ls
            JOIN client cl ON ls.client_id = cl.id
            JOIN produit p ON ls.produit_id = p.id
            WHERE ls.fiche_id=?
            """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, ficheId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                LigneCommande l = new LigneCommande();
                l.setId(rs.getString("id"));
                l.setFicheId(ficheId);
                l.setQuantiteSortie(rs.getInt("quantite_sortie"));
                l.setQuantiteRetournee(rs.getInt("quantite_retournee"));
                BigDecimal tarif = rs.getBigDecimal("tarif_applicable");
                if (tarif == null) tarif = rs.getBigDecimal("prix_unitaire");
                l.setTarifApplicable(tarif);
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
        fj.setStatut(fromDbStatut(rs.getString("statut")));
        fj.setTotalSorties(rs.getBigDecimal("total_sorties"));
        fj.setTotalRetours(rs.getBigDecimal("total_retours"));
        fj.setTotalNet(rs.getBigDecimal("total_net"));
        fj.setCreePar(rs.getString("cree_par"));
        Timestamp dc = rs.getTimestamp("date_creation");
        if (dc != null) fj.setDateCreation(dc.toLocalDateTime());
        String livId = rs.getString("liv_id");
        if (livId != null) {
            Utilisateur liv = new Utilisateur();
            liv.setId(livId);
            liv.setNomComplet(rs.getString("liv_nom"));
            fj.setLivreur(liv);
        }
        return fj;
    }

    private static String toDbStatut(FicheJournaliere.Statut statut) {
        if (statut == null) return "Brouillon";
        if (statut == FicheJournaliere.Statut.EnCours) return "En cours";
        return statut.name();
    }

    private static FicheJournaliere.Statut fromDbStatut(String s) {
        if (s == null || s.isBlank()) return FicheJournaliere.Statut.Brouillon;
        // Normaliser : retirer espaces et passer en minuscule pour comparaison fiable
        String norm = s.replace(" ", "").toLowerCase();
        if (norm.equals("encours"))   return FicheJournaliere.Statut.EnCours;
        if (norm.equals("brouillon")) return FicheJournaliere.Statut.Brouillon;
        if (norm.startsWith("compl")) return FicheJournaliere.Statut.Complétée;
        if (norm.startsWith("cl"))    return FicheJournaliere.Statut.Clôturée;
        try { return FicheJournaliere.Statut.valueOf(s); }
        catch (Exception e) {
            log.warn("Statut inconnu '{}', fallback Brouillon", s);
            return FicheJournaliere.Statut.Brouillon;
        }
    }
}
