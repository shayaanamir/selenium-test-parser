package com.testparser;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.testparser.extractors.PageObjectExtractor;
import com.testparser.extractors.TestMethodExtractor;
import com.testparser.models.PageObject;
import com.testparser.models.TestCase;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses Selenium test projects to extract test cases and page objects into JSON format.
 * Usage: java -jar selenium-test-parser.jar <project-path> [output-file]
 */
public class TestCaseParser {
    
    /**
     * Main entry point - parses project and outputs JSON report.
     */
    public static void main(String[] args) {
        // Validate command line arguments
        if (args.length < 1) {
            System.out.println("Usage: java -jar selenium-test-parser.jar <project-path> [output-file]");
            System.exit(1);
        }
        
        // Extract command line parameters
        String projectPath = args[0];
        String outputFile = args.length > 1 ? args[1] : "test-cases.json";
        
        try {
            // Parse the project and generate output
            parseProject(projectPath, outputFile);
            System.out.println("Test cases successfully extracted to: " + outputFile);
        } catch (Exception e) {
            // Handle any errors during parsing
            System.err.println("Error parsing project: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Parses project to extract page objects, test cases, and generates JSON output.
     */
    public static void parseProject(String projectPath, String outputFile) throws IOException {
        // Extract page objects and test cases
        Map<String, PageObject> pageObjects = PageObjectExtractor.extractPageObjects(projectPath);
        List<TestCase> testCases = TestMethodExtractor.extractTestCases(projectPath, pageObjects);
        
        // Create output structure with summary statistics
        Map<String, Object> output = new HashMap<>();
        output.put("pageObjects", pageObjects);
        output.put("testCases", testCases);
        output.put("summary", createSummary(testCases, pageObjects));
        
        // Write to JSON file
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.writeValue(new File(outputFile), output);
    }
    
    /**
     * Creates summary statistics for test cases and page objects.
     */
    private static Map<String, Object> createSummary(List<TestCase> testCases, Map<String, PageObject> pageObjects) {
        Map<String, Object> summary = new HashMap<>();
        
        // Count totals
        summary.put("totalTestCases", testCases.size());
        summary.put("totalPageObjects", pageObjects.size());
        
        // Calculate total steps across all test cases
        int totalSteps = testCases.stream()
                .mapToInt(tc -> tc.getSteps().size())
                .sum();
        summary.put("totalSteps", totalSteps);
        
        // Count action types used in test steps
        Map<String, Long> actionCounts = testCases.stream()
                .flatMap(tc -> tc.getSteps().stream())
                .collect(java.util.stream.Collectors.groupingBy(
                    step -> step.getActionType() != null ? step.getActionType() : "unknown",
                    java.util.stream.Collectors.counting()
                ));
        summary.put("actionTypeCounts", actionCounts);
        
        return summary;
    }
}