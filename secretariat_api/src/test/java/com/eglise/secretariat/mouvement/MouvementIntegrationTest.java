package com.eglise.secretariat.mouvement;

import com.eglise.model.AdresseCoordonnees;
import com.eglise.model.Fidele;
import com.eglise.model.StatutFidele;
import com.eglise.model.enumeration.Sexe;
import com.eglise.secretariat.fidele.FideleRepository;
import com.eglise.secretariat.fidele.dto.FideleDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class MouvementIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FideleRepository fideleRepository;

    @Autowired
    private MouvementRepository mouvementRepository;

    @Autowired
    private OcrService ocrService;

    @Autowired
    private ObjectMapper objectMapper;

    private Fidele savedFidele;

    @BeforeEach
    void setUp() {
        Fidele fidele = new Fidele();
        fidele.setNom("LOGOU");
        fidele.setPrenoms("Emmanuel");
        fidele.setDateNaissance(LocalDate.of(1990, 8, 15));
        fidele.setSexe(Sexe.MASCULIN);

        AdresseCoordonnees adresse = new AdresseCoordonnees();
        adresse.setDomicile("Adidogomé");
        fidele.setAdresse(adresse);

        StatutFidele statut = new StatutFidele();
        statut.setCarteMembreValide(false);
        statut.setCarnetDimeValide(false);
        fidele.setStatutFidele(statut);

        savedFidele = fideleRepository.save(fidele);
    }

    @Test
    @WithMockUser(username = "test-secretaire", roles = {"SECRETAIRE"})
    void testUpdateCarteMembreStatus() throws Exception {
        mockMvc.perform(patch("/api/mouvements/carte-membre/" + savedFidele.getId())
                        .param("valide", "true")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        Fidele updated = fideleRepository.findById(savedFidele.getId()).orElseThrow();
        assertTrue(updated.getStatutFidele().getCarteMembreValide());

        // Check if movement log has been created
        long count = mouvementRepository.count();
        assertTrue(count > 0);
    }

    @Test
    @WithMockUser(username = "test-secretaire", roles = {"SECRETAIRE"})
    void testUpdateCarnetDimeStatus() throws Exception {
        mockMvc.perform(patch("/api/mouvements/carnet-dime/" + savedFidele.getId())
                        .param("valide", "true")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        Fidele updated = fideleRepository.findById(savedFidele.getId()).orElseThrow();
        assertTrue(updated.getStatutFidele().getCarnetDimeValide());
    }

    @Test
    @WithMockUser(username = "test-secretaire", roles = {"SECRETAIRE"})
    void testRegisterFideleEntrant() throws Exception {
        FideleDto dto = new FideleDto();
        dto.setNom("AKAKPO");
        dto.setPrenoms("Koffi");
        dto.setSexe(Sexe.MASCULIN);
        dto.setTelephone("90123456");
        dto.setQuartier("Zanguera");
        dto.setLettreRecommandationPresentee(true);
        dto.setPasteurLettreRecommandation("Pasteur APEDO");
        dto.setEgliseLettreRecommandation("AD Kpalimé");
        dto.setDateLettreRecommandation(LocalDate.now());

        mockMvc.perform(post("/api/mouvements/fidele-entrant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated());

        // Verify it exists in database
        Optional<Fidele> created = fideleRepository.findAll().stream()
                .filter(f -> "AKAKPO".equals(f.getNom()))
                .findFirst();

        assertTrue(created.isPresent());
        assertEquals("Pasteur APEDO", created.get().getLettreEntrante().getNomPasteurSignataire());
    }

    @Test
    void testParseOcrTextWithComplexLayout() {
        String sampleText = "Fait à Lomé, le 13/09/2026\n" +
                "Le Pasteur\n" +
                "EGLISE DES ASSEMBLEES DE DIEU DU TOGO\n" +
                "TEMPLE DIEU NE CHANGE PAS\n" +
                "04 BP: 27 Lomé 04-Togo Tél: 0028 90 14 68 87\n" +
                "LETTRE DE RECOMMANDATION\n" +
                " Transfert  Voyage  Emploi Objet : X\n" +
                "Je soussigné :  Pasteur de l'Église des Assemblées de Dieu Temple : « DIEU NEPasteur AD\n" +
                "CHANGE PAS », atteste que le ou la nommé(e) :  est membre actifGNOUNKABITI Prunelle\n" +
                "de notre assemblée depuis : .08/09/2026\n" +
                " Date et lieu de naissance : 12/05/2005 à Lomé\n" +
                " Baptisé d'eau le : 24/08/2025\n" +
                " Baptisé du Saint-Esprit le : 26/08/2025\n" +
                " Profession : Etudiante\n" +
                " Célibataire  Fiancé(e)  Marié(e)  Divorcé(e)  Nombre d'enfant : Situation matrimoniale : X\n" +
                "0\n" +
                " Masculin  Féminin Sexe : X\n" +
                " oui  non      Sous-discipline : X Date de levée de la discipline :\n" +
                "ACTIVITÉ AU SEIN DE L'ÉGLISE LOCALE\n" +
                "Diacre/Diaconesse  Conseil des hommes  Ministère des femmes \n" +
                "Jeunesse  Interprète  Groupe musical  Choriste \n" +
                "Évangéliste  Moniteur  Accueil \n" +
                "AUTRE RENSEIGNEMENT\n" +
                " Motif / Église de destination : transfert - Église : Temple de Kpalimé\n" +
                "En foi de quoi, nous lui délivrons la présente lettre de recommandation pour servir et valoir ce que de droit.";

        com.eglise.secretariat.mouvement.dto.OcrResultDto result = ocrService.parseOcrText(sampleText);

        assertNotNull(result);
        assertEquals("GNOUNKABITI Prunelle", result.getNomFidele());
        assertEquals("Pasteur AD", result.getPasteurSignataire());
        assertNotNull(result.getEgliseOrigine());
        assertTrue(result.getEgliseOrigine().toUpperCase().contains("DIEU NE CHANGE PAS") || result.getEgliseOrigine().toUpperCase().contains("ASSEMBLEES DE DIEU"));
        assertTrue(result.getRawText().contains("12/05/2005 à Lomé"));
        assertTrue(result.getRawText().contains("transfert"));
    }

    @Test
    void testActualPdfFile() throws Exception {
        java.io.File pdfFile = new java.io.File("GNOUNKABITI_Prunelle_Lettre.pdf");
        if (pdfFile.exists()) {
            org.springframework.mock.web.MockMultipartFile multipartFile = new org.springframework.mock.web.MockMultipartFile(
                    "file",
                    pdfFile.getName(),
                    "application/pdf",
                    java.nio.file.Files.readAllBytes(pdfFile.toPath())
            );

            com.eglise.secretariat.mouvement.dto.OcrResultDto result = ocrService.processOcr(multipartFile);
            System.out.println("=== RAW EXTRACTED TEXT FROM PDF ===");
            try (org.apache.pdfbox.pdmodel.PDDocument doc = org.apache.pdfbox.Loader.loadPDF(pdfFile)) {
                System.out.println(new org.apache.pdfbox.text.PDFTextStripper().getText(doc));
            }
            System.out.println("=== ACTUAL PDF OCR RESULT SUMMARY ===");
            System.out.println(result.getRawText());
            System.out.println("=====================================");

            assertNotNull(result);
            assertEquals("GNOUNKABITI Prunelle", result.getNomFidele());
            assertNotNull(result.getEgliseOrigine());
            assertTrue(result.getEgliseOrigine().toUpperCase().contains("DIEU NE CHANGE PAS") || result.getEgliseOrigine().toUpperCase().contains("ASSEMBLEES DE DIEU"));
            assertTrue(result.getRawText().contains("12/05/2005 à Lomé"));
        }
    }

    @Test
    void testLettrePdfFile() throws Exception {
        java.io.File pdfFile = new java.io.File("lettre.pdf");
        if (pdfFile.exists()) {
            org.springframework.mock.web.MockMultipartFile multipartFile = new org.springframework.mock.web.MockMultipartFile(
                    "file",
                    pdfFile.getName(),
                    "application/pdf",
                    java.nio.file.Files.readAllBytes(pdfFile.toPath())
            );

            com.eglise.secretariat.mouvement.dto.OcrResultDto result = ocrService.processOcr(multipartFile);
            System.out.println("=== RAW EXTRACTED TEXT FROM lettre.pdf ===");
            try (org.apache.pdfbox.pdmodel.PDDocument doc = org.apache.pdfbox.Loader.loadPDF(pdfFile)) {
                System.out.println(new org.apache.pdfbox.text.PDFTextStripper().getText(doc));
            }
            System.out.println("=== OCR RESULT SUMMARY FOR lettre.pdf ===");
            System.out.println(result.getRawText());
            System.out.println("Nom: " + result.getNomFidele());
            System.out.println("Pasteur: " + result.getPasteurSignataire());
            System.out.println("Eglise: " + result.getEgliseOrigine());
            System.out.println("Date: " + result.getDatePresentation());
            System.out.println("DateNaiss: " + result.getDateNaissance());
            System.out.println("=========================================");

            assertNotNull(result);
            assertTrue("SENDO RUBEN".equalsIgnoreCase(result.getNomFidele()));
            assertEquals("Pasteur MBAIDADJE BENJAMIN", result.getPasteurSignataire());
            assertNotNull(result.getEgliseOrigine());
            assertTrue(result.getEgliseOrigine().toLowerCase().contains("baptiste"));
            assertEquals(java.time.LocalDate.of(2024, 3, 19), result.getDatePresentation());
            assertNull(result.getDateNaissance());
        }
    }
}
