package com.eglise.secretariat.mouvement.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FideleRecommendationDto {

    @JsonProperty("nomFidele")
    private String nomFidele;

    @JsonProperty("prenomFidele")
    private String prenomFidele;

    @JsonProperty("sexe")
    private String sexe; // MASCULIN ou FEMININ

    @JsonProperty("dateNaissance")
    private String dateNaissance;

    @JsonProperty("lieuNaissance")
    private String lieuNaissance;

    @JsonProperty("profession")
    private String profession;

    @JsonProperty("statutMatrimonial")
    private String statutMatrimonial; // CELIBATAIRE, MARIE, DIVORCE, VEUF

    @JsonProperty("dateBaptemeEau")
    private String dateBaptemeEau;

    @JsonProperty("dateBaptemeEsprit")
    private String dateBaptemeEsprit;

    @JsonProperty("egliseOrigine")
    private String egliseOrigine;

    @JsonProperty("nomPasteurRecommandateur")
    private String nomPasteurRecommandateur;

    @JsonProperty("egliseDestination")
    private String egliseDestination;

    @JsonProperty("motifTransfert")
    private String motifTransfert;

    @JsonProperty("remarquesOuQualites")
    private String remarquesOuQualites;

    public FideleRecommendationDto() {}

    // Getters et Setters
    public String getNomFidele() { return nomFidele; }
    public void setNomFidele(String nomFidele) { this.nomFidele = nomFidele; }

    public String getPrenomFidele() { return prenomFidele; }
    public void setPrenomFidele(String prenomFidele) { this.prenomFidele = prenomFidele; }

    public String getSexe() { return sexe; }
    public void setSexe(String sexe) { this.sexe = sexe; }

    public String getDateNaissance() { return dateNaissance; }
    public void setDateNaissance(String dateNaissance) { this.dateNaissance = dateNaissance; }

    public String getLieuNaissance() { return lieuNaissance; }
    public void setLieuNaissance(String lieuNaissance) { this.lieuNaissance = lieuNaissance; }

    public String getProfession() { return profession; }
    public void setProfession(String profession) { this.profession = profession; }

    public String getStatutMatrimonial() { return statutMatrimonial; }
    public void setStatutMatrimonial(String statutMatrimonial) { this.statutMatrimonial = statutMatrimonial; }

    public String getDateBaptemeEau() { return dateBaptemeEau; }
    public void setDateBaptemeEau(String dateBaptemeEau) { this.dateBaptemeEau = dateBaptemeEau; }

    public String getDateBaptemeEsprit() { return dateBaptemeEsprit; }
    public void setDateBaptemeEsprit(String dateBaptemeEsprit) { this.dateBaptemeEsprit = dateBaptemeEsprit; }

    public String getEgliseOrigine() { return egliseOrigine; }
    public void setEgliseOrigine(String egliseOrigine) { this.egliseOrigine = egliseOrigine; }

    public String getNomPasteurRecommandateur() { return nomPasteurRecommandateur; }
    public void setNomPasteurRecommandateur(String nomPasteurRecommandateur) { this.nomPasteurRecommandateur = nomPasteurRecommandateur; }

    public String getEgliseDestination() { return egliseDestination; }
    public void setEgliseDestination(String egliseDestination) { this.egliseDestination = egliseDestination; }

    public String getMotifTransfert() { return motifTransfert; }
    public void setMotifTransfert(String motifTransfert) { this.motifTransfert = motifTransfert; }

    public String getRemarquesOuQualites() { return remarquesOuQualites; }
    public void setRemarquesOuQualites(String remarquesOuQualites) { this.remarquesOuQualites = remarquesOuQualites; }
}
