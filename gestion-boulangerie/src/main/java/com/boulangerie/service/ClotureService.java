package com.boulangerie.service;

import com.boulangerie.dao.*;
import com.boulangerie.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Service de clôture journalière et mensuelle.
 *
 * Règle fondamentale du CDC :
 *   Solde clôture J = Solde ouverture J+1  pour chaque client nominatif.
 */
public class ClotureService {
    private static final Logger log = LoggerFactory.getLogger(ClotureService.class);

    private final ClientDAO      clientDAO      = new ClientDAO();
    private final SoldeClientDAO soldeDAO       = new SoldeClientDAO();
    private final FactureDAO     factureDAO     = new FactureDAO();
    private final VersementDAO   versementDAO   = new VersementDAO();
    private final AuditDAO       auditDAO       = new AuditDAO();
    private final SessionService session        = SessionService.getInstance();

    /**
     * Clôture journalière complète :
     * 1. Calcule et enregistre le solde de chaque client nominatif.
     * 2. Reporte le solde de clôture J en solde d'ouverture J+1.
     * 3. Journalise la clôture.
     */
    public void cloturerJour(LocalDate date) {
        List<Client> clients = clientDAO.findAll();
        int nbTraites = 0;

        for (Client client : clients) {
            if (!client.isNominatif()) continue;

            // Solde d'ouverture = solde de clôture de la veille (ou solde actuel si 1er jour)
            LocalDate veille = date.minusDays(1);
            BigDecimal soldeOuverture = soldeDAO.getSoldeCloture(client.getId(), veille)
                .orElse(client.getSoldeActuel());

            // Sorties du jour (montant total facturé)
            BigDecimal sortiesJour = getSortiesClientJour(client.getId(), date);

            // Versements du jour
            BigDecimal versementsJour = getVersementsClientJour(client.getId(), date);

            // Calcul et persistance
            SoldeClient sc = new SoldeClient();
            sc.setClientId(client.getId());
            sc.setDateSolde(date);
            sc.setSoldeOuverture(soldeOuverture);
            sc.setSortiesDuJour(sortiesJour);
            sc.setVersementsDuJour(versementsJour);
            sc.calculer();
            soldeDAO.sauvegarder(sc);

            // Mettre à jour solde_actuel du client
            clientDAO.updateSolde(client.getId(), sc.getSoldeCloture());
            nbTraites++;
        }

        auditDAO.log(new JournalAudit(
            "Cloture", null, JournalAudit.CLOTURE,
            session.getUserId(), session.getLogin(),
            "Clôture journalière " + date + " — " + nbTraites + " clients traités"
        ));
        log.info("Clôture journalière {} terminée — {} clients", date, nbTraites);
    }

    /**
     * Clôture mensuelle : consolide les soldes du mois.
     */
    public void cloturerMois(int annee, int mois) {
        LocalDate debut = LocalDate.of(annee, mois, 1);
        LocalDate fin   = debut.withDayOfMonth(debut.lengthOfMonth());

        // Exécuter la clôture journalière pour chaque jour du mois si elle n'existe pas
        LocalDate jour = debut;
        while (!jour.isAfter(fin)) {
            try {
                cloturerJour(jour);
            } catch (Exception e) {
                log.warn("Clôture jour {} ignorée: {}", jour, e.getMessage());
            }
            jour = jour.plusDays(1);
        }

        auditDAO.log(new JournalAudit(
            "Cloture", null, JournalAudit.CLOTURE,
            session.getUserId(), session.getLogin(),
            "Clôture mensuelle " + mois + "/" + annee
        ));
        log.info("Clôture mensuelle {}/{} terminée", mois, annee);
    }

    // ── Helpers ──────────────────────────────────────────────────
    private BigDecimal getSortiesClientJour(String clientId, LocalDate date) {
        List<Facture> factures = factureDAO.findByFilters(date, date, clientId, null);
        return factures.stream()
            .filter(f -> !f.isEstAnnulee())
            .map(Facture::getMontantTtc)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal getVersementsClientJour(String clientId, LocalDate date) {
        return versementDAO.findByDate(date).stream()
            .filter(v -> v.getClient() != null && clientId.equals(v.getClient().getId()))
            .map(Versement::getMontantEnregistre)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
