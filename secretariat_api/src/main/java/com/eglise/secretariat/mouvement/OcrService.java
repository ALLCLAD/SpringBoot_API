package com.eglise.secretariat.mouvement;

import com.eglise.secretariat.mouvement.dto.FideleRecommendationDto;
import com.eglise.secretariat.mouvement.dto.OcrResultDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.sourceforge.tess4j.Tesseract;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class OcrService {

    private static final Logger log = LoggerFactory.getLogger(OcrService.class);

    private static final String GROQ_SYSTEM_PROMPT = """
        Tu es un assistant IA expert en analyse de documents ecclésiastiques et en extraction de données d'état civil.
        Ta tâche est d'analyser le texte brut issu d'un traitement OCR (lettre de recommandation pastorale ou formulaire de transfert de membre).
        
        ### FORMAT DE SORTIE EXIGÉ (JSON STRICT) :
        Tu dois extraire et structurer les informations sous la forme d'un objet JSON strict respectant exactement cette structure :
        
        {
          "nomFidele": "Nom de famille du fidèle en MAJUSCULES (ex: KOUASSI)",
          "prenomFidele": "Prénom(s) du fidèle (ex: Jean-Marc)",
          "sexe": "MASCULIN ou FEMININ si mentionné ou déduisable du prénom/titre (Sieur/Dame/Frère/Sœur), sinon null",
          "dateNaissance": "Date de naissance au format DD/MM/YYYY ou YYYY-MM-DD si présente, sinon null",
          "lieuNaissance": "Ville ou lieu de naissance si présent, sinon null",
          "profession": "Profession ou métier si présent, sinon null",
          "statutMatrimonial": "CELIBATAIRE, MARIE, DIVORCE ou VEUF si présent ou déduit des cases cochées, sinon null",
          "dateBaptemeEau": "Date du baptême d'eau/immersion si présente (format DD/MM/YYYY ou YYYY-MM-DD), sinon null",
          "dateBaptemeEsprit": "Date du baptême du Saint-Esprit si présente (format DD/MM/YYYY ou YYYY-MM-DD), sinon null",
          "egliseOrigine": "Nom de l'église émettrice / d'origine (ex: Temple de Kpalimé, Assemblée de Dieu de Lomé)",
          "nomPasteurRecommandateur": "Nom et prénom du Pasteur signataire (ex: Pasteur Jean KOUASSI)",
          "egliseDestination": "Nom de l'église d'accueil/destination si mentionnée, sinon null",
          "motifTransfert": "Raison ou motif du transfert (ex: Études, Déménagement, Emploi, Voyage), sinon null",
          "remarquesOuQualites": "Notes pastorales, responsabilités occupées (ex: Choriste, Diacre) ou témoignage, sinon null"
        }
        
        ### CONSIGNES STRICTES DE PARSING :
        1. Analyse aussi bien les lettres narratives ("Je soussigné Pasteur X...") que les formulaires pré-imprimés avec des cases à cocher (ex: [X] Marié, Féminin (X)).
        2. Réponds UNIQUEMENT avec l'objet JSON valide. Ne mets AUCUN texte explicatif avant ou après, et n'utilise PAS de balises markdown (pas de ```json).
        3. Si une donnée n'est pas trouvée dans le texte OCR, attribue-lui la valeur null. N'invente AUCUNE information.
        4. Ne confonds jamais le nom du Pasteur signataire avec le nom du Fidèle recommandé.
        """;

    @Value("${groq.api.key:}")
    private String groqApiKey;

    @Value("${groq.api.url:https://api.groq.com/openai/v1/chat/completions}")
    private String groqApiUrl;

    @Value("${groq.model:llama-3.3-70b-versatile}")
    private String groqModel;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Value("${gemini.api.key:}")
    private String geminiApiKey;

    public OcrResultDto processOcr(MultipartFile file) throws IOException {
        String extractedText = null;
        if (file.getOriginalFilename() != null && file.getOriginalFilename().toLowerCase().endsWith(".pdf")) {
            try (org.apache.pdfbox.pdmodel.PDDocument pdfDoc = org.apache.pdfbox.Loader.loadPDF(file.getBytes())) {
                org.apache.pdfbox.text.PDFTextStripper stripper = new org.apache.pdfbox.text.PDFTextStripper();
                extractedText = stripper.getText(pdfDoc);
            } catch (Exception ignored) {}
        }

        if (extractedText == null || extractedText.isBlank()) {
            File convFile = new File(System.getProperty("java.io.tmpdir") + "/" + System.currentTimeMillis() + "_" + file.getOriginalFilename());
            try (FileOutputStream fos = new FileOutputStream(convFile)) {
                fos.write(file.getBytes());
            }

            try {
                Tesseract tesseract = new Tesseract();
                String datapath = System.getenv("TESSDATA_PREFIX");
                if (datapath == null) {
                    datapath = System.getProperty("TESSDATA_PREFIX");
                }
                if (datapath == null) {
                    File f1 = new File("C:/Program Files/Tesseract-OCR/tessdata");
                    if (f1.exists()) datapath = f1.getAbsolutePath();
                    else {
                        File f2 = new File("./tessdata");
                        if (f2.exists()) datapath = f2.getAbsolutePath();
                    }
                }
                if (datapath != null) {
                    tesseract.setDatapath(datapath);
                }
                tesseract.setLanguage("fra");
                extractedText = tesseract.doOCR(convFile);
            } catch (Throwable t) {
                System.err.println("Tesseract non disponible ou tessdata absent : " + t.getMessage());
            } finally {
                convFile.delete();
            }
        }

        if (extractedText == null) {
            extractedText = "";
        }

        if (!extractedText.isBlank()) {
            // 1. Essai avec Groq API (découverte dynamique des modèles actifs)
            OcrResultDto groqResult = callGroqApi(extractedText);
            if (groqResult != null) {
                return groqResult;
            }

            // 2. Essai avec Google Gemini API (découverte dynamique des modèles actifs)
            OcrResultDto geminiResult = callGeminiApi(extractedText);
            if (geminiResult != null) {
                return geminiResult;
            }
        }

        // 3. Fallback final : Parser Regex local
        return parseOcrText(extractedText);
    }

    private static final java.util.List<String> CANDIDATE_MODELS = java.util.List.of(
            "llama-3.3-70b-versatile",
            "llama3-8b-8192",
            "gemma2-9b-it",
            "deepseek-r1-distill-llama-70b",
            "llama-3.2-11b-vision-preview"
    );

    public OcrResultDto callGroqApi(String rawText) {
        String apiKey = getEffectiveApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            log.info("Clé Groq API non configurée.");
            return null;
        }

        java.util.List<String> modelsToTry = new java.util.ArrayList<>();
        if (groqModel != null && !groqModel.isBlank()) {
            modelsToTry.add(groqModel.trim());
        }
        java.util.List<String> discoveredModels = fetchAvailableGroqModels(apiKey);
        for (String m : discoveredModels) {
            if (!modelsToTry.contains(m)) {
                modelsToTry.add(m);
            }
        }

        log.info("Modèles Groq disponibles à tester : {}", modelsToTry);

        for (String modelName : modelsToTry) {
            try {
                ObjectNode root = objectMapper.createObjectNode();
                root.put("model", modelName);

                ArrayNode messages = root.putArray("messages");
                ObjectNode sysMsg = messages.addObject();
                sysMsg.put("role", "system");
                sysMsg.put("content", GROQ_SYSTEM_PROMPT);

                ObjectNode userMsg = messages.addObject();
                userMsg.put("role", "user");
                userMsg.put("content", rawText);

                root.put("temperature", 0.1);
                ObjectNode responseFormat = root.putObject("response_format");
                responseFormat.put("type", "json_object");

                String jsonPayload = objectMapper.writeValueAsString(root);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(groqApiUrl))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + apiKey)
                        .POST(HttpRequest.BodyPublishers.ofString(jsonPayload, StandardCharsets.UTF_8))
                        .timeout(Duration.ofSeconds(15))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    JsonNode resNode = objectMapper.readTree(response.body());
                    JsonNode choices = resNode.path("choices");
                    if (choices.isArray() && !choices.isEmpty()) {
                        String jsonContent = choices.get(0).path("message").path("content").asText();
                        jsonContent = jsonContent.replaceAll("^```json\\s*", "").replaceAll("```$", "").trim();
                        FideleRecommendationDto groqDto = objectMapper.readValue(jsonContent, FideleRecommendationDto.class);
                        log.info("Extraction Groq API réussie (modèle {}) pour le fidèle : {} {}", modelName, groqDto.getNomFidele(), groqDto.getPrenomFidele());
                        return mapGroqDtoToOcrResultDto(groqDto, rawText);
                    }
                } else if (response.statusCode() == 404 || response.statusCode() == 400) {
                    log.warn("Groq API modèle [{}] non disponible (statut HTTP {})", modelName, response.statusCode());
                } else {
                    log.warn("Groq API (modèle {}) a répondu avec le statut HTTP {} : {}", modelName, response.statusCode(), response.body());
                }
            } catch (Exception e) {
                log.error("Erreur lors de l'appel à l'API Groq (modèle {}) : {}", modelName, e.getMessage());
            }
        }

        log.warn("Aucun modèle Groq n'a pu répondre avec succès.");
        return null;
    }

    public OcrResultDto callGeminiApi(String rawText) {
        String apiKey = geminiApiKey != null && !geminiApiKey.isBlank() ? geminiApiKey : System.getenv("GEMINI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            return null;
        }

        java.util.List<String> geminiModels = fetchAvailableGeminiModels(apiKey);
        log.info("Modèles Gemini disponibles découverts dynamiquement : {}", geminiModels);

        for (String modelName : geminiModels) {
            try {
                String url = "https://generativelanguage.googleapis.com/v1beta/models/" + modelName + ":generateContent?key=" + apiKey.trim();

                ObjectNode root = objectMapper.createObjectNode();

                // System Instruction
                ObjectNode sysInstNode = root.putObject("system_instruction");
                ArrayNode sysParts = sysInstNode.putArray("parts");
                sysParts.addObject().put("text", GROQ_SYSTEM_PROMPT);

                // User Content
                ArrayNode contents = root.putArray("contents");
                ObjectNode userContentObj = contents.addObject();
                ArrayNode userParts = userContentObj.putArray("parts");
                userParts.addObject().put("text", rawText);

                // Generation Config
                ObjectNode genConfig = root.putObject("generationConfig");
                genConfig.put("temperature", 0.1);
                genConfig.put("responseMimeType", "application/json");

                String jsonPayload = objectMapper.writeValueAsString(root);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonPayload, StandardCharsets.UTF_8))
                        .timeout(Duration.ofSeconds(15))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    JsonNode resNode = objectMapper.readTree(response.body());
                    JsonNode candidates = resNode.path("candidates");
                    if (candidates.isArray() && !candidates.isEmpty()) {
                        JsonNode parts = candidates.get(0).path("content").path("parts");
                        if (parts.isArray() && !parts.isEmpty()) {
                            String jsonContent = parts.get(0).path("text").asText();
                            jsonContent = jsonContent.replaceAll("^```json\\s*", "").replaceAll("```$", "").trim();
                            FideleRecommendationDto dto = objectMapper.readValue(jsonContent, FideleRecommendationDto.class);
                            log.info("Extraction Gemini API ({}) réussie pour le fidèle : {} {}", modelName, dto.getNomFidele(), dto.getPrenomFidele());
                            return mapGroqDtoToOcrResultDto(dto, rawText);
                        }
                    }
                } else {
                    log.warn("Gemini API (modèle {}) a répondu avec le statut HTTP {} : {}", modelName, response.statusCode(), response.body());
                }
            } catch (Exception e) {
                log.error("Erreur lors de l'appel à l'API Gemini ({}) : {}", modelName, e.getMessage());
            }
        }

        log.warn("Aucun modèle Gemini API n'a pu répondre avec succès.");
        return null;
    }

    private java.util.List<String> fetchAvailableGeminiModels(String apiKey) {
        java.util.List<String> discovered = new java.util.ArrayList<>();
        try {
            String url = "https://generativelanguage.googleapis.com/v1beta/models?key=" + apiKey.trim();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .timeout(Duration.ofSeconds(5))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode resNode = objectMapper.readTree(response.body());
                JsonNode modelsNode = resNode.path("models");
                if (modelsNode.isArray()) {
                    for (JsonNode mNode : modelsNode) {
                        String name = mNode.path("name").asText();
                        JsonNode methods = mNode.path("supportedGenerationMethods");
                        boolean supportsGenContent = false;
                        if (methods.isArray()) {
                            for (JsonNode meth : methods) {
                                if ("generateContent".equals(meth.asText())) {
                                    supportsGenContent = true;
                                    break;
                                }
                            }
                        }
                        if (supportsGenContent && name.startsWith("models/")) {
                            String modelId = name.substring("models/".length());
                            String lower = modelId.toLowerCase();
                            // Filtrer les modèles spécialisés (audio, tts, vision-only, lyria, robotics, etc.)
                            if (!lower.contains("tts") && !lower.contains("lyria") && !lower.contains("robotics") 
                                    && !lower.contains("computer-use") && !lower.contains("transcribe") 
                                    && !lower.contains("embedding") && !lower.contains("imagen")
                                    && !lower.contains("gemini-2.5") && !lower.contains("gemma")
                                    && !lower.contains("nano-banana")) {
                                discovered.add(modelId);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Impossible de lister dynamiquement les modèles Gemini : {}", e.getMessage());
        }

        java.util.List<String> defaults = java.util.List.of("gemini-2.0-flash", "gemini-1.5-flash", "gemini-1.5-pro");
        for (String def : defaults) {
            if (!discovered.contains(def)) {
                discovered.add(def);
            }
        }
        return discovered;
    }

    private java.util.List<String> fetchAvailableGroqModels(String apiKey) {
        java.util.List<String> discovered = new java.util.ArrayList<>();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.groq.com/openai/v1/models"))
                    .header("Authorization", "Bearer " + apiKey.trim())
                    .GET()
                    .timeout(Duration.ofSeconds(5))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode resNode = objectMapper.readTree(response.body());
                JsonNode dataNode = resNode.path("data");
                if (dataNode.isArray()) {
                    for (JsonNode mNode : dataNode) {
                        boolean active = mNode.path("active").asBoolean(true);
                        if (active) {
                            String id = mNode.path("id").asText();
                            if (!id.isBlank()) {
                                discovered.add(id);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Impossible de lister dynamiquement les modèles Groq : {}", e.getMessage());
        }

        for (String cand : CANDIDATE_MODELS) {
            if (!discovered.contains(cand)) {
                discovered.add(cand);
            }
        }
        return discovered;
    }

    private String getEffectiveApiKey() {
        if (groqApiKey != null && !groqApiKey.isBlank()) {
            return groqApiKey;
        }
        return System.getenv("GROQ_API_KEY");
    }

    private OcrResultDto mapGroqDtoToOcrResultDto(FideleRecommendationDto groqDto, String rawText) {
        String nomComplet = combineNomPrenom(groqDto.getNomFidele(), groqDto.getPrenomFidele());

        OcrResultDto regexResult = parseOcrText(rawText);

        String pasteur = groqDto.getNomPasteurRecommandateur() != null && !groqDto.getNomPasteurRecommandateur().isBlank()
                ? groqDto.getNomPasteurRecommandateur() : regexResult.getPasteurSignataire();

        String eglise = groqDto.getEgliseOrigine() != null && !groqDto.getEgliseOrigine().isBlank()
                ? groqDto.getEgliseOrigine() : regexResult.getEgliseOrigine();

        LocalDate datePres = regexResult.getDatePresentation() != null ? regexResult.getDatePresentation() : LocalDate.now();

        OcrResultDto dto = new OcrResultDto(
                nomComplet != null ? nomComplet : regexResult.getNomFidele(),
                pasteur,
                eglise,
                datePres,
                rawText
        );

        dto.setLieuNaissance(groqDto.getLieuNaissance() != null ? groqDto.getLieuNaissance() : regexResult.getLieuNaissance());
        dto.setProfession(groqDto.getProfession() != null ? groqDto.getProfession() : regexResult.getProfession());
        dto.setSexe(groqDto.getSexe() != null ? groqDto.getSexe() : regexResult.getSexe());
        dto.setStatutMatrimonial(groqDto.getStatutMatrimonial() != null ? groqDto.getStatutMatrimonial() : regexResult.getStatutMatrimonial());
        dto.setMotif(groqDto.getMotifTransfert() != null ? groqDto.getMotifTransfert() : regexResult.getMotif());

        if (groqDto.getDateNaissance() != null && !groqDto.getDateNaissance().isBlank()) {
            dto.setDateNaissance(parseDate(groqDto.getDateNaissance()));
        }
        if (dto.getDateNaissance() == null) {
            dto.setDateNaissance(regexResult.getDateNaissance());
        }

        if (groqDto.getDateBaptemeEau() != null && !groqDto.getDateBaptemeEau().isBlank()) {
            dto.setDateBapteme(parseDate(groqDto.getDateBaptemeEau()));
        }
        if (dto.getDateBapteme() == null) {
            dto.setDateBapteme(regexResult.getDateBapteme());
        }

        if (groqDto.getDateBaptemeEsprit() != null && !groqDto.getDateBaptemeEsprit().isBlank()) {
            dto.setDateBaptemeEsprit(parseDate(groqDto.getDateBaptemeEsprit()));
        }
        if (dto.getDateBaptemeEsprit() == null) {
            dto.setDateBaptemeEsprit(regexResult.getDateBaptemeEsprit());
        }

        return dto;
    }

    private String combineNomPrenom(String nom, String prenom) {
        if ((nom == null || nom.isBlank()) && (prenom == null || prenom.isBlank())) {
            return null;
        }
        if (nom != null && !nom.isBlank() && prenom != null && !prenom.isBlank()) {
            return nom.trim().toUpperCase() + " " + prenom.trim();
        }
        if (nom != null && !nom.isBlank()) {
            return nom.trim().toUpperCase();
        }
        return prenom.trim();
    }

    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;
        String clean = dateStr.trim();
        try {
            return LocalDate.parse(clean);
        } catch (Exception ignored) {}
        return parseDateStringOrFrench(clean);
    }

    public OcrResultDto parseOcrText(String text) {
        if (text == null || text.isBlank()) {
            return new OcrResultDto(null, null, null, null, "Aucun texte n'a pu être extrait du document.");
        }

        String cleanText = text.replace('\u00A0', ' ')
                .replace('\u202F', ' ')
                .replace('\uFFFD', 'é')
                .replaceAll("&nbsp;", " ");

        String nomFidele = extractNomFidele(cleanText);
        String pasteur = extractPasteur(cleanText);
        String eglise = extractEglise(cleanText);
        LocalDate date = extractDate(cleanText);

        LocalDate dateNaissance = extractDateNaissance(cleanText);
        String lieuNaissance = extractLieuNaissance(cleanText);
        String profession = extractRegexMatch(cleanText, "(?i)(?:profession|m[ée\uFFFD]tier)\\s*:?\\s*([^\\r\\n•]+)");
        String sexe = extractSexe(cleanText);
        String statutMatrimonial = extractStatutMatrimonial(cleanText);
        LocalDate dateBapteme = extractDateBaptemeEau(cleanText);
        LocalDate dateBaptemeEsprit = extractDateBaptemeEsprit(cleanText);
        String motif = extractMotif(cleanText);

        String summaryText = buildCleanSummaryText(nomFidele, pasteur, eglise, date, cleanText);

        OcrResultDto dto = new OcrResultDto(nomFidele, pasteur, eglise, date, summaryText);
        dto.setDateNaissance(dateNaissance);
        dto.setLieuNaissance(lieuNaissance);
        dto.setProfession(profession);
        dto.setSexe(sexe);
        dto.setStatutMatrimonial(statutMatrimonial);
        dto.setDateBapteme(dateBapteme);
        dto.setDateBaptemeEsprit(dateBaptemeEsprit);
        dto.setMotif(motif);
        return dto;
    }

    public String buildCleanSummaryText(String nomFidele, String pasteur, String eglise, LocalDate date, String rawOcrText) {
        StringBuilder sb = new StringBuilder();
        sb.append("📋 === SYNTHÈSE DES INFORMATIONS EXTRAITES ===\n\n");

        boolean anyExtracted = false;

        if (nomFidele != null && !nomFidele.isBlank()) {
            String[] parts = nomFidele.split("\\s+");
            if (parts.length >= 2) {
                sb.append("• Nom du fidèle     : ").append(parts[0].toUpperCase()).append("\n");
                StringBuilder prenoms = new StringBuilder();
                for (int i = 1; i < parts.length; i++) {
                    if (i > 1) prenoms.append(" ");
                    prenoms.append(parts[i]);
                }
                sb.append("• Prénom(s) du fidèle: ").append(prenoms).append("\n");
            } else {
                sb.append("• Nom & Prénom(s)   : ").append(nomFidele).append("\n");
            }
            anyExtracted = true;
        }

        if (eglise != null && !eglise.isBlank()) {
            sb.append("• Église / Temple   : ").append(eglise).append("\n");
            anyExtracted = true;
        }

        if (pasteur != null && !pasteur.isBlank()) {
            sb.append("• Pasteur Signataire: ").append(pasteur).append("\n");
            anyExtracted = true;
        }

        if (date != null) {
            sb.append("• Date recommandation: ").append(date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))).append("\n");
            anyExtracted = true;
        }

        String dateLieuNaiss = extractDateLieuNaissance(rawOcrText);
        if (dateLieuNaiss != null && !dateLieuNaiss.isBlank()) {
            sb.append("• Date & Lieu naiss.: ").append(dateLieuNaiss).append("\n");
            anyExtracted = true;
        }

        String prof = extractRegexMatch(rawOcrText, "(?i)(?:profession|m[ée]tier)\\s*:?\\s*([^\\r\\n•]+)");
        if (prof != null) {
            sb.append("• Profession        : ").append(prof).append("\n");
            anyExtracted = true;
        }

        String motif = extractMotif(rawOcrText);
        if (motif != null && !motif.isBlank()) {
            sb.append("• Motif             : ").append(motif).append("\n");
            anyExtracted = true;
        }

        if (!anyExtracted) {
            sb.append("Aucune information pertinente n'a été pré-détectée sur ce document.\n");
        }

        sb.append("\n=======================================================");
        return sb.toString();
    }

    private String extractDateLieuNaissance(String text) {
        if (text == null) return null;

        Pattern p1 = Pattern.compile("(?i)\\b(?:date\\s*(?:et|&)?\\s*lieu\\s*de\\s*naissance|date\\s*de\\s*naissance|n[ée\uFFFD]\\s*\\(e\\)\\s*le|\\bnaissance\\b)\\s*:?\\s*(\\d{1,2}[\\/\\-\\.]\\d{1,2}[\\/\\-\\.]\\d{4}(?:\\s*(?:[àa\uFFFD]|in)\\s*[^.\\r\\n•]+)?)");
        Matcher m1 = p1.matcher(text);
        if (m1.find()) {
            String res = m1.group(1).trim();
            if (res.contains("\n")) res = res.split("\n")[0].trim();
            if (res.contains("•")) res = res.split("•")[0].trim();
            return res.replaceAll("^[.:_\\s«»\"'…\\-]+|[.:_\\s«»\"'…\\-]+$", "");
        }

        return null;
    }

    private String extractMotif(String text) {
        if (text == null) return null;
        Pattern p = Pattern.compile("(?i)Motif(?:\\s*\\/\\s*[^:]+)?\\s*:\\s*([A-Za-zÀ-ÿ0-9\\s]+)");
        Matcher m = p.matcher(text);
        if (m.find()) {
            String val = m.group(1).trim();
            if (val.contains("\n")) val = val.split("\n")[0].trim();
            if (val.contains("-")) val = val.split("-")[0].trim();
            if (val.contains("•")) val = val.split("•")[0].trim();
            if (!val.isBlank()) return val;
        }
        if (text.contains("Transfert X") || text.contains("Transfert [X]") || text.toLowerCase().contains("transfert")) {
            return "Transfert";
        }
        if (text.contains("Voyage X") || text.contains("Voyage [X]") || text.toLowerCase().contains("voyage")) {
            return "Voyage";
        }
        if (text.contains("Emploi X") || text.contains("Emploi [X]") || text.toLowerCase().contains("emploi")) {
            return "Emploi";
        }
        if (text.toLowerCase().contains("baccalauréat") || text.toLowerCase().contains("baccalaureat") || text.toLowerCase().contains("université") || text.toLowerCase().contains("etudes")) {
            return "Études / Recommandation universitaire";
        }
        return null;
    }

    private String extractRegexMatch(String text, String regex) {
        if (text == null) return null;
        Pattern p = Pattern.compile(regex);
        Matcher m = p.matcher(text);
        if (m.find()) {
            String val = m.group(1).trim();
            if (val.contains("\n")) val = val.split("\n")[0].trim();
            if (val.contains("•")) val = val.split("•")[0].trim();
            val = val.replaceAll("^[.:_\\s«»\"]+|[.:_\\s«»\"]+$", "");
            return val.isBlank() ? null : val;
        }
        return null;
    }

    private String extractNomFidele(String text) {
        if (text == null || text.isBlank()) return null;

        String[] lines = text.split("[\\r\\n]+");

        Pattern triggerPattern = Pattern.compile("(?i)(?:atteste(?:|nt)\\s+que|certifie(?:|nt)\\s+que|recommande(?:|nt)|candidature\\s+de|sieur|dame|nomm[ée]\\s*\\(e\\)|nomm[ée]|fr[èe]re|s[œo]ur|M\\.|Mme|Mlle|porteur).*?([A-ZÀ-ÿ]{2,}\\s+[A-ZÀ-ÿa-zà-ÿ]+(?:\\s+[A-ZÀ-ÿa-zà-ÿ]+)*)");
        for (String line : lines) {
            Matcher m = triggerPattern.matcher(line);
            while (m.find()) {
                String rawName = m.group(1).trim();
                String cleaned = cleanNomCandidate(rawName);
                if (isValidName(cleaned)) {
                    return cleaned;
                }
            }
        }

        for (String line : lines) {
            String lower = line.toLowerCase();
            if (lower.contains("nomm") || lower.contains("atteste") || lower.contains("membre") || lower.contains("porteur") || lower.contains("rassure")) {
                Pattern p1 = Pattern.compile("([A-ZÀ-ÿ]{2,}\\s+[A-ZÀ-ÿa-zà-ÿ]+(?:\\s+[A-ZÀ-ÿa-zà-ÿ]+)*)");
                Matcher m1 = p1.matcher(line);
                while (m1.find()) {
                    String cand = cleanNomCandidate(m1.group(1).trim());
                    if (isValidName(cand)) {
                        return cand;
                    }
                }
            }
        }

        Pattern globalPattern = Pattern.compile("([A-ZÀ-ÿ]{2,}\\s+[A-ZÀ-ÿa-zà-ÿ]+(?:\\s+[A-ZÀ-ÿa-zà-ÿ]+)*)");
        Matcher globalMatcher = globalPattern.matcher(text);
        while (globalMatcher.find()) {
            String cand = cleanNomCandidate(globalMatcher.group(1).trim());
            if (isValidName(cand)) {
                return cand;
            }
        }

        return null;
    }

    private String cleanNomCandidate(String raw) {
        if (raw == null) return null;
        String val = raw;
        val = val.replaceAll("(?i)^(le|la|le\\s+ou\\s+la|ou|nomm[ée]\\s*\\(e\\)|nomm[ée]|est|membre|actif|du|de|des|d'|l')\\s+", "").trim();
        val = val.replaceAll("(?i)\\s+(est|était|etait|a|né|née|ne|demeurant|qui|membre|actif|du|de|le|la|à|pour|dans|régulièrement|regulierement|au|aux|en).*$", "");
        val = val.replaceAll("^[.:_\\s«»\"'…\\-]+|[.:_\\s«»\"'…\\-]+$", "").trim();
        return val;
    }

    private boolean isValidName(String cand) {
        if (cand == null || cand.isBlank()) return false;
        if (cand.length() < 3 || cand.length() > 60) return false;
        String normalized = java.text.Normalizer.normalize(cand, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .toUpperCase();
        if (normalized.equals("LE OU") || normalized.equals("LE OU LA") || normalized.equals("LA NOMMEE") || normalized.equals("LE NOMME")
                || normalized.contains("MINISTERE") || normalized.contains("CONSEIL") || normalized.contains("JEUNESSE") || normalized.contains("EVANGELISTE")
                || normalized.contains("MONITEUR") || normalized.contains("DIACRE") || normalized.contains("DIACONESSE") || normalized.contains("CHORISTE")
                || normalized.contains("ASSEMBLEES") || normalized.contains("DIEU") || normalized.contains("LETTRE") 
                || normalized.contains("RECOMMANDATION") || normalized.contains("TEMPLE") || normalized.contains("CHANGE PAS")
                || normalized.contains("OBJECT") || normalized.contains("MOTIF") || normalized.contains("EGLISE") || normalized.contains("BAPTISTE")
                || normalized.contains("REPUBLIQUE") || normalized.contains("TCHAD") || normalized.contains("TOGO") || normalized.contains("CONTACT")) {
            return false;
        }
        return cand.matches(".*[A-Za-zÀ-ÿ]{2,}.*");
    }

    private String extractPasteur(String text) {
        if (text == null || text.isBlank()) return null;

        Pattern p1 = Pattern.compile("(?i)(?:Je\\s+soussign[ée]\\s+)?Pasteur\\s+([A-ZÀ-ÿ]{2,}(?:\\s+[A-ZÀ-ÿ]{2,})*|[A-ZÀ-ÿ][a-zà-ÿ]+(?:\\s+[A-ZÀ-ÿ][a-zà-ÿ]+)*)");
        Matcher m1 = p1.matcher(text);
        String lastFound = null;
        while (m1.find()) {
            String rawP = m1.group(1).trim();
            if (rawP.contains("\n")) rawP = rawP.split("\n")[0].trim();
            String cleaned = cleanPasteurName(rawP);
            if (cleaned != null && !cleaned.isBlank()) {
                lastFound = "Pasteur " + cleaned;
                if (cleaned.contains(" ")) {
                    return lastFound;
                }
            }
        }
        if (lastFound != null) return lastFound;

        Pattern p2 = Pattern.compile("(?i)(?:pasteur|signataire|pstr|signé par)\\s*:?\\s*([A-Za-zÀ-ÿ\\-\\s]+)");
        Matcher m2 = p2.matcher(text);
        if (m2.find()) {
            String p = m2.group(1).trim();
            if (p.contains("\n")) p = p.split("\n")[0].trim();
            String cleaned = cleanPasteurName(p);
            if (cleaned != null) return "Pasteur " + cleaned;
        }
        return null;
    }

    private String cleanPasteurName(String name) {
        if (name == null) return null;
        String cleaned = name.replaceAll("(?i),?\\s+(ne|change|pas|dieu|temple|eglise|assemblees|du|de|des|le|la|•|pasteur).*$", "").trim();
        cleaned = cleaned.replaceAll("(?i)^(de|du|des|le|la|l')\\s+", "").trim();
        cleaned = cleaned.replaceAll("^[.:_\\s«»\"'…\\-,]+|[.:_\\s«»\"'…\\-,]+$", "").trim();
        if (cleaned.length() < 2 || cleaned.equalsIgnoreCase("pasteur")) return null;
        return cleaned;
    }

    private String extractEglise(String text) {
        if (text == null || text.isBlank()) return null;

        // 1. Priorité 1 : L'en-tête du document (10 premières lignes) qui contient l'église émettrice
        String[] lines = text.split("[\\r\\n]+");
        StringBuilder headerEglise = new StringBuilder();
        for (int i = 0; i < Math.min(12, lines.length); i++) {
            String line = lines[i].trim();
            String upper = line.toUpperCase();
            if (upper.contains("EGLISE") || upper.contains("ÉGLISE") || upper.contains("TEMPLE") || upper.contains("PAROISSE") || upper.contains("ASSEMBLEE")) {
                // Ne pas prendre la ligne de destination
                if (!upper.contains("DESTINATION") && !upper.contains("MOTIF")) {
                    String cleaned = cleanEgliseName(line);
                    if (cleaned != null && cleaned.length() >= 3) {
                        if (headerEglise.length() > 0) headerEglise.append(", ");
                        headerEglise.append(cleaned);
                    }
                }
            }
        }
        if (headerEglise.length() > 0) {
            return headerEglise.toString();
        }

        // 2. Pasteur de L'église / Temple / Paroisse <CHURCH_NAME>
        Pattern pPasteurEglise = Pattern.compile("(?i)Pasteur\\s+de\\s+(?:L['’]?\\s*)?([EÉeé]glise\\s+[^,\\n\\r]+|Temple\\s+[^,\\n\\r]+|Paroisse\\s+[^,\\n\\r]+)");
        Matcher mPasteurEglise = pPasteurEglise.matcher(text);
        if (mPasteurEglise.find()) {
            String cand = cleanEgliseName(mPasteurEglise.group(1));
            if (cand != null) return cand;
        }

        // 3. Fallback : Match "Église :" or "Temple :" or "Paroisse :"
        Pattern p1 = Pattern.compile("(?i)(?:[^\\s:]*glise|Temple|Paroisse)\\s*:\\s*[«\"]?([^»\"\\r\\n•]+)[»\"]?");
        Matcher m1 = p1.matcher(text);
        while (m1.find()) {
            String raw = m1.group(1).trim();
            if (!raw.toLowerCase().contains("destination")) {
                String cand = cleanEgliseName(raw);
                if (cand != null) return cand;
            }
        }

        return null;
    }

    private String cleanEgliseName(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String val = raw.replace('\uFFFD', 'é');
        if (val.contains("\n")) val = val.split("\n")[0].trim();
        if (val.contains("\r")) val = val.split("\r")[0].trim();
        if (val.contains("•")) val = val.split("•")[0].trim();
        val = val.replaceAll("(?i)\\s+(BP\\s*\\d+.*|Tél:?.*|Contact:?.*|Unité.*)$", "").trim();
        val = val.replaceAll("(?i)\\s+(•|pasteur|signataire|atteste|date|fait|objet|motif|nomm[ée]|tel|bp|lomé).*$", "");
        val = val.replaceAll("^[.:_\\s«»\"'…\\-]+|[.:_\\s«»\"'…\\-]+$", "").trim();
        if (val.length() < 3 || val.equalsIgnoreCase("EGLISE") || val.equalsIgnoreCase("TEMPLE")) {
            return null;
        }
        return val;
    }

    private LocalDate extractDateNaissance(String text) {
        if (text == null) return null;
        Pattern p = Pattern.compile("(?i)\\b(?:date\\s*(?:et|&)?\\s*lieu\\s*de\\s*naissance|date\\s*de\\s*naissance|n[ée\uFFFD]\\s*\\(e\\)\\s*le|\\bnaissance\\b)\\s*:?\\s*(\\d{1,2}[\\/\\-\\.]\\d{1,2}[\\/\\-\\.]\\d{4})");
        Matcher m = p.matcher(text);
        if (m.find()) {
            return parseDateString(m.group(1));
        }
        return null;
    }

    private String extractLieuNaissance(String text) {
        if (text == null) return null;
        Pattern p = Pattern.compile("(?i)\\b(?:date\\s*(?:et|&)?\\s*lieu\\s*de\\s*naissance|date\\s*de\\s*naissance|n[ée\uFFFD]\\s*\\(e\\)\\s*le|\\bnaissance\\b)\\s*:?\\s*\\d{1,2}[\\/\\-\\.]\\d{1,2}[\\/\\-\\.]\\d{4}\\s*(?:[àa\uFFFD]|in)\\s*([^.\\r\\n•]+)");
        Matcher m = p.matcher(text);
        if (m.find()) {
            String lieu = m.group(1).trim();
            if (lieu.contains("\n")) lieu = lieu.split("\n")[0].trim();
            lieu = lieu.replaceAll("^[.:_\\s«»\"'…\\-]+|[.:_\\s«»\"'…\\-]+$", "").trim();
            lieu = lieu.replace('\uFFFD', 'é');
            if (lieu.equalsIgnoreCase("Lom")) return "Lomé";
            return lieu;
        }
        return null;
    }

    private String extractSexe(String text) {
        if (text == null) return null;
        if (text.contains("Féminin Sexe : X") || text.contains("Féminin [X]") || text.contains("Féminin X") || text.contains("Sexe: Féminin") || (text.contains("Féminin") && text.contains("Sexe : X"))) {
            return "FEMININ";
        }
        if (text.contains("Masculin Sexe : X") || text.contains("Masculin [X]") || text.contains("Masculin X") || text.contains("Sexe: Masculin")) {
            return "MASCULIN";
        }
        return null;
    }

    private String extractStatutMatrimonial(String text) {
        if (text == null) return null;
        if (text.contains("Marié(e) X") || text.contains("Marié(e) [X]") || text.contains("Marié [X]") || text.contains("Marié(e) : X")) {
            return "MARIE";
        }
        if (text.contains("Célibataire X") || text.contains("Célibataire [X]") || (text.contains("Célibataire") && text.contains("Situation matrimoniale : X"))) {
            return "CELIBATAIRE";
        }
        if (text.contains("Divorcé(e) X") || text.contains("Divorcé(e) [X]")) {
            return "DIVORCE";
        }
        return null;
    }

    private LocalDate extractDateBaptemeEau(String text) {
        if (text == null) return null;
        Pattern p = Pattern.compile("(?i)(?:baptis[ée\uFFFD]\\s*d['’]eau\\s*le|bapt[êe\uFFFD]me\\s*d['’]eau)\\s*:?\\s*(\\d{1,2}[\\/\\-\\.]\\d{1,2}[\\/\\-\\.]\\d{4})");
        Matcher m = p.matcher(text);
        if (m.find()) {
            return parseDateString(m.group(1));
        }
        return null;
    }

    private LocalDate extractDateBaptemeEsprit(String text) {
        if (text == null) return null;
        Pattern p = Pattern.compile("(?i)(?:baptis[ée\uFFFD]\\s*du\\s*saint[\\-\\s]*esprit\\s*le|saint[\\-\\s]*esprit)\\s*:?\\s*(\\d{1,2}[\\/\\-\\.]\\d{1,2}[\\/\\-\\.]\\d{4})");
        Matcher m = p.matcher(text);
        if (m.find()) {
            return parseDateString(m.group(1));
        }
        return null;
    }

    private LocalDate parseDateString(String str) {
        if (str == null) return null;
        try {
            String[] parts = str.trim().split("[\\/\\-\\.]");
            if (parts.length == 3) {
                int day = Integer.parseInt(parts[0].trim());
                int month = Integer.parseInt(parts[1].trim());
                int year = Integer.parseInt(parts[2].trim());
                if (day >= 1 && day <= 31 && month >= 1 && month <= 12 && year >= 1900 && year <= 2099) {
                    return LocalDate.of(year, month, day);
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private LocalDate extractDate(String text) {
        if (text == null) return LocalDate.now();

        Pattern patternFait = Pattern.compile("(?i)(?:fait\\s*(?:[àa]\\s*[^,\\r\\n]+)?,?\\s*le|le)\\s*(\\d{1,2}\\s+[a-zA-ZÀ-ÿûéè]+\\s+\\d{4}|\\d{1,2}[\\/\\-\\.]\\d{1,2}[\\/\\-\\.]\\d{4})");
        Matcher matcherFait = patternFait.matcher(text);
        while (matcherFait.find()) {
            LocalDate d = parseDateStringOrFrench(matcherFait.group(1));
            if (d != null) return d;
        }

        Pattern datePattern = Pattern.compile("(\\d{1,2})[\\/\\-\\s](\\d{1,2}|[a-zA-ZÀ-ÿûéè]+)[\\/\\-\\s](\\d{4})");
        Matcher dateMatcher = datePattern.matcher(text);
        LocalDate lastDate = null;
        while (dateMatcher.find()) {
            String dayStr = dateMatcher.group(1);
            String monthStr = dateMatcher.group(2);
            String yearStr = dateMatcher.group(3);

            try {
                int day = Integer.parseInt(dayStr);
                int year = Integer.parseInt(yearStr);
                int month = monthStr.matches("\\d+") ? Integer.parseInt(monthStr) : convertFrenchMonth(monthStr);
                if (day >= 1 && day <= 31 && month >= 1 && month <= 12 && year >= 1900 && year <= 2099) {
                    lastDate = LocalDate.of(year, month, day);
                }
            } catch (Exception ignored) {}
        }
        return lastDate != null ? lastDate : LocalDate.now();
    }

    private LocalDate parseDateStringOrFrench(String str) {
        if (str == null || str.isBlank()) return null;
        String clean = str.trim();
        if (clean.matches("\\d{1,2}[\\/\\-\\.]\\d{1,2}[\\/\\-\\.]\\d{4}")) {
            return parseDateString(clean);
        }
        String[] parts = clean.split("\\s+");
        if (parts.length == 3) {
            try {
                int day = Integer.parseInt(parts[0]);
                int month = convertFrenchMonth(parts[1]);
                int year = Integer.parseInt(parts[2]);
                if (day >= 1 && day <= 31 && month >= 1 && month <= 12 && year >= 1900 && year <= 2099) {
                    return LocalDate.of(year, month, day);
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    private int convertFrenchMonth(String monthStr) {
        String lower = monthStr.toLowerCase();
        if (lower.contains("jan")) return 1;
        if (lower.contains("fév") || lower.contains("fev")) return 2;
        if (lower.contains("mar")) return 3;
        if (lower.contains("avr")) return 4;
        if (lower.contains("mai")) return 5;
        if (lower.contains("jun") || lower.contains("jui")) {
            if (lower.contains("juil")) return 7;
            return 6;
        }
        if (lower.contains("aoû") || lower.contains("aou")) return 8;
        if (lower.contains("sep")) return 9;
        if (lower.contains("oct")) return 10;
        if (lower.contains("nov")) return 11;
        if (lower.contains("déc") || lower.contains("dec")) return 12;
        return 1;
    }
}
