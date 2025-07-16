package com.testparser.extractors;

import com.testparser.models.PageObject;
import com.testparser.models.TestCase;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * Handles scanning and identification of test files in project directories
 */
public class TestFileScanner {
    
    /**
     * Recursively scan directory for test files
     */
    public void scanForTestFiles(File dir, List<TestCase> testCases, Map<String, PageObject> pageObjects, 
                                Map<String, String> configUrls, TestFileParser parser) {
        if (!dir.exists() || !dir.isDirectory()) {
            return;
        }
        
        File[] files = dir.listFiles();
        if (files == null) return;
        
        for (File file : files) {
            if (file.isDirectory()) {
                // Recursively scan subdirectories
                scanForTestFiles(file, testCases, pageObjects, configUrls, parser);
            } else if (file.getName().endsWith(".java")) {
                if (isTestFile(file)) {
                    try {
                        parser.parseTestFile(file, testCases, pageObjects, configUrls);
                    } catch (Exception e) {
                        System.err.println("Error parsing test file " + file.getName() + ": " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            }
        }
    }
    
    /**
     * Determine if a file is a test file based on location and naming patterns
     */
    private boolean isTestFile(File file) {
        String fileName = file.getName();
        String parentDirName = file.getParentFile().getName().toLowerCase();
        
        // Check if file is in tests folder
        if (parentDirName.equals("test") || parentDirName.equals("tests")) {
            return true;
        }
        
        // Check if any parent directory is test/tests
        File current = file.getParentFile();
        while (current != null) {
            String dirName = current.getName().toLowerCase();
            if (dirName.equals("test") || dirName.equals("tests")) {
                return true;
            }
            current = current.getParentFile();
        }
        
        // Check filename patterns (Test prefix or Test/Tests suffix)
        return fileName.startsWith("Test") || 
               fileName.endsWith("Test.java") || 
               fileName.endsWith("Tests.java");
    }
}