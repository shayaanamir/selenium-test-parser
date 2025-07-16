package com.testparser.extractors;

import com.testparser.models.PageObject;
import com.testparser.models.TestCase;
import com.testparser.utils.ConfigPropertiesReader;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Main coordinator class for extracting test cases from Java test files
 */
public class TestMethodExtractor {
    
    private final TestFileScanner fileScanner;
    private final TestFileParser fileParser;
    
    public TestMethodExtractor() {
        this.fileScanner = new TestFileScanner();
        this.fileParser = new TestFileParser();
    }
    
    /**
     * Main entry point - extracts test cases from Java project
     */
    public List<TestCase> extractTestCases(String projectPath, Map<String, PageObject> pageObjects) {
        List<TestCase> testCases = new ArrayList<>();
        
        try {
            // Load URLs from config.properties
            Map<String, String> configUrls = ConfigPropertiesReader.loadUrlsFromProject(projectPath);
            
            File projectDir = new File(projectPath);
            fileScanner.scanForTestFiles(projectDir, testCases, pageObjects, configUrls, fileParser);
        } catch (Exception e) {
            System.err.println("Error extracting test cases: " + e.getMessage());
            e.printStackTrace();
        }
        
        return testCases;
    }
}