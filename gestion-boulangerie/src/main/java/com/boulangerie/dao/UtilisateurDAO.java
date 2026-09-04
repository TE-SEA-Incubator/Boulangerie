package com.boulangerie.dao;

import com.boulangerie.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

public class UtilisateurDAO {
    private static final Logger log = LoggerFactory.getLogger(UtilisateurDAO.class);
    private final DatabaseConnection db = DatabaseConnection.getInstance();

    // ── Chercher par login ───────────────────────────────────────
    public Optional<Utilisateur> findByLogin(String login) {
        String sql = """
            SELECT u.id, u.login, u.mot_de_passe, u.nom_complet, u.telephone, u.email,
                   u.actif, u.date_creation, u.derniere_connexion,
                   r.id AS role_id, r.nom AS role_nom, r.description AS role_desc
            FROM utilisateur u
            JOIN role r ON u.role_id = r.id
            WHERE u.login = ?
            """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, login);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return Optional.of(mapRow(rs, c));
        } catch (SQLException e) {
            log.error("findByLogin {}", login, e);
        }
        return Optional.empty();
    }

    // ── Trouver par ID ──────────────────────────────────────────
    public Optional<Utilisateur> findById(String id) {
        String sql = """
            SELECT u.id, u.login, u.mot_de_passe, u.nom_complet, u.telephone, u.email,
                   u.actif, u.date_creation, u.derniere_connexion,
                   r.id AS role_id, r.nom AS role_nom, r.description AS role_desc
            FROM utilisateur u
            JOIN role r ON u.role_id = r.id
            WHERE u.id = ?
            """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return Optional.of(mapRow(rs, c));
        } catch (SQLException e) {
            log.error("findById {}", id, e);
        }
        return Optional.empty();
    }

    // ── Lister tous ─────────────────────────────────────────────
    public List<Utilisateur> findAll() {
        List<Utilisateur> list = new ArrayList<>();
        String sql = """
            SELECT u.id, u.login, u.mot_de_passe, u.nom_complet, u.telephone, u.email,
                   u.actif, u.date_creation, u.derniere_connexion,
                   r.id AS role_id, r.nom AS role_nom, r.description AS role_desc
            FROM utilisateur u
            JOIN role r ON u.role_id = r.id
            ORDER BY u.nom_complet
            """;
        try (Connection c = db.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) list.add(mapRow(rs, c));
        } catch (SQLException e) {
            log.error("findAll utilisateurs", e);
        }
        return list;
    }

    // ── Lister les livreurs ──────────────────────────────────────
    public List<Utilisateur> findLivreurs() {
        List<Utilisateur> list = new ArrayList<>();
        String sql = """
            SELECT u.id, u.login, u.mot_de_passe, u.nom_complet, u.telephone, u.email,
                   u.actif, u.date_creation, u.derniere_connexion,
                   r.id AS role_id, r.nom AS role_nom, r.description AS role_desc
            FROM utilisateur u
            JOIN role r ON u.role_id = r.id
            WHERE r.nom = 'LIVREUR' AND u.actif = 1
            ORDER BY u.nom_complet
            """;
        try (Connection c = db.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) list.add(mapRow(rs, c));
        } catch (SQLException e) {
            log.error("findLivreurs", e);
        }
        return list;
    }

    // ── Créer ────────────────────────────────────────────────────
    public void save(Utilisateur u) {
        String sql = """
            INSERT INTO utilisateur (id, login, mot_de_passe, nom_complet, telephone, email, role_id, actif)
            VALUES (UUID(), ?, ?, ?, ?, ?, ?, ?)
            """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, u.getLogin());
            ps.setString(2, u.getMotDePasse());
            ps.setString(3, u.getNomComplet());
            ps.setString(4, u.getTelephone());
            ps.setString(5, u.getEmail());
            ps.setString(6, u.getRole().getId());
            ps.setBoolean(7, u.isActif());
            ps.executeUpdate();
            // récupérer l'id généré
            try (ResultSet keys = ps.getGeneratedKeys()) {
                // UUID généré côté SQL, on relit
            }
        } catch (SQLException e) {
            log.error("save utilisateur", e);
            throw new RuntimeException(e);
        }
    }

    // ── Mettre à jour ────────────────────────────────────────────
    public void update(Utilisateur u) {
        String sql = """
            UPDATE utilisateur SET login=?, nom_complet=?, telephone=?, email=?,
            role_id=?, actif=? WHERE id=?
            """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, u.getLogin());
            ps.setString(2, u.getNomComplet());
            ps.setString(3, u.getTelephone());
            ps.setString(4, u.getEmail());
            ps.setString(5, u.getRole().getId());
            ps.setBoolean(6, u.isActif());
            ps.setString(7, u.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("update utilisateur", e);
            throw new RuntimeException(e);
        }
    }

    // ── Changer mot de passe ──────────────────────────────────────
    public void updatePassword(String userId, String newHashedPassword) {
        String sql = "UPDATE utilisateur SET mot_de_passe=? WHERE id=?";
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, newHashedPassword);
            ps.setString(2, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("updatePassword", e);
            throw new RuntimeException(e);
        }
    }

    // ── Mettre à jour dernière connexion ─────────────────────────
    public void updateDerniereConnexion(String userId) {
        String sql = "UPDATE utilisateur SET derniere_connexion=NOW() WHERE id=?";
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.warn("updateDerniereConnexion", e);
        }
    }

    // ── Mapper un ResultSet en Utilisateur ───────────────────────
    private Utilisateur mapRow(ResultSet rs, Connection c) throws SQLException {
        Utilisateur u = new Utilisateur();
        u.setId(rs.getString("id"));
        u.setLogin(rs.getString("login"));
        u.setMotDePasse(rs.getString("mot_de_passe"));
        u.setNomComplet(rs.getString("nom_complet"));
        u.setTelephone(rs.getString("telephone"));
        u.setEmail(rs.getString("email"));
        u.setActif(rs.getBoolean("actif"));
        Timestamp dc = rs.getTimestamp("date_creation");
        if (dc != null) u.setDateCreation(dc.toLocalDateTime());
        Timestamp lc = rs.getTimestamp("derniere_connexion");
        if (lc != null) u.setDerniereConnexion(lc.toLocalDateTime());

        Role role = new Role(rs.getString("role_id"), rs.getString("role_nom"));
        role.setDescription(rs.getString("role_desc"));
        role.setPermissions(loadPermissions(rs.getString("role_id"), c));
        u.setRole(role);
        return u;
    }

    private List<Permission> loadPermissions(String roleId, Connection c) throws SQLException {
        List<Permission> list = new ArrayList<>();
        String sql = """
            SELECT p.id, p.code, p.description
            FROM permission p
            JOIN role_permission rp ON p.id = rp.permission_id
            WHERE rp.role_id = ?
            """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, roleId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(new Permission(rs.getString("id"), rs.getString("code"), rs.getString("description")));
            }
        }
        return list;
    }

    // ── Lister les rôles ─────────────────────────────────────────
    public List<Role> findAllRoles() {
        List<Role> list = new ArrayList<>();
        String sql = "SELECT id, nom, description FROM role ORDER BY nom";
        try (Connection c = db.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                Role r = new Role(rs.getString("id"), rs.getString("nom"));
                r.setDescription(rs.getString("description"));
                list.add(r);
            }
        } catch (SQLException e) {
            log.error("findAllRoles", e);
        }
        return list;
    }
}
