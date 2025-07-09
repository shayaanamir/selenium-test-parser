package com.testparser.models;

import java.util.Map;

public class PageObject {
    private String className;
    private Map<String, String> elements;
    
    public PageObject() {}
    
    public PageObject(String className, Map<String, String> elements) {
        this.className = className;
        this.elements = elements;
    }
    
    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }
    public Map<String, String> getElements() { return elements; }
    public void setElements(Map<String, String> elements) { this.elements = elements; }
}
