// TestCase.java
package com.testparser.models;

import java.util.List;

public class TestCase {
    private String testName;
    private String className;
    private String description;
    private List<TestStep> steps;
    
    public TestCase() {}
    
    public TestCase(String testName, String className, String description, List<TestStep> steps) {
        this.testName = testName;
        this.className = className;
        this.description = description;
        this.steps = steps;
    }
    
    // Getters and setters
    public String getTestName() { return testName; }
    public void setTestName(String testName) { this.testName = testName; }
    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public List<TestStep> getSteps() { return steps; }
    public void setSteps(List<TestStep> steps) { this.steps = steps; }
}
