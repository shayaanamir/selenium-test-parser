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
        
        // Strategy 2: Match page object method calls (e.g., loginPage.clickLoginButton())
        for (PageObject pageObject : pageObjects.values()) {
            String pageObjectClassName = pageObject.getClassName();
            
            // Check if scope matches page object class
            if (scope.toLowerCase().contains(pageObjectClassName.toLowerCase()) || 
                scope.toLowerCase().contains(pageObjectClassName.toLowerCase().replace("page", ""))) {
                
                // Direct method name match
                if (pageObject.getElements().containsKey(methodName)) {
                    return pageObject.getElements().get(methodName);
                }
                
                // Match after removing action prefixes
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
        
        // Strategy 3: Enhanced semantic matching across all page objects
        String elementSelector = semanticMatcher.findElementBySemanticMatching(methodName, pageObjects);
        if (elementSelector != null) {
            return elementSelector;
        }
        
        // Strategy 4: Search all page objects without scope matching
        for (PageObject pageObject : pageObjects.values()) {
            // Direct match
            if (pageObject.getElements().containsKey(methodName)) {
                return pageObject.getElements().get(methodName);
            }
            
            // Match after removing action prefixes
            String elementName = removeActionPrefixes(methodName);
            if (pageObject.getElements().containsKey(elementName)) {
                return pageObject.getElements().get(elementName);
            }
        }
        
        return null;
    }
    
    /**
     * Remove common action prefixes from method names
     */
    private String removeActionPrefixes(String methodName) {
        String[] prefixes = {"click", "enter", "select", "type", "set", "get", "wait", "verify", "assert", "send"};
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