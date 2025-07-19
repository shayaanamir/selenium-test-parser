package com.testparser.extractors;

import com.github.javaparser.ast.expr.MethodCallExpr;
import com.testparser.models.PageObject;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles finding element selectors using multiple strategies
 */
public class ElementSelectorFinder {
    
    private final SemanticMatcher semanticMatcher;
    
    public ElementSelectorFinder() {
        this.semanticMatcher = new SemanticMatcher();
    }
    
    /**
     * Find element selector using multiple strategies
     */
    public String findElementSelector(MethodCallExpr call, Map<String, PageObject> pageObjects) {
        String scope = call.getScope().map(Object::toString).orElse("");
        String methodName = call.getNameAsString();
        
        // Check if this is a boolean assertion first
        if (isBooleanAssertion(call)) {
            String booleanElementSelector = inferElementFromBooleanAssertion(call, pageObjects);
            if (booleanElementSelector != null) {
                return booleanElementSelector;
            }
        }
        
        // Strategy 1: Look for direct By.* selector in method call
        String callString = call.toString();
        if (callString.contains("By.")) {
            Pattern byPattern = Pattern.compile("By\\.[a-zA-Z]+\\([^)]+\\)");
            Matcher matcher = byPattern.matcher(callString);
            if (matcher.find()) {
                return matcher.group();
            }
        }
        
        // Strategy 2: Extract element from assertion method arguments
        String assertionElementSelector = extractElementFromAssertionArgs(call, pageObjects);
        if (assertionElementSelector != null) {
            return assertionElementSelector;
        }
        
        // Strategy 3: Match page object method calls (e.g., loginPage.clickLoginButton())
        for (PageObject pageObject : pageObjects.values()) {
            String pageObjectClassName = pageObject.getClassName();
            
            // Check if scope matches page object class
            if (scope.toLowerCase().contains(pageObjectClassName.toLowerCase()) || 
                scope.toLowerCase().contains(pageObjectClassName.toLowerCase().replace("page", ""))) {
                
                // Direct method name match
                if (pageObject.getElements().containsKey(methodName)) {
                    return pageObject.getElements().get(methodName);
                }
                
                // Match after removing action prefixes (including assertion prefixes)
                String elementName = removeActionPrefixes(methodName);
                if (pageObject.getElements().containsKey(elementName)) {
                    return pageObject.getElements().get(elementName);
                }
                
                // Partial matches (case-insensitive)
                for (Map.Entry<String, String> element : pageObject.getElements().entrySet()) {
                    String elementKey = element.getKey().toLowerCase();
                    String methodLower = methodName.toLowerCase();
                    String elementNameLower = elementName.toLowerCase();
                    
                    if (elementKey.contains(methodLower) || methodLower.contains(elementKey) ||
                        elementKey.contains(elementNameLower) || elementNameLower.contains(elementKey)) {
                        return element.getValue();
                    }
                }
            }
        }
        
        // Strategy 4: Enhanced semantic matching across all page objects
        String elementSelector = semanticMatcher.findElementBySemanticMatching(methodName, pageObjects);
        if (elementSelector != null) {
            return elementSelector;
        }
        
        // Strategy 5: Search all page objects without scope matching
        for (PageObject pageObject : pageObjects.values()) {
            // Direct match
            if (pageObject.getElements().containsKey(methodName)) {
                return pageObject.getElements().get(methodName);
            }
            
            // Match after removing action prefixes (including assertion prefixes)
            String elementName = removeActionPrefixes(methodName);
            if (pageObject.getElements().containsKey(elementName)) {
                return pageObject.getElements().get(elementName);
            }
            
            // Enhanced partial matching for assertions
            for (Map.Entry<String, String> element : pageObject.getElements().entrySet()) {
                String elementKey = element.getKey().toLowerCase();
                String methodLower = methodName.toLowerCase();
                String elementNameLower = elementName.toLowerCase();
                
                if (elementKey.contains(methodLower) || methodLower.contains(elementKey) ||
                    elementKey.contains(elementNameLower) || elementNameLower.contains(elementKey)) {
                    return element.getValue();
                }
            }
        }
        
        return null;
    }
    
    /**
     * Check if this is a boolean assertion
     */
    private boolean isBooleanAssertion(MethodCallExpr call) {
        String callString = call.toString();
        String methodName = call.getNameAsString();
        
        // Check for explicit boolean values
        if (callString.contains("true") || callString.contains("false")) {
            return true;
        }
        
        // Check for boolean variable patterns (isXxx, hasXxx, canXxx, shouldXxx)
        if (callString.matches(".*\\b(is[A-Z][a-zA-Z]*|has[A-Z][a-zA-Z]*|can[A-Z][a-zA-Z]*|should[A-Z][a-zA-Z]*)\\b.*")) {
            return true;
        }
        
        // Check if it's an assertion method with boolean-like arguments
        if (isAssertionMethod(methodName)) {
            if (call.getArguments().size() > 0) {
                String firstArg = call.getArguments().get(0).toString();
                // Check for boolean method patterns in arguments
                if (firstArg.matches(".*(is[A-Z][a-zA-Z]*|has[A-Z][a-zA-Z]*|can[A-Z][a-zA-Z]*|should[A-Z][a-zA-Z]*)\\(\\).*")) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    /**
     * Infer element selector from boolean assertion
     */
    private String inferElementFromBooleanAssertion(MethodCallExpr call, Map<String, PageObject> pageObjects) {
        String callString = call.toString();
        
        // Extract boolean variable/method name
        String booleanIdentifier = extractBooleanIdentifier(callString);
        if (booleanIdentifier != null) {
            return findRelatedElement(booleanIdentifier, pageObjects);
        }
        
        return null;
    }
    
    /**
     * Extract boolean identifier from the call string
     */
    private String extractBooleanIdentifier(String callString) {
        // Pattern to match boolean identifiers like isProductClicked, hasLoginButton, etc.
        Pattern booleanPattern = Pattern.compile("\\b(is[A-Z][a-zA-Z]*|has[A-Z][a-zA-Z]*|can[A-Z][a-zA-Z]*|should[A-Z][a-zA-Z]*)\\b");
        Matcher matcher = booleanPattern.matcher(callString);
        
        if (matcher.find()) {
            return matcher.group(1);
        }
        
        // Also check for method calls like someMethod() that return boolean
        Pattern methodCallPattern = Pattern.compile("([a-zA-Z][a-zA-Z0-9]*)\\(\\)");
        matcher = methodCallPattern.matcher(callString);
        
        if (matcher.find()) {
            String methodName = matcher.group(1);
            // Only consider if it looks like a boolean method
            if (methodName.startsWith("is") || methodName.startsWith("has") || 
                methodName.startsWith("can") || methodName.startsWith("should") ||
                methodName.contains("Click") || methodName.contains("Valid") ||
                methodName.contains("Success") || methodName.contains("Complete")) {
                return methodName;
            }
        }
        
        return null;
    }
    
    /**
     * Find element related to the boolean identifier
     */
    private String findRelatedElement(String booleanIdentifier, Map<String, PageObject> pageObjects) {
        if (booleanIdentifier == null) {
            return null;
        }
        
        // Extract key terms from boolean identifier
        String[] keyTerms = extractKeyTerms(booleanIdentifier);
        
        for (PageObject pageObject : pageObjects.values()) {
            for (Map.Entry<String, String> element : pageObject.getElements().entrySet()) {
                String elementKey = element.getKey().toLowerCase();
                
                // Check if element key contains any of the key terms
                for (String term : keyTerms) {
                    if (elementKey.contains(term.toLowerCase())) {
                        return element.getValue();
                    }
                }
                
                // Reverse check - if any key term contains the element key
                for (String term : keyTerms) {
                    if (term.toLowerCase().contains(elementKey)) {
                        return element.getValue();
                    }
                }
            }
        }
        
        // If no direct match found, try semantic matching with the key terms
        for (String term : keyTerms) {
            String semanticMatch = semanticMatcher.findElementBySemanticMatching(term, pageObjects);
            if (semanticMatch != null) {
                return semanticMatch;
            }
        }
        
        return null;
    }
    
    /**
     * Extract key terms from boolean identifier
     */
    private String[] extractKeyTerms(String booleanIdentifier) {
        // Remove common boolean prefixes
        String withoutPrefix = booleanIdentifier;
        if (booleanIdentifier.startsWith("is")) {
            withoutPrefix = booleanIdentifier.substring(2);
        } else if (booleanIdentifier.startsWith("has")) {
            withoutPrefix = booleanIdentifier.substring(3);
        } else if (booleanIdentifier.startsWith("can")) {
            withoutPrefix = booleanIdentifier.substring(3);
        } else if (booleanIdentifier.startsWith("should")) {
            withoutPrefix = booleanIdentifier.substring(6);
        }
        
        // Split camelCase into words
        String[] terms = withoutPrefix.split("(?=[A-Z])");
        
        // Clean up terms and convert to lowercase
        for (int i = 0; i < terms.length; i++) {
            terms[i] = terms[i].toLowerCase();
            // Remove common action words that might not help in matching
            if (terms[i].equals("clicked") || terms[i].equals("valid") || 
                terms[i].equals("success") || terms[i].equals("complete") ||
                terms[i].equals("ed") || terms[i].isEmpty()) {
                terms[i] = "";
            }
        }
        
        return terms;
    }
    
    /**
     * Extract element selector from assertion method arguments
     */
    private String extractElementFromAssertionArgs(MethodCallExpr call, Map<String, PageObject> pageObjects) {
        String methodName = call.getNameAsString();
        
        // Check if this is an assertion method
        if (isAssertionMethod(methodName)) {
            // Look for element references in the arguments
            if (call.getArguments().size() > 0) {
                String firstArg = call.getArguments().get(0).toString();
                
                // Check if argument contains a method call that might reference an element
                if (firstArg.contains(".")) {
                    String[] parts = firstArg.split("\\.");
                    if (parts.length >= 2) {
                        String possibleScope = parts[0];
                        String possibleMethod = parts[1].replaceAll("\\([^)]*\\)", ""); // Remove parameters
                        
                        // Try to find element using the extracted scope and method
                        String elementSelector = findElementInPageObjects(possibleScope, possibleMethod, pageObjects);
                        if (elementSelector != null) {
                            return elementSelector;
                        }
                    }
                }
                
                // Check if argument directly references an element
                String elementSelector = findElementDirectly(firstArg, pageObjects);
                if (elementSelector != null) {
                    return elementSelector;
                }
            }
        }
        
        return null;
    }
    
    /**
     * Check if method is an assertion method
     */
    private boolean isAssertionMethod(String methodName) {
        String lowerMethodName = methodName.toLowerCase();
        return lowerMethodName.startsWith("assert") || 
               lowerMethodName.startsWith("verify") || 
               lowerMethodName.startsWith("expect") ||
               lowerMethodName.startsWith("check") ||
               lowerMethodName.contains("should") ||
               lowerMethodName.contains("equals") ||
               lowerMethodName.contains("contains") ||
               lowerMethodName.contains("visible") ||
               lowerMethodName.contains("displayed") ||
               lowerMethodName.contains("enabled") ||
               lowerMethodName.contains("present");
    }
    
    /**
     * Find element in page objects using scope and method
     */
    private String findElementInPageObjects(String scope, String method, Map<String, PageObject> pageObjects) {
        for (PageObject pageObject : pageObjects.values()) {
            String pageObjectClassName = pageObject.getClassName();
            
            // Check if scope matches page object class
            if (scope.toLowerCase().contains(pageObjectClassName.toLowerCase()) || 
                scope.toLowerCase().contains(pageObjectClassName.toLowerCase().replace("page", ""))) {
                
                // Direct method name match
                if (pageObject.getElements().containsKey(method)) {
                    return pageObject.getElements().get(method);
                }
                
                // Match after removing action prefixes
                String elementName = removeActionPrefixes(method);
                if (pageObject.getElements().containsKey(elementName)) {
                    return pageObject.getElements().get(elementName);
                }
                
                // Partial matches
                for (Map.Entry<String, String> element : pageObject.getElements().entrySet()) {
                    String elementKey = element.getKey().toLowerCase();
                    String methodLower = method.toLowerCase();
                    String elementNameLower = elementName.toLowerCase();
                    
                    if (elementKey.contains(methodLower) || methodLower.contains(elementKey) ||
                        elementKey.contains(elementNameLower) || elementNameLower.contains(elementKey)) {
                        return element.getValue();
                    }
                }
            }
        }
        
        return null;
    }
    
    /**
     * Find element directly by name across all page objects
     */
    private String findElementDirectly(String elementRef, Map<String, PageObject> pageObjects) {
        for (PageObject pageObject : pageObjects.values()) {
            // Direct match
            if (pageObject.getElements().containsKey(elementRef)) {
                return pageObject.getElements().get(elementRef);
            }
            
            // Partial matches
            for (Map.Entry<String, String> element : pageObject.getElements().entrySet()) {
                String elementKey = element.getKey().toLowerCase();
                String elementRefLower = elementRef.toLowerCase();
                
                if (elementKey.contains(elementRefLower) || elementRefLower.contains(elementKey)) {
                    return element.getValue();
                }
            }
        }
        
        return null;
    }
    
    /**
     * Remove common action prefixes from method names (including assertion prefixes)
     */
    private String removeActionPrefixes(String methodName) {
        String[] prefixes = {
            "click", "enter", "select", "type", "set", "get", "wait", 
            "verify", "assert", "send", "check", "expect", "should",
            "is", "has", "contains", "equals", "visible", "displayed",
            "enabled", "present", "getText", "getValue", "getAttribute"
        };
        String lowerMethodName = methodName.toLowerCase();
        
        for (String prefix : prefixes) {
            if (lowerMethodName.startsWith(prefix.toLowerCase())) {
                String remaining = methodName.substring(prefix.length());
                if (remaining.length() > 0) {
                    // Convert first character to lowercase
                    return remaining.substring(0, 1).toLowerCase() + remaining.substring(1);
                }
            }
        }
        
        return methodName;
    }
}