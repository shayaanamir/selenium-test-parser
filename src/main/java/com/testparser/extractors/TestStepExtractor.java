package com.testparser.extractors;

import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.Statement;
import com.testparser.models.PageObject;
import com.testparser.models.TestStep;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Enhanced TestStepExtractor with better assertion handling
 */
public class TestStepExtractor {
    
    private static final Map<String, String> ACTION_PATTERNS = Map.of(
        "click", "click|Click",
        "type", "sendKeys|type|setText|enterText|enter",
        "select", "select|Select",
        "wait", "wait|Wait|until",
        "assert", "assert|Assert",
        "verify", "verify|Verify|expect|should|check",
        "navigate", "get|navigate|goTo",
        "drag", "drag|dragAndDrop"
    );
    
    private final ElementSelectorFinder selectorFinder;
    
    public TestStepExtractor() {
        this.selectorFinder = new ElementSelectorFinder();
    }
    
    public List<TestStep> analyzeStatement(Statement stmt, Map<String, PageObject> pageObjects, int startingStepNumber) {
        List<TestStep> steps = new ArrayList<>();
        List<MethodCallExpr> methodCalls = stmt.findAll(MethodCallExpr.class);
        
        int currentStepNumber = startingStepNumber;
        for (MethodCallExpr call : methodCalls) {
            String methodName = call.getNameAsString();
            String actionType = determineActionType(methodName);
            
            if (actionType != null) {
                String elementSelector = null;
                String value = null;
                
                if (isAssertionAction(actionType)) {
                    // Enhanced assertion handling
                    AssertionInfo assertionInfo = analyzeAssertionCall(call, pageObjects);
                    elementSelector = assertionInfo.elementSelector;
                    value = assertionInfo.expectedValue;
                } else {
                    // Regular action handling
                    elementSelector = selectorFinder.findElementSelector(call, pageObjects);
                    value = extractValue(call);
                }
                
                String description = generateStepDescription(actionType, elementSelector, value, methodName);
                steps.add(new TestStep(currentStepNumber, description, actionType, elementSelector, value));
                currentStepNumber++;
            }
        }
        
        return steps;
    }
    
    private boolean isAssertionAction(String actionType) {
        return "assert".equals(actionType) || "verify".equals(actionType);
    }
    
    /**
     * Analyze assertion method calls to extract element and expected value
     */
    private AssertionInfo analyzeAssertionCall(MethodCallExpr call, Map<String, PageObject> pageObjects) {
        String methodName = call.getNameAsString();
        String elementSelector = null;
        String expectedValue = null;
        
        // Try to find element selector first
        elementSelector = selectorFinder.findElementSelector(call, pageObjects);
        
        // If no direct element found, try to extract from method name
        if (elementSelector == null) {
            elementSelector = extractElementFromAssertionMethod(methodName, pageObjects);
        }
        
        // Extract expected value from arguments
        if (call.getArguments().size() > 0) {
            expectedValue = extractAssertionValue(call);
        } else {
            // For boolean assertions like isProductClicked(), the method name itself is the condition
            expectedValue = methodName;
        }
        
        return new AssertionInfo(elementSelector, expectedValue);
    }
    
    /**
     * Extract element information from assertion method names
     */
    private String extractElementFromAssertionMethod(String methodName, Map<String, PageObject> pageObjects) {
        // Remove assertion prefixes and boolean prefixes
        String elementName = methodName.toLowerCase()
            .replaceFirst("^(assert|verify|check|expect|should|is|has|get)", "")
            .replaceFirst("^(is|has|get)", "");
        
        if (elementName.isEmpty()) {
            return null;
        }
        
        // Search through page objects for matching elements
        for (PageObject pageObject : pageObjects.values()) {
            for (Map.Entry<String, String> element : pageObject.getElements().entrySet()) {
                String elementKey = element.getKey().toLowerCase();
                
                if (elementKey.contains(elementName) || elementName.contains(elementKey)) {
                    return element.getValue();
                }
            }
        }
        
        return null;
    }
    
    /**
     * Extract expected value from assertion method arguments
     */
    private String extractAssertionValue(MethodCallExpr call) {
        if (call.getArguments().size() == 0) {
            return null;
        }
        
        // For assertions, we might have multiple arguments (actual, expected)
        // Common patterns:
        // assertEquals(actual, expected)
        // assertTrue(condition)
        // assertThat(actual, matcher)
        
        String methodName = call.getNameAsString().toLowerCase();
        
        if (methodName.contains("true") || methodName.contains("false")) {
            // Boolean assertions - return the condition
            return call.getArguments().get(0).toString();
        } else if (methodName.contains("equals") && call.getArguments().size() >= 2) {
            // Equality assertions - return expected value (usually second argument)
            String expectedArg = call.getArguments().get(1).toString();
            return cleanArgumentValue(expectedArg);
        } else {
            // Default to first argument
            String firstArg = call.getArguments().get(0).toString();
            return cleanArgumentValue(firstArg);
        }
    }
    
    /**
     * Clean argument value by removing quotes and unnecessary characters
     */
    private String cleanArgumentValue(String arg) {
        if (arg.startsWith("\"") && arg.endsWith("\"")) {
            return arg.substring(1, arg.length() - 1);
        }
        return arg;
    }
    
    private String determineActionType(String methodName) {
        for (Map.Entry<String, String> entry : ACTION_PATTERNS.entrySet()) {
            Pattern pattern = Pattern.compile(entry.getValue(), Pattern.CASE_INSENSITIVE);
            if (pattern.matcher(methodName).find()) {
                return entry.getKey();
            }
        }
        return null;
    }
    
    private String extractValue(MethodCallExpr call) {
        if (call.getArguments().size() > 0) {
            String arg = call.getArguments().get(0).toString();
            return cleanArgumentValue(arg);
        }
        return null;
    }
    
    /**
     * Generate improved human-readable step description
     */
    private String generateStepDescription(String actionType, String elementSelector, String value, String methodName) {
        StringBuilder desc = new StringBuilder();
        
        if (isAssertionAction(actionType)) {
            // Enhanced assertion descriptions
            if (methodName.toLowerCase().contains("true")) {
                desc.append("Assert that ").append(value != null ? value : "condition").append(" is true");
            } else if (methodName.toLowerCase().contains("false")) {
                desc.append("Assert that ").append(value != null ? value : "condition").append(" is false");
            } else if (methodName.toLowerCase().contains("equals")) {
                desc.append("Assert that ");
                if (elementSelector != null) {
                    desc.append("element ").append(elementSelector);
                } else {
                    desc.append("value");
                }
                desc.append(" equals ").append(value != null ? value : "expected value");
            } else {
                desc.append("Verify ");
                if (elementSelector != null) {
                    desc.append("element ").append(elementSelector);
                }
                if (value != null) {
                    desc.append(" with condition: ").append(value);
                }
            }
        } else {
            // Regular action descriptions
            desc.append(actionType.substring(0, 1).toUpperCase()).append(actionType.substring(1));
            
            if (elementSelector != null && !elementSelector.trim().isEmpty()) {
                desc.append(" on element: ").append(elementSelector);
            }
            
            if (value != null && !value.trim().isEmpty()) {
                desc.append(" with value: ").append(value);
            }
        }
        
        return desc.toString();
    }
    
    /**
     * Helper class to hold assertion information
     */
    private static class AssertionInfo {
        final String elementSelector;
        final String expectedValue;
        
        AssertionInfo(String elementSelector, String expectedValue) {
            this.elementSelector = elementSelector;
            this.expectedValue = expectedValue;
        }
    }
}