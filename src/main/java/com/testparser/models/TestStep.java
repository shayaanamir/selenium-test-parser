package com.testparser.models;

public class TestStep {
    private int stepNumber;
    private String stepDescription;
    private String actionType;
    private String elementSelector;
    private String value;
    
    public TestStep() {}
    
    public TestStep(int stepNumber, String stepDescription, String actionType, String elementSelector, String value) {
        this.stepNumber = stepNumber;
        this.stepDescription = stepDescription;
        this.actionType = actionType;
        this.elementSelector = elementSelector;
        this.value = value;
    }
    
    // Getters and setters
    public int getStepNumber() { return stepNumber; }
    public void setStepNumber(int stepNumber) { this.stepNumber = stepNumber; }
    public String getStepDescription() { return stepDescription; }
    public void setStepDescription(String stepDescription) { this.stepDescription = stepDescription; }
    public String getActionType() { return actionType; }
    public void setActionType(String actionType) { this.actionType = actionType; }
    public String getElementSelector() { return elementSelector; }
    public void setElementSelector(String elementSelector) { this.elementSelector = elementSelector; }
    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
}