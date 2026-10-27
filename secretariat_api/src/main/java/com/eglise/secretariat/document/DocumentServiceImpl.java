package com.eglise.secretariat.document;

import com.eglise.model.Fidele;
import com.eglise.secretariat.document.dto.LettreRecommandationRequestDto;
import com.eglise.secretariat.fidele.FideleRepository;
import com.eglise.secretariat.shared.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implémentation du service documentaire garantissant l'isolation 3-tiers.
 */
@Service
@Transactional(readOnly = true)
public class DocumentServiceImpl implements DocumentService {

    private final FideleRepository fideleRepository;
    private final FidelePdfExporter fidelePdfExporter;
    private final LetterExporter letterExporter;

    public DocumentServiceImpl(FideleRepository fideleRepository,
                               FidelePdfExporter fidelePdfExporter,
                               LetterExporter letterExporter) {
        this.fideleRepository = fideleRepository;
        this.fidelePdfExporter = fidelePdfExporter;
        this.letterExporter = letterExporter;
    }

    @Override
    public byte[] generateFicheFidelePdf(Long fideleId) {
        Fidele fidele = fideleRepository.findById(fideleId)
                .orElseThrow(() -> new ResourceNotFoundException("Fidèle non trouvé avec l'ID : " + fideleId));
        return fidelePdfExporter.generateFidelePdf(fidele);
    }

    @Override
    public byte[] generateLettreRecommandationPdf(LettreRecommandationRequestDto request) {
        Fidele fidele = fideleRepository.findById(request.fideleId())
                .orElseThrow(() -> new ResourceNotFoundException("Fidèle non trouvé avec l'ID : " + request.fideleId()));
        return letterExporter.generateLettreRecommandationPdf(fidele, request);
    }
}
