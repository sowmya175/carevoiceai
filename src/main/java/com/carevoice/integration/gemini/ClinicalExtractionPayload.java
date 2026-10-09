package com.carevoice.integration.gemini;
import com.carevoice.service.ExtractedClinicalFacts;

/**
 * Provider-neutral structured extraction result.
 * Null fields mean the patient did not address that item.
 */
public class ClinicalExtractionPayload {
    private Integer painScore;
    private Boolean dizziness;
    private String dizzinessOnset;
    private Boolean lossOfConsciousness;
    private Boolean medicationTaken;
    private String appetite;
    private String sleepQuality;
    private Boolean shortnessOfBreath;
    private Double temperature;

    public Integer getPainScore() { return painScore; }
    public void setPainScore(Integer painScore) { this.painScore = painScore; }
    public Boolean getDizziness() { return dizziness; }
    public void setDizziness(Boolean dizziness) { this.dizziness = dizziness; }
    public String getDizzinessOnset() { return dizzinessOnset; }
    public void setDizzinessOnset(String dizzinessOnset) { this.dizzinessOnset = dizzinessOnset; }
    public Boolean getLossOfConsciousness() { return lossOfConsciousness; }
    public void setLossOfConsciousness(Boolean lossOfConsciousness) { this.lossOfConsciousness = lossOfConsciousness; }
    public Boolean getMedicationTaken() { return medicationTaken; }
    public void setMedicationTaken(Boolean medicationTaken) { this.medicationTaken = medicationTaken; }
    public String getAppetite() { return appetite; }
    public void setAppetite(String appetite) { this.appetite = appetite; }
    public String getSleepQuality() { return sleepQuality; }
    public void setSleepQuality(String sleepQuality) { this.sleepQuality = sleepQuality; }
    public Boolean getShortnessOfBreath() { return shortnessOfBreath; }
    public void setShortnessOfBreath(Boolean shortnessOfBreath) { this.shortnessOfBreath = shortnessOfBreath; }
    public Double getTemperature() { return temperature; }
    public void setTemperature(Double temperature) { this.temperature = temperature; }

    public ExtractedClinicalFacts toFacts() {
        return new ExtractedClinicalFacts(
                painScore,
                dizziness,
                dizzinessOnset,
                lossOfConsciousness,
                medicationTaken,
                appetite,
                sleepQuality,
                shortnessOfBreath,
                temperature
        );
    }
}
