package com.eglise.secretariat.fidele;

import com.eglise.model.AdresseCoordonnees;
import com.eglise.model.Fidele;
import com.eglise.model.Filiation;
import com.eglise.model.LettreRecommandationEntrante;
import com.eglise.model.ParcoursSpirituel;
import com.eglise.model.SituationMatrimoniale;
import com.eglise.model.StatutFidele;
import com.eglise.model.enumeration.FrequenceDime;
import com.eglise.model.enumeration.Sexe;
import com.eglise.model.enumeration.Statut;
import com.eglise.secretariat.fidele.dto.FideleDto;
import com.eglise.secretariat.shared.exception.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class FideleService {

    private final FideleRepository fideleRepository;

    public FideleService(FideleRepository fideleRepository) {
        this.fideleRepository = fideleRepository;
    }

    public Page<FideleDto> searchFideles(String query, String quartier, Boolean baptise, Boolean actif, Pageable pageable) {
        Specification<Fidele> spec = FideleSpecification.filterFideles(query, quartier, baptise, actif);
        return fideleRepository.findAll(spec, pageable).map(this::mapToDto);
    }

    public FideleDto getFideleById(Long id) {
        Fidele fidele = fideleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Fidèle non trouvé avec l'ID : " + id));
        return mapToDto(fidele);
    }

    @Transactional
    public FideleDto createFidele(FideleDto dto) {
        Fidele fidele = mapToEntity(dto);
        Fidele savedFidele = fideleRepository.save(fidele);
        return mapToDto(savedFidele);
    }

    @Transactional
    public FideleDto updateFidele(Long id, FideleDto dto) {
        Fidele fidele = fideleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Fidèle non trouvé avec l'ID : " + id));

        updateFideleFromDto(fidele, dto);
        Fidele updatedFidele = fideleRepository.save(fidele);
        return mapToDto(updatedFidele);
    }

    @Transactional
    public void deleteFidele(Long id) {
        if (!fideleRepository.existsById(id)) {
            throw new ResourceNotFoundException("Fidèle non trouvé avec l'ID : " + id);
        }
        fideleRepository.deleteById(id);
    }

    private void updateFideleFromDto(Fidele fidele, FideleDto dto) {
        fidele.setNom(dto.getNom());
        fidele.setPrenoms(dto.getPrenoms());
        fidele.setSexe(dto.getSexe());
        fidele.setDateNaissance(dto.getDateNaissance());
        fidele.setLieuNaissance(dto.getLieuNaissance());
        fidele.setEthnie(dto.getEthnie());
        fidele.setProfession(dto.getProfession());
        fidele.setNiveauEtude(dto.getNiveauEtude());

        // Adresse
        AdresseCoordonnees adresse = fidele.getAdresse() != null ? fidele.getAdresse() : new AdresseCoordonnees();
        adresse.setContactTogocel(dto.getTelephone());
        adresse.setContactMoov(dto.getContactMoov());
        adresse.setEmail(dto.getEmail());
        adresse.setDomicile(dto.getQuartier());
        adresse.setMaison(dto.getAdresse());
        adresse.setPrefectureRegion(dto.getPrefectureRegion());
        fidele.setAdresse(adresse);

        // Filiation
        Filiation filiation = fidele.getFiliation() != null ? fidele.getFiliation() : new Filiation();
        filiation.setNomPere(dto.getNomPere());
        filiation.setPrenomPere(dto.getPrenomPere());
        filiation.setNomMere(dto.getNomMere());
        filiation.setPrenomMere(dto.getPrenomMere());
        fidele.setFiliation(filiation);

        // Situation Matrimoniale & Conjoint
        SituationMatrimoniale mat = fidele.getSituationMatrimoniale() != null ? fidele.getSituationMatrimoniale() : new SituationMatrimoniale();
        mat.setStatutActuel(dto.getStatutMatrimonial());
        mat.setDateMariageEglise(dto.getDateMariage());
        mat.setEgliseMariage(dto.getEgliseMariage());
        mat.setPasteurCelebrant(dto.getPasteurMariage());
        mat.setNomConjointActuel(dto.getNomConjoint());
        mat.setConfessionFoiConjoint(dto.getConfessionFoiConjoint());
        if (dto.getNombreGarcons() != null) mat.setNombreGarçons(dto.getNombreGarcons());
        if (dto.getNombreFilles() != null) mat.setNombreFilles(dto.getNombreFilles());
        fidele.setSituationMatrimoniale(mat);

        // Parcours Spirituel
        ParcoursSpirituel parcours = fidele.getParcoursSpirituel() != null ? fidele.getParcoursSpirituel() : new ParcoursSpirituel();
        parcours.setDateConversion(dto.getDateConversion());
        parcours.setEgliseConversion(dto.getEgliseConversion());
        parcours.setDateBaptemeEau(dto.getDateBapteme());
        parcours.setEgliseBaptemeEau(dto.getLieuBapteme());
        parcours.setPasteurBaptemeEau(dto.getPasteurBapteme());
        parcours.setDateBaptemeSaintEsprit(dto.getDateBaptemeEsprit());
        parcours.setLieuBaptemeSaintEsprit(dto.getLieuBaptemeEsprit());
        parcours.setAncienneDenomination(dto.getAncienneDenomination());
        parcours.setNouvelleDenomination(dto.getNouvelleDenomination());
        fidele.setParcoursSpirituel(parcours);

        // Lettre entrante
        if (Boolean.TRUE.equals(dto.getLettreRecommandationPresentee())) {
            LettreRecommandationEntrante lettre = fidele.getLettreEntrante() != null ? fidele.getLettreEntrante() : new LettreRecommandationEntrante();
            lettre.setDatePresentation(dto.getDateLettreRecommandation());
            lettre.setNomPasteurSignataire(dto.getPasteurLettreRecommandation());
            lettre.setEgliseOrigine(dto.getEgliseLettreRecommandation());
            fidele.setLettreEntrante(lettre);
        }

        // Statut & Dîmes
        StatutFidele statut = fidele.getStatutFidele() != null ? fidele.getStatutFidele() : new StatutFidele();
        if (dto.getCarteMembreValide() != null) statut.setCarteMembreValide(dto.getCarteMembreValide());
        else statut.setCarteMembreValide(dto.isActif());

        if (dto.getRegulierReunions() != null) statut.setEstRegulierReunions(dto.getRegulierReunions());
        if (dto.getCarnetDimeValide() != null) statut.setCarnetDimeValide(dto.getCarnetDimeValide());
        if (dto.getPayeDimes() != null) statut.setPayeDime(dto.getPayeDimes());
        if (dto.getDateIntegrationAdidogome() != null) statut.setEstMembreActifDepuis(dto.getDateIntegrationAdidogome());
        statut.setFrequenceDime(dto.getFrequenceDime());
        fidele.setStatutFidele(statut);
    }

    private FideleDto mapToDto(Fidele fidele) {
        FideleDto dto = new FideleDto();
        dto.setId(fidele.getId());
        dto.setNom(fidele.getNom());
        dto.setPrenoms(fidele.getPrenoms());
        dto.setSexe(fidele.getSexe());
        dto.setDateNaissance(fidele.getDateNaissance());
        dto.setLieuNaissance(fidele.getLieuNaissance());
        dto.setEthnie(fidele.getEthnie());
        dto.setProfession(fidele.getProfession());
        dto.setNiveauEtude(fidele.getNiveauEtude());

        if (fidele.getAdresse() != null) {
            dto.setTelephone(fidele.getAdresse().getContactTogocel());
            dto.setContactMoov(fidele.getAdresse().getContactMoov());
            dto.setEmail(fidele.getAdresse().getEmail());
            dto.setQuartier(fidele.getAdresse().getDomicile());
            dto.setAdresse(fidele.getAdresse().getMaison());
            dto.setPrefectureRegion(fidele.getAdresse().getPrefectureRegion());
        }

        if (fidele.getFiliation() != null) {
            dto.setNomPere(fidele.getFiliation().getNomPere());
            dto.setPrenomPere(fidele.getFiliation().getPrenomPere());
            dto.setNomMere(fidele.getFiliation().getNomMere());
            dto.setPrenomMere(fidele.getFiliation().getPrenomMere());
        }

        if (fidele.getSituationMatrimoniale() != null) {
            dto.setStatutMatrimonial(fidele.getSituationMatrimoniale().getStatutActuel());
            dto.setDateMariage(fidele.getSituationMatrimoniale().getDateMariageEglise());
            dto.setEgliseMariage(fidele.getSituationMatrimoniale().getEgliseMariage());
            dto.setPasteurMariage(fidele.getSituationMatrimoniale().getPasteurCelebrant());
            dto.setNomConjoint(fidele.getSituationMatrimoniale().getNomConjointActuel());
            dto.setConfessionFoiConjoint(fidele.getSituationMatrimoniale().getConfessionFoiConjoint());
            dto.setNombreGarcons(fidele.getSituationMatrimoniale().getNombreGarçons());
            dto.setNombreFilles(fidele.getSituationMatrimoniale().getNombreFilles());
        }

        if (fidele.getParcoursSpirituel() != null) {
            dto.setDateConversion(fidele.getParcoursSpirituel().getDateConversion());
            dto.setEgliseConversion(fidele.getParcoursSpirituel().getEgliseConversion());
            dto.setBaptise(fidele.getParcoursSpirituel().getDateBaptemeEau() != null);
            dto.setDateBapteme(fidele.getParcoursSpirituel().getDateBaptemeEau());
            dto.setLieuBapteme(fidele.getParcoursSpirituel().getEgliseBaptemeEau());
            dto.setPasteurBapteme(fidele.getParcoursSpirituel().getPasteurBaptemeEau());
            dto.setDateBaptemeEsprit(fidele.getParcoursSpirituel().getDateBaptemeSaintEsprit());
            dto.setLieuBaptemeEsprit(fidele.getParcoursSpirituel().getLieuBaptemeSaintEsprit());
            dto.setAncienneDenomination(fidele.getParcoursSpirituel().getAncienneDenomination());
            dto.setNouvelleDenomination(fidele.getParcoursSpirituel().getNouvelleDenomination());
        }

        if (fidele.getLettreEntrante() != null) {
            dto.setLettreRecommandationPresentee(true);
            dto.setDateLettreRecommandation(fidele.getLettreEntrante().getDatePresentation());
            dto.setPasteurLettreRecommandation(fidele.getLettreEntrante().getNomPasteurSignataire());
            dto.setEgliseLettreRecommandation(fidele.getLettreEntrante().getEgliseOrigine());
        }

        if (fidele.getStatutFidele() != null) {
            dto.setCarteMembreValide(fidele.getStatutFidele().getCarteMembreValide());
            dto.setRegulierReunions(fidele.getStatutFidele().getEstRegulierReunions());
            dto.setCarnetDimeValide(fidele.getStatutFidele().getCarnetDimeValide());
            dto.setPayeDimes(fidele.getStatutFidele().getPayeDime());
            dto.setDateIntegrationAdidogome(fidele.getStatutFidele().getEstMembreActifDepuis());
            dto.setFrequenceDime(fidele.getStatutFidele().getFrequenceDime());
            dto.setActif(Boolean.TRUE.equals(fidele.getStatutFidele().getCarteMembreValide()));
        }

        return dto;
    }

    private Fidele mapToEntity(FideleDto dto) {
        Fidele fidele = new Fidele();
        updateFideleFromDto(fidele, dto);
        return fidele;
    }
}
