package com.teamopensourcesmartglasses.factchecker.events;

public class FactCheckedEvent {
    private final String fact;
    private final String validity;
    private final String explanation;

    public FactCheckedEvent(String aiFact, String aiValidity, String aiExplanation) {
        fact = aiFact;
        validity = aiValidity;
        explanation = aiExplanation;
    }

    public String getFact() {
        return fact;
    }

    public String getValidity(){
        return validity;
    }

    public String getExplanation() {
        return explanation;
    }
}
