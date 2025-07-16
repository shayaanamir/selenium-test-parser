package com.testparser.extractors;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.stmt.Statement;
import com.testparser.models.PageObject;
import com.testparser.models.TestCase;
import com.testparser.models.TestStep;
import com.testparser.utils.ConfigPropertiesReader;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Handles extraction of test case details from method declarations
 */
public class TestCaseExtractor {
    
    private final TestStepExtractor stepExtractor;
    
    public TestCaseExtractor() {
        this.stepExtractor = new TestStepExtractor();
    }
    
    /**
     * Extract test case details from a method
     */
    public TestCase extractTestCase(MethodDeclaration method, String className, 
                                   Map<String, PageObject> pageObjects, Map<String, String> configUrls) {
        String testName = method.getNameAsString();
        String description = extractDescription(method);
        List<TestStep> steps = extractSteps(method, pageObjects);
        
        // Extract URL from config based on test method name
        String testUrl = ConfigPropertiesReader.findMatchingUrl(testName, configUrls);
        
        return new TestCase(testName, className, description, steps, testUrl);
    }
    
    /**
     * Extract description from Javadoc, comments, or method name
     */
    private String extractDescription(MethodDeclaration method) {
        // Try Javadoc first
        String javadocDesc = method.getJavadoc()
                .map(javadoc -> javadoc.getDescription().toText())
                .orElse(null);
        
        if (javadocDesc != null && !javadocDesc.trim().isEmpty()) {
            return javadocDesc;
        }
        
        // Try comments above method
        String commentDesc = extractCommentDescription(method);
        if (commentDesc != null && !commentDesc.trim().isEmpty()) {
            return commentDesc;
        }
        
        // Fallback to method name
        return method.getNameAsString();
    }
    
    /**
     * Extract description from single-line comments
     */
    private String extractCommentDescription(MethodDeclaration method) {
        String methodSource = method.toString();
        String[] lines = methodSource.split("\n");
        
        // Look for comments mentioning "test case"
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.startsWith("//") && line.contains("test case")) {
                return line.substring(2).trim();
            }
        }
        
        return null;
    }
    
    /**
     * Extract test steps from method body
     */
    private List<TestStep> extractSteps(MethodDeclaration method, Map<String, PageObject> pageObjects) {
        List<TestStep> steps = new ArrayList<>();
        
        method.getBody().ifPresent(body -> {
            int stepNumber = 1;
            // Process each statement in the method
            for (Statement stmt : body.getStatements()) {
                List<TestStep> stepsFromStatement = stepExtractor.analyzeStatement(stmt, pageObjects, stepNumber);
                steps.addAll(stepsFromStatement);
                stepNumber += stepsFromStatement.size();
            }
        });
        
        return steps;
    }
}