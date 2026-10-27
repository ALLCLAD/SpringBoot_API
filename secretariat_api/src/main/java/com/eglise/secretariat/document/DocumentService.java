package com.eglise.secretariat.document;

import com.eglise.secretariat.document.dto.LettreRecommandationRequestDto;

/**
 * Contrat de service pour l'orchestration et la génération de documents.
 */
public interface DocumentService {

    /**
     * Génère le flux PDF de la fiche d'inscription d'un fidèle.
     *
     * @param fideleId l'identifiant du fidèle
     * @return tableau d'octets représentant le fichier PDF
     */
    byte[] generateFicheFidelePdf(Long fideleId);

    /**
     * Génère la lettre de recommandation pastorale sortante au format PDF.
     *
     * @param request critères et métadonnées de la lettre
     * @return tableau d'octets représentant le fichier PDF
     */
    byte[] generateLettreRecommandationPdf(LettreRecommandationRequestDto request);
}
