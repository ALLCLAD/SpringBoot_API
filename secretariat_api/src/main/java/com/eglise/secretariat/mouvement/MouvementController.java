package com.eglise.secretariat.mouvement;

import com.eglise.secretariat.fidele.dto.FideleDto;
import com.eglise.secretariat.mouvement.dto.ConformiteStatusDto;
import com.eglise.secretariat.mouvement.dto.OcrResultDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Gestion des Mouvements et Conformité", description = "Endpoints pour l'OCR, la saisie des arrivants et la conformité administrative")
@RestController
@RequestMapping("/api/mouvements")
public class MouvementController {

    private final MouvementService mouvementService;
    private final OcrService ocrService;

    public MouvementController(MouvementService mouvementService, OcrService ocrService) {
        this.mouvementService = mouvementService;
        this.ocrService = ocrService;
    }

    @Operation(summary = "Mettre à jour la validité de la carte de membre")
    @PatchMapping("/carte-membre/{fideleId}")
    public ResponseEntity<ConformiteStatusDto> updateCarteMembre(
            @PathVariable Long fideleId,
            @RequestParam Boolean valide) {
        ConformiteStatusDto result = mouvementService.updateCarteMembreStatus(fideleId, valide);
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Mettre à jour la validité du carnet de dîme")
    @PatchMapping("/carnet-dime/{fideleId}")
    public ResponseEntity<ConformiteStatusDto> updateCarnetDime(
            @PathVariable Long fideleId,
            @RequestParam Boolean valide) {
        ConformiteStatusDto result = mouvementService.updateCarnetDimeStatus(fideleId, valide);
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Analyser par OCR une lettre de recommandation d'un fidèle arrivant")
    @PostMapping(value = "/ocr-scan", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<OcrResultDto> scanLetter(@RequestParam("file") MultipartFile file) {
        try {
            OcrResultDto result = ocrService.processOcr(file);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new OcrResultDto(null, null, null, null, "Erreur lors de l'OCR: " + e.getMessage()));
        }
    }

    @Operation(summary = "Enregistrer un nouveau fidèle arrivant avec sa lettre de recommandation")
    @PostMapping("/fidele-entrant")
    public ResponseEntity<FideleDto> registerFideleEntrant(@Valid @RequestBody FideleDto dto) {
        FideleDto result = mouvementService.saveFideleEntrant(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }
}
