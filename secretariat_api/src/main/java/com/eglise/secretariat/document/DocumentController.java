package com.eglise.secretariat.document;

import com.eglise.secretariat.document.dto.LettreRecommandationRequestDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Documents & Éditions", description = "Endpoints de génération et d'exportation de documents officiels PDF")
@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @Operation(summary = "Exporter la fiche d'inscription au format PDF")
    @GetMapping(value = "/fidele/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> getFidelePdf(@PathVariable Long id) {
        byte[] pdfBytes = documentService.generateFicheFidelePdf(id);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.builder("inline")
                .filename("fiche_fidele_" + id + ".pdf")
                .build());

        return ResponseEntity.ok().headers(headers).body(pdfBytes);
    }

    @Operation(summary = "Générer la Lettre de Recommandation sortante au format PDF")
    @PostMapping(value = "/lettre-recommandation/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> generateLettrePdf(@Valid @RequestBody LettreRecommandationRequestDto request) {
        byte[] pdfBytes = documentService.generateLettreRecommandationPdf(request);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.builder("inline")
                .filename("lettre_recommandation_" + request.fideleId() + ".pdf")
                .build());

        return ResponseEntity.ok().headers(headers).body(pdfBytes);
    }
}
