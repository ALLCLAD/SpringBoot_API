package com.eglise.secretariat.mouvement.dto;

import java.time.LocalDate;

public class OcrResultDto {
    private String nomFidele;
    private String pasteurSignataire;
    private String egliseOrigine;
    private LocalDate datePresentation;
    private LocalDate dateNaissance;
    private String lieuNaissance;
    private String profession;
    private String sexe;
    private String statutMatrimonial;
    private LocalDate dateBapteme;
    private LocalDate dateBaptemeEsprit;
    private String motif;
    private String rawText;

    public OcrResultDto() {}

    public OcrResultDto(String nomFidele, String pasteurSignataire, String egliseOrigine, LocalDate datePresentation, String rawText) {
        this.nomFidele = nomFidele;
        this.pasteurSignataire = pasteurSignataire;
        this.egliseOrigine = egliseOrigine;
        this.datePresentation = datePresentation;
        this.rawText = rawText;
    }

    public String getNomFidele() {
        return nomFidele;
    }

    public void setNomFidele(String nomFidele) {
        this.nomFidele = nomFidele;
    }

    public String getPasteurSignataire() {
        return pasteurSignataire;
    }

    public void setPasteurSignataire(String pasteurSignataire) {
        this.pasteurSignataire = pasteurSignataire;
    }

    public String getEgliseOrigine() {
        return egliseOrigine;
    }

    public void setEgliseOrigine(String egliseOrigine) {
        this.egliseOrigine = egliseOrigine;
    }

    public LocalDate getDatePresentation() {
        return datePresentation;
    }

    public void setDatePresentation(LocalDate datePresentation) {
        this.datePresentation = datePresentation;
    }

    public LocalDate getDateNaissance() {
        return dateNaissance;
    }

    public void setDateNaissance(LocalDate dateNaissance) {
        this.dateNaissance = dateNaissance;
    }

    public String getLieuNaissance() {
        return lieuNaissance;
    }

    public void setLieuNaissance(String lieuNaissance) {
        this.lieuNaissance = lieuNaissance;
    }

    public String getProfession() {
        return profession;
    }

    public void setProfession(String profession) {
        this.profession = profession;
    }

    public String getSexe() {
        return sexe;
    }

    public void setSexe(String sexe) {
        this.sexe = sexe;
    }

    public String getStatutMatrimonial() {
        return statutMatrimonial;
    }

    public void setStatutMatrimonial(String statutMatrimonial) {
        this.statutMatrimonial = statutMatrimonial;
    }

    public LocalDate getDateBapteme() {
        return dateBapteme;
    }

    public void setDateBapteme(LocalDate dateBapteme) {
        this.dateBapteme = dateBapteme;
    }

    public LocalDate getDateBaptemeEsprit() {
        return dateBaptemeEsprit;
    }

    public void setDateBaptemeEsprit(LocalDate dateBaptemeEsprit) {
        this.dateBaptemeEsprit = dateBaptemeEsprit;
    }

    public String getMotif() {
        return motif;
    }

    public void setMotif(String motif) {
        this.motif = motif;
    }

    public String getRawText() {
        return rawText;
    }

    public void setRawText(String rawText) {
        this.rawText = rawText;
    }
}
