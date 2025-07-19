package com.testparser.extractors;

import com.testparser.models.PageObject;

import java.util.Map;

/**
 * Handles semantic matching of element names and method names
 */
public class SemanticMatcher {
    
    /**
     * Find element using semantic matching patterns
     */
    public String findElementBySemanticMatching(String methodName, Map<String, PageObject> pageObjects) {
        String coreElementName = extractCoreElementName(methodName);
        
        // Search through all page objects for semantic matches
        for (PageObject pageObject : pageObjects.values()) {
            for (Map.Entry<String, String> element : pageObject.getElements().entrySet()) {
                String elementKey = element.getKey();
                String elementSelector = element.getValue();
                
                if (isSemanticMatch(coreElementName, elementKey, methodName)) {
                    return elementSelector;
                }
            }
        }
        
        return null;
    }
    
    /**
     * Extract core element name by removing action prefixes (including assertion prefixes)
     */
    private String extractCoreElementName(String methodName) {
        String[] actionPrefixes = {
            "click", "enter", "select", "type", "set", "get", "wait", 
            "verify", "assert", "send", "check", "expect", "should",
            "is", "has", "contains", "equals", "visible", "displayed",
            "enabled", "present", "getText", "getValue", "getAttribute"
        };
        String lowerMethodName = methodName.toLowerCase();
        
        for (String prefix : actionPrefixes) {
            if (lowerMethodName.startsWith(prefix)) {
                String remaining = methodName.substring(prefix.length());
                if (remaining.length() > 0) {
                    // Convert first character to lowercase
                    return remaining.substring(0, 1).toLowerCase() + remaining.substring(1);
                }
            }
        }
        
        return methodName;
    }
    
    /**
     * Check if two element names are semantically related
     */
    private boolean isSemanticMatch(String coreElementName, String elementKey, String fullMethodName) {
        String lowerCoreElement = coreElementName.toLowerCase();
        String lowerElementKey = elementKey.toLowerCase();
        String lowerFullMethod = fullMethodName.toLowerCase();
        
        // Exact match
        if (lowerCoreElement.equals(lowerElementKey)) {
            return true;
        }
        
        // Contains match (both directions)
        if (lowerCoreElement.contains(lowerElementKey) || lowerElementKey.contains(lowerCoreElement)) {
            return true;
        }
        
        // Check if full method name contains element key
        if (lowerFullMethod.contains(lowerElementKey) || lowerElementKey.contains(lowerFullMethod)) {
            return true;
        }
        
        // Enhanced semantic mappings for common UI patterns including assertions
        Map<String, String[]> semanticMappings = Map.of(
            "search", new String[]{"search", "find", "query", "box", "input"},
            "login", new String[]{"login", "signin", "username", "email", "user"},
            "password", new String[]{"password", "pass", "pwd"},
            "submit", new String[]{"submit", "send", "save", "confirm", "button"},
            "product", new String[]{"product", "item", "goods"},
            "cart", new String[]{"cart", "basket", "bag"},
            "checkout", new String[]{"checkout", "pay", "purchase", "order"},
            "text", new String[]{"text", "label", "span", "div", "message", "content"},
            "button", new String[]{"button", "btn", "link", "click"},
            "field", new String[]{"field", "input", "textbox", "box"}
        );
        
        // Check semantic mappings
        for (Map.Entry<String, String[]> mapping : semanticMappings.entrySet()) {
            String key = mapping.getKey();
            String[] synonyms = mapping.getValue();
            
            // Check if core element name matches the key
            if (lowerCoreElement.contains(key)) {
                // Check if element key matches any synonym
                for (String synonym : synonyms) {
                    if (lowerElementKey.contains(synonym)) {
                        return true;
                    }
                }
            }
            
            // Check if element key matches the key
            if (lowerElementKey.contains(key)) {
                // Check if core element name matches any synonym
                for (String synonym : synonyms) {
                    if (lowerCoreElement.contains(synonym)) {
                        return true;
                    }
                }
            }
        }
        
        // Additional matching for assertion-specific patterns
        if (isAssertionMethod(fullMethodName)) {
            return matchAssertionPatterns(coreElementName, elementKey, fullMethodName);
        }
        
        return false;
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
     * Match assertion-specific patterns
     */
    private boolean matchAssertionPatterns(String coreElementName, String elementKey, String fullMethodName) {
        String lowerCoreElement = coreElementName.toLowerCase();
        String lowerElementKey = elementKey.toLowerCase();
        String lowerFullMethod = fullMethodName.toLowerCase();
        
        // Common assertion patterns
        Map<String, String[]> assertionPatterns = Map.of(
            "visible", new String[]{"visible", "displayed", "shown", "present"},
            "enabled", new String[]{"enabled", "active", "clickable"},
            "text", new String[]{"text", "content", "value", "label"},
            "equals", new String[]{"equal", "same", "match"},
            "contains", new String[]{"contain", "include", "has"},
            "empty", new String[]{"empty", "blank", "null"},
            "error", new String[]{"error", "warning", "alert", "message"},
            "success", new String[]{"success", "confirmation", "complete"}
        );
        
        // Check assertion patterns
        for (Map.Entry<String, String[]> pattern : assertionPatterns.entrySet()) {
            String key = pattern.getKey();
            String[] synonyms = pattern.getValue();
            
            // If the method contains assertion keywords
            if (lowerFullMethod.contains(key)) {
                for (String synonym : synonyms) {
                    if (lowerElementKey.contains(synonym) || lowerCoreElement.contains(synonym)) {
                        return true;
                    }
                }
            }
        }
        
        // Special handling for common assertion method patterns
        if (lowerFullMethod.contains("assert") || lowerFullMethod.contains("verify")) {
            // Look for element references in the core element name
            if (lowerCoreElement.length() > 2) {
                // Try to match partial element names
                String[] elementParts = lowerElementKey.split("_");
                for (String part : elementParts) {
                    if (part.length() > 2 && lowerCoreElement.contains(part)) {
                        return true;
                    }
                }
            }
        }
        
        return false;
    }
}