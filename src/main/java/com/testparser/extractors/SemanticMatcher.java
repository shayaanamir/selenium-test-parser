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
     * Extract core element name by removing action prefixes
     */
    private String extractCoreElementName(String methodName) {
        String[] actionPrefixes = {"click", "enter", "select", "type", "set", "get", "wait", "verify", "assert", "send"};
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
        
        // Semantic mappings for common UI patterns
        Map<String, String[]> semanticMappings = Map.of(
            "search", new String[]{"search", "find", "query", "box"},
            "login", new String[]{"login", "signin", "username", "email"},
            "password", new String[]{"password", "pass", "pwd"},
            "submit", new String[]{"submit", "send", "save", "confirm", "button"},
            "product", new String[]{"product", "item", "goods"},
            "cart", new String[]{"cart", "basket", "bag"},
            "checkout", new String[]{"checkout", "pay", "purchase", "order"}
        );
        
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
        
        return false;
    }
}