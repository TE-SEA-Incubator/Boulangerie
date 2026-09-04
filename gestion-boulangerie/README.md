# Gestion Boulangerie — Application Desktop Java

Application de gestion des entrées/sorties d'une boulangerie.  
Stack : **Java 21 + JavaFX (AtlantaFX) + MySQL 8.x + iText 7 + BCrypt**

---

## Pré-requis

| Outil | Version minimale |
|-------|-----------------|
| Java JDK | 17 |
| Maven | 3.8+ |
| MySQL | 8.0+ |

---

## Installation en 5 étapes

### 1. Créer la base de données MySQL

```sql
CREATE DATABASE IF NOT EXISTS boulangerie
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### 2. Configurer la connexion

Éditez `src/main/resources/db.properties` :

```properties
db.url=jdbc:mysql://localhost:3306/boulangerie?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Africa/Abidjan&useSSL=false&allowPublicKeyRetrieval=true
db.username=root
db.password=root
```

En production, préférez les variables d'environnement `BOULANGERIE_DB_URL`,
`BOULANGERIE_DB_USERNAME` et `BOULANGERIE_DB_PASSWORD`, qui remplacent les
valeurs du fichier de configuration.

### 3. Initialiser le schéma

Le schéma se crée automatiquement au premier lancement (via `DatabaseInitializer`).  
Vous pouvez aussi l'exécuter manuellement :

```bash
mysql -u root -p boulangerie < src/main/resources/schema.sql
```

### 4. Compiler et construire le JAR

```bash
mvn clean package -DskipTests
mvn clean package
```

Le JAR exécutable est généré dans `target/gestion-boulangerie-1.0.0.jar`.

### 5. Lancer l'application

```bash
java -jar target/gestion-boulangerie-1.0.0.jar
```

Ou utilisez le script fourni :

```bash
./run.sh
```

---

## Connexion initiale

| Champ | Valeur |
|-------|--------|
| Identifiant | `admin` |
| Mot de passe | `Admin@2025` |

> **Changez le mot de passe dès la première connexion** via *Utilisateurs → Changer mot de passe*.

---

## Architecture du projet

```
src/main/java/com/boulangerie/
├── Main.java                          ← Point d'entrée
├── dao/                               ← Accès base de données (JDBC)
│   ├── DatabaseConnection.java
│   ├── UtilisateurDAO.java
│   ├── ProduitDAO.java
│   ├── ClientDAO.java
│   ├── FicheJournaliereDAO.java
│   ├── FactureDAO.java
│   ├── VersementDAO.java
│   ├── AuditDAO.java
│   ├── TarifClientDAO.java
│   ├── RemiseExceptionnelleDAO.java
│   ├── AutorisationDeblocageDAO.java
│   └── SoldeClientDAO.java
├── model/                             ← Entités métier
│   ├── Utilisateur, Role, Permission
│   ├── Produit, Famille, Tarif, RemiseExceptionnelle
│   ├── Client, CategorieClient, Blocage, AutorisationDeblocage
│   ├── FicheJournaliere, LigneSortie
│   ├── Facture, Avoir
│   ├── Versement, Recu, ClotureJournaliere
│   ├── SoldeClient
│   └── JournalAudit
├── service/                           ← Règles métier
│   ├── AuthService.java               ← Authentification BCrypt
│   ├── SessionService.java            ← Session + timeout inactivité
│   ├── TarifService.java              ← Résolution tarif (CDC §8.2)
│   ├── FacturationService.java        ← Génération factures + avoirs
│   ├── CaisseService.java             ← Versements + reçus
│   ├── DeblocageService.java          ← Déblocage exceptionnel Manager
│   ├── ClotureService.java            ← Clôture J et mensuelle
│   └── PdfService.java                ← Export PDF iText 7
├── ui/
│   ├── LoginFrame.java
│   ├── MainFrame.java                 ← Navigation CardLayout
│   ├── components/                    ← Composants réutilisables
│   │   ├── RoundedButton, StatusBadge, KpiCard, StyledTable, SearchField
│   ├── panels/                        ← Modules principaux
│   │   ├── DashboardPanel             ← Tableau de bord admin
│   │   ├── ProduitsPanel              ← Catalogue produits & tarifs
│   │   ├── ClientsPanel               ← Gestion clients
│   │   ├── SortiesPanel               ← Fiches journalières
│   │   ├── FacturationPanel           ← Factures & avoirs
│   │   ├── CaissePanel                ← Versements & reçus
│   │   ├── RecouvrementPanel          ← Rapprochement & clôture caisse
│   │   ├── UtilisateursPanel          ← Gestion utilisateurs & rôles
│   │   ├── RapportsPanel              ← Exports PDF
│   │   ├── AuditPanel                 ← Journal d'audit
│   │   └── ParametresPanel            ← Administration & sauvegarde
│   └── dialogs/
│       ├── ProduitDialog, ClientDialog
│       ├── SaisieDialog               ← Saisie sorties/retours
│       ├── AperçuFactureDialog        ← Aperçu impression facture
│       ├── VersementRecuDialog        ← Saisie versement + reçu
│       ├── AvoirDialog                ← Création avoir
│       └── DeblocageDialog            ← Déblocage exceptionnel
└── util/
    ├── FormatUtil.java                ← Formatage dates/montants
    ├── UIConstants.java               ← Palette couleurs + polices
    └── DatabaseInitializer.java       ← Init schéma au démarrage
```

---

## Modules fonctionnels

| Module | Description |
|--------|-------------|
| **Dashboard** | KPIs temps réel : CA jour, sorties nettes, créances, clients bloqués, écarts caisse |
| **Produits & Tarifs** | Catalogue produits, familles, tarifs multi-niveaux, remises exceptionnelles |
| **Clients** | Fiche client, catégories (Externe/Interne/Carrefour), nominatif/anonyme, soldes |
| **Sorties/Retours** | Fiches journalières par livreur, saisie lignes, validation, finalisation |
| **Facturation** | Génération auto depuis sorties nettes, factures verrouillées, avoirs traçables |
| **Caisse** | Versements, rapprochement attendu/remis, reçus électroniques, motif écart obligatoire |
| **Recouvrement** | Rapprochement de caisse, taux de recouvrement, validation clôture |
| **Utilisateurs** | 4 rôles : Admin, Comptable, Caissier, Livreur — permissions granulaires |
| **Rapports** | Export PDF : factures, recouvrement mensuel, soldes clients, état journalier, audit |
| **Audit** | Journal immuable, filtrable par entité/action/utilisateur/période, export CSV |
| **Paramètres** | Entreprise, sauvegarde/restauration BDD, clôture mensuelle, infos système |

---

## Règles métier clés (CDC)

- **Tarif** : Spécifique client > Catégorie (Externe/Interne/Carrefour) > Standard
- **Facture verrouillée** : toute correction passe par un avoir traçable
- **Blocage automatique** : déclenchement si plafond de crédit dépassé
- **Déblocage exceptionnel** : réservé Manager avec motif + engagement + durée
- **Solde clôture J = Solde ouverture J+1** pour chaque client nominatif
- **Écart de caisse** : motif obligatoire, journalisé dans l'audit
- **Client anonyme** : pas de solde individuel ni de blocage

---

## Sauvegarde recommandée

```bash
# Sauvegarde quotidienne (ajouter en cron)
mysqldump -u root -p boulangerie > backup_$(date +%F).sql
```

---

## Développement

```bash
# Compiler
mvn compile

# Tests
mvn test

# Package JAR exécutable
mvn clean package

# Lancer directement
mvn exec:java -Dexec.mainClass="com.boulangerie.Main"
```
