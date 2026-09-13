# ⛪ API Secrétariat Église (Spring Boot)

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.0-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://www.oracle.com/java/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-14%2B-blue.svg)](https://www.postgresql.org/)
[![Package-Mapping](https://img.shields.io/badge/Package--Mapping-v1.0.0-blueviolet.svg)](https://github.com/ALLCLAD/Package-Mapping)

Bienvenue sur le projet **API Secrétariat Église**. Cette application backend développée sous **Spring Boot 3.3** et **Java 21** gère l'ensemble de la logique métier du secrétariat d'église (authentification, gestion des membres fidèles, génération de documents PDF officiels, gestion des mouvements/OCR et tableau de bord).

Le projet s'appuie sur la librairie partagée externe [`Package-Mapping`](https://github.com/ALLCLAD/Package-Mapping) pour le modèle de données JPA (`com.eglise.model.*`).

---

## 🗺️ Navigation dans le projet & Architecture Modulaire

Le code backend est structuré en **modules métier** étanches situés dans `src/main/java/com/eglise/secretariat/`. Chaque module dispose de sa propre documentation dédiée :

* 🔐 [**Module `auth` (Authentification & Profils)**](src/main/java/com/eglise/secretariat/auth/README.md)
* 👥 [**Module `fidele` (Gestion des Fidèles)**](src/main/java/com/eglise/secretariat/fidele/README.md)
* 📄 [**Module `document` (Exportations PDF)**](src/main/java/com/eglise/secretariat/document/README.md)
* 🔄 [**Module `mouvement` (OCR & Conformité Administrative)**](src/main/java/com/eglise/secretariat/mouvement/README.md)
* 📊 [**Module `dashboard` (Statistiques et Métriques)**](src/main/java/com/eglise/secretariat/dashboard/README.md)
* 🛠️ [**Module `shared` (Sécurité JWT, Configuration & Exceptions)**](src/main/java/com/eglise/secretariat/shared/README.md)

---

## 🛠️ Prérequis Logiciels

### Outils requis :
* **Git** ([Télécharger Git](https://git-scm.com/))
* **PostgreSQL** (version 14+) + **pgAdmin** ou **DBeaver** ([Télécharger PostgreSQL](https://www.postgresql.org/download/))
* **Java JDK 21** ([Télécharger Eclipse Temurin JDK 21](https://adoptium.net/))

> 💡 **Remarque importante (Maven Wrapper) :**  
> Vous n'avez **pas besoin d'installer Maven** séparément. Le projet inclut `mvnw` (Linux/Mac) et `mvnw.cmd` (Windows).

---

## 🚀 Installation & Démarrage Rapide

### 1. Cloner le dépôt Git
```bash
git clone https://github.com/ALLCLAD/secretariat_api.git
cd secretariat_api
```

### 2. Créer la Base de Données PostgreSQL
Dans `psql`, pgAdmin ou DBeaver, exécutez la commande suivante :
```sql
CREATE DATABASE secretariat_db;
```

### 3. Configurer les Variables d'Environnement (`.env`)
À la racine du projet (au même niveau que le fichier `pom.xml`), créez un fichier `.env` avec le contenu suivant :
```env
# Configuration PostgreSQL local
DB_URL=jdbc:postgresql://localhost:5432/secretariat_db
DB_USERNAME=postgres
DB_PASSWORD=votre_mot_de_passe_local

# Configuration Clés API IA pour l'OCR & Structuration (Optionnel)
GROQ_API_KEY=votre_cle_api_groq
GEMINI_API_KEY=votre_cle_api_gemini
```

> 💡 **Remarque sur les services IA (Groq / Gemini) :**  
> L'extraction intelligente OCR du module `mouvement` utilise en priorité **Groq AI** (Llama 3.3 70B) puis **Google Gemini AI** en fallback. Si aucune clé d'API n'est configurée, un parser Regex local autonome prend automatiquement le relais.

> ⚠️ **IMPORTANT :** Le fichier `.env` contient des identifiants locaux secrets et des clés d'API. Il est ignoré par Git via `.gitignore`. Ne le poussez jamais sur le dépôt distant.

---

## 💻 Configuration IDE & Lancement

### IntelliJ IDEA (Recommandé)
1. **File** ➔ **Open...** ➔ Sélectionnez le dossier `secretariat_api`.
2. Attendez l'importation Maven automatique (téléchargement de `Package-Mapping` depuis JitPack).
3. En cas de problème de dépendances : Clic droit sur `pom.xml` ➔ **Maven** ➔ **Reload Project**.
4. Configurez le SDK dans **File** ➔ **Project Structure** ➔ **Project** ➔ **SDK 21**.
5. Exécutez la classe principale `com.eglise.secretariat.SecretariatApiApplication`.

### VS Code
1. Installez le pack d'extension **Extension Pack for Java**.
2. Ouvrez le dossier dans VS Code.
3. Si les classes du package `com.eglise.model` ne sont pas détectées : Palette de commandes (`Ctrl + Shift + P`) ➔ `Java: Clean Java Language Server Workspace` ➔ `Restart and delete`.
4. Lancez via l'option **Run** sur la méthode `main` dans `SecretariatApiApplication.java`.

### Terminal (Maven Wrapper)
```bash
# Compilation du projet
./mvnw clean compile

# Démarrage du serveur Spring Boot
./mvnw spring-boot:run
```
L'API s'exécute par défaut sur **`http://localhost:8081`**.

---

## 🧪 Tests d'Intégration & Rendu PDF

Pour valider le moteur de génération des documents PDF sans dépendre de la base de données :
```bash
./mvnw test -Dtest=PdfGenerationTest
```
Ce test génère à la racine du projet deux documents PDF de démonstration :
- `test_fiche_inscription.pdf`
- `test_lettre_recommandation.pdf`

---

## 📖 Documentation OpenAPI & Swagger UI

Accédez à la documentation Swagger interactive une fois l'application démarrée :
* 🌐 **Swagger UI** : [http://localhost:8081/swagger-ui/index.html](http://localhost:8081/swagger-ui/index.html)
* 📄 **Spécification JSON** : [http://localhost:8081/v3/api-docs](http://localhost:8081/v3/api-docs)

> 🔑 **Pour exécuter les endpoints sécurisés dans Swagger UI :**
> 1. Effectuez la requête `POST /api/auth/login` avec vos identifiants pour obtenir le jeton JWT.
> 2. Cliquez sur le bouton **Authorize** en haut à droite.
> 3. Entrez votre jeton dans le champ `Value` et validez.

---

## ❓ Résolution des Problèmes Courants

| Symptôme / Erreur | Cause probable | Solution |
| :--- | :--- | :--- |
| `FATAL: authentification par mot de passe échouée` | Mot de passe PostgreSQL erroné | Vérifiez la valeur de `DB_PASSWORD` dans le fichier `.env`. |
| `Port 8081 already in use` | Port occupé par un autre processus | Modifiez `server.port` dans `src/main/resources/application.properties`. |
| `Package com.eglise.model does not exist` | Dépendance JitPack non résolue | Exécutez `./mvnw clean compile` ou rechargez le projet Maven dans l'IDE. |
