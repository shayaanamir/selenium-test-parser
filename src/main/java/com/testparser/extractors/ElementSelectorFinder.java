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