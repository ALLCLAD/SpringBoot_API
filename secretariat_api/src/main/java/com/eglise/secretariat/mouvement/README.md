# 🔄 Module Mouvements & Conformité OCR (`mouvement`)

## 📌 Présentation du Module
Le module `mouvement` gère la traçabilité des fidèles (arrivées, départs, transferts d'églises), la régularisation de leur statut administratif (carte de membre, carnet de dîme) et l'extraction automatique d'informations à partir de documents scannés via un moteur OCR (Tesseract / Tess4J).

---

## 🏗️ Architecture et Rôle des Fichiers

```
mouvement/
├── Mouvement.java              # Entité locale de suivi des mouvements
├── MouvementController.java    # Contrôleur REST des mouvements et de l'OCR
├── MouvementService.java       # Service Métier de gestion des flux et conformité
├── MouvementRepository.java   # Interface de persistance JPA pour les mouvements
├── OcrService.java             # Service d'analyse OCR de documents (Tesseract)
└── dto/                        # Objets de transfert de données
    ├── ConformiteStatusDto.java
    └── OcrResultDto.java
```

---

## 📜 Description détaillée des Classes

### 1. `MouvementController.java`
* **Rôle** : Contrôleur REST du module.
* **Endpoints exposés** :
  * `POST /api/mouvements/ocr/scan` : Téléversement d'une lettre de recommandation scannée (image/PDF) et retour des données extraites par OCR.
  * `POST /api/mouvements/arrivant` : Enregistrement d'un nouveau membre arrivant d'une autre église à partir des données validées.
  * `PUT /api/mouvements/fidele/{id}/conformite` : Mise à jour du statut de conformité administrative (possession du carnet, carte de membre).

### 2. `MouvementService.java`
* **Rôle** : Logique métier liée aux déplacements des membres et à la gestion de la conformité.
* **Responsabilités** :
  * Enregistrement de l'historique des transferts et mouvements des fidèles.
  * Vérification et mise à jour de la conformité du membre (délivrance de carte, paiement des dîmes).

### 3. `OcrService.java`
* **Rôle** : Moteur de reconnaissance optique de caractères (OCR) s'appuyant sur Tess4J (Tesseract).
* **Fonctionnalités** :
  * Analyse du texte contenu dans les images/PDF téléversés.
  * Extraction d'informations clés (ex: nom du pasteur expéditeur, église de provenance, date d'émission, nom du fidèle).

### 4. `Mouvement.java` & `MouvementRepository.java`
* **`Mouvement.java`** : Entité représentant l'enregistrement d'un événement de mouvement d'un fidèle.
* **`MouvementRepository.java`** : Repository JPA permettant de consulter et stocker l'historique chronologique des mouvements.

---

## 📦 Data Transfer Objects (`dto/`)

* **`ConformiteStatusDto.java`** : Contient le statut détaillé de la conformité d'un membre (carte de membre à jour, carnet de dîme, situation administrative).
* **`OcrResultDto.java`** : Structure de retour contenant le texte brut extrait par l'OCR ainsi que les champs pré-analysés (`nomFidele`, `pasteurSignataire`, `egliseOrigine`, `datePresentation`, `rawText`).
