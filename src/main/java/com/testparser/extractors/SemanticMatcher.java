package com.testparser.extractors;

import com.testparser.models.PageObject;

import java.util.Map;

/**
 * Enhanced SemanticMatcher with assertion-specific patterns
 */
public class SemanticMatcher {
    
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
     * Extract core element name by removing action prefixes and assertion prefixes
     */
    private String extractCoreElementName(String methodName) {
        String[] actionPrefixes = {"click", "enter", "select", "type", "set", "get", "wait", "send"};
        String[] assertionPrefixes = {"assert", "verify", "check", "expect", "should", "is", "has"};
        
        String lowerMethodName = methodName.toLowerCase();
        
        // Remove action prefixes first
        for (String prefix : actionPrefixes) {
            if (lowerMethodName.startsWith(prefix)) {
                String remaining = methodName.substring(prefix.length());
                if (remaining.length() > 0) {
                    return remaining.substring(0, 1).toLowerCase() + remaining.substring(1);
                }
            }
        }
        
        // Remove assertion prefixes
        for (String prefix : assertionPrefixes) {
            if (lowerMethodName.startsWith(prefix)) {
                String remaining = methodName.substring(prefix.length());
                if (remaining.length() > 0) {
                    return remaining.substring(0, 1).toLowerCase() + remaining.substring(1);
                }
            }
        }
        
        return methodName;
    }
    
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
        
        // Enhanced semantic mappings including assertion patterns
        Map<String, String[]> semanticMappings = Map.of(
            "search", new String[]{"search", "find", "query", "box"},
            "login", new String[]{"login", "signin", "username", "email"},
            "password", new String[]{"password", "pass", "pwd"},
            "submit", new String[]{"submit", "send", "save", "confirm", "button"},
            "product", new String[]{"product", "item", "goods", "clicked", "selected"},
            "cart", new String[]{"cart", "basket", "bag"},
            "checkout", new String[]{"checkout", "pay", "purchase", "order"},
            "message", new String[]{"message", "notification", "alert", "error", "success"},
            "visible", new String[]{"visible", "displayed", "shown", "present"},
            "enabled", new String[]{"enabled", "active", "clickable", "available"}
        );
        
        for (Map.Entry<String, String[]> mapping : semanticMappings.entrySet()) {
            String key = mapping.getKey();
            String[] synonyms = mapping.getValue();
            
            if (lowerCoreElement.contains(key)) {
                for (String synonym : synonyms) {
                    if (lowerElementKey.contains(synonym)) {
                        return true;
                    }
                }
            }
            
            if (lowerElementKey.contains(key)) {
                for (String synonym : synonyms) {
                    if (lowerCoreElement.contains(synonym)) {
                        return true;
                    }
                }
            }
        }
        
        // Special handling for boolean assertion patterns
        if (lowerFullMethod.contains("is") || lowerFullMethod.contains("has")) {
            // For methods like isProductClicked(), try to match "product" with elements
            String[] booleanPrefixes = {"is", "has", "get"};
            for (String prefix : booleanPrefixes) {
                if (lowerFullMethod.startsWith(prefix)) {
                    String remaining = lowerFullMethod.substring(prefix.length());
                    if (remaining.contains(lowerElementKey) || lowerElementKey.contains(remaining)) {
                        return true;
                    }
                }
            }
        }
        
        return false;
    }
}