# 🔄 Module Mouvements, Conformité & OCR IA (`mouvement`)

## 📌 Présentation du Module
Le module `mouvement` assure la traçabilité des fidèles (arrivées, départs, transferts d'églises), la gestion de leur conformité administrative (carte de membre, carnet de dîme, situation matrimoniale, baptêmes) et l'extraction automatique d'informations structurées à partir de lettres de recommandation ou formulaires scannés via une approche hybride **OCR + Intelligence Artificielle (Groq LLM / Google Gemini)** avec fallback Regex.

---

## 🏗️ Architecture et Rôle des Fichiers

```
mouvement/
├── Mouvement.java              # Entité JPA de suivi des mouvements
├── MouvementController.java    # Contrôleur REST des mouvements, de la conformité et du scan OCR/IA
├── MouvementService.java       # Service Métier de gestion des flux et conformité
├── MouvementRepository.java   # Interface de persistance JPA pour les mouvements
├── OcrService.java             # Moteur hybride d'analyse OCR (Tesseract / PDFBox) & IA (Groq LLM / Gemini)
└── dto/                        # Objets de transfert de données (DTOs)
    ├── ConformiteStatusDto.java       # Statut administratif détaillé d'un membre
    ├── FideleRecommendationDto.java   # Modèle d'extraction IA structuré des lettres pastorales
    └── OcrResultDto.java              # Résultat consolidé du scan OCR / IA
```

---

## 🤖 Moteur d'Extraction Hybride OCR & Intelligence Artificielle (`OcrService.java`)

Le traitement des documents scannés (PDF / Images) repose sur un pipeline à 3 niveaux d'exécution garants d'une précision et d'une résilience maximales :

```
[ Document PDF / Image ]
          │
          ▼
┌──────────────────────────────────┐
│  1. Extraction de Texte Brut     │
│  - PDFBox (pour PDF natifs)      │
│  - Tess4J / Tesseract (images/scan)
└──────────────────────────────────┘
          │
          ▼
┌──────────────────────────────────┐
│  2. Pipeline IA (LLM dynamic)    │
│  - Groq API (ex: Llama 3.3 70B)  │
│  - Google Gemini API (fallback)  │
│  - Autodécouverte des modèles    │
└──────────────────────────────────┘
          │
     (Si échec IA)
          ▼
┌──────────────────────────────────┐
│  3. Fallback Regex Local         │
│  - Extraction par motifs Regex   │
│  - Nettoyage et règles métier    │
└──────────────────────────────────┘
```

### Fonctionnalités de l'IA dans l'OCR :
1. **Analyse Sémantique & Structuration Strict (JSON)** :
   * Extraction automatique du nom, prénom(s), sexe, date et lieu de naissance, profession.
   * Détection du statut matrimonial, des dates de baptême (eau / Saint-Esprit).
   * Identification de l'église d'origine, de l'église de destination, du pasteur signataire et du motif de transfert.
2. **Support Multimode & Multi-API** :
   * **Groq API** : Utilise en priorité les modèles à très faible latence (Llama 3.3 70B, Gemma 2, etc.).
   * **Google Gemini API** : Relais automatique en cas d'indisponibilité ou quota Groq (découverte dynamique des modèles `gemini-2.0-flash`, `gemini-1.5-pro`, etc.).
3. **Résilience et Sécurité (Fallback Regex)** :
   * Si aucune clé d'API n'est configurée ou si les services IA sont injoignables, le parser basé sur des expressions régulières (Regex) prend automatiquement le relais sans interrompre l'expérience utilisateur.

---

## 📜 Description détaillée des Classes

### 1. `MouvementController.java`
* **Rôle** : Interface API REST pour le module.
* **Endpoints principaux** :
  * `POST /api/mouvements/ocr/scan` : Envoi d'une lettre de recommandation / formulaire scanné (PDF ou image) pour analyse automatique OCR + IA.
  * `POST /api/mouvements/arrivant` : Enregistrement d'un nouveau membre arrivant et enregistrement de son historique de mouvement à partir des données OCR validées.
  * `PUT /api/mouvements/fidele/{id}/conformite` : Mise à jour du statut de conformité administrative du fidèle (possession de carte, carnet de dîme, situation).

### 2. `MouvementService.java`
* **Rôle** : Logique métier de gestion des mouvements et de la conformité administrative.
* **Responsabilités** :
  * Enregistrement et consultation de l'historique chronologique des transferts/arrivées/départs.
  * Vérification du statut de conformité administrative des fidèles.

### 3. `OcrService.java`
* **Rôle** : Moteur intelligent d'analyse et d'extraction de données de documents.
* **Composants intégrés** :
  * **Tesseract OCR (Tess4J) & Apache PDFBox** pour l'extraction du texte brut.
  * **Groq API Client & Gemini API Client** pour la structuration intelligente par LLM avec prompt système strict.
  * **Regex Parser** pour la détection locale autonome.

### 4. `Mouvement.java` & `MouvementRepository.java`
* **`Mouvement.java`** : Entité JPA modélisant un événement dans le parcours du fidèle (date, église d'origine, église de destination, motif, type de mouvement).
* **`MouvementRepository.java`** : Interface Spring Data JPA fournissant les méthodes d'accès et de requêtage de l'historique.

---

## 📦 Data Transfer Objects (`dto/`)

* **`ConformiteStatusDto.java`** : DTO de transfert contenant le statut de conformité d'un membre (carte de membre valide, carnet de dîmes, régularité administrative).
* **`FideleRecommendationDto.java`** : DTO intermédiaire utilisé pour mapper la réponse JSON structurée issue des modèles IA (Groq/Gemini).
* **`OcrResultDto.java`** : DTO complet de retour du scan d'un document, combinant les données individuelles extraites (`nomFidele`, `pasteurSignataire`, `egliseOrigine`, `dateNaissance`, `dateBapteme`, `statutMatrimonial`, etc.) et la synthèse textuelle.
