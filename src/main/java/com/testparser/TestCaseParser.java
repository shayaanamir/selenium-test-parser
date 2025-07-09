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

public class TestCaseParser {
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java -jar selenium-test-parser.jar <project-path> [output-file]");
            System.exit(1);
        }
        
        String projectPath = args[0];
        String outputFile = args.length > 1 ? args[1] : "test-cases.json";
        
        try {
            parseProject(projectPath, outputFile);
            System.out.println("Test cases successfully extracted to: " + outputFile);
        } catch (Exception e) {
            System.err.println("Error parsing project: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    public static void parseProject(String projectPath, String outputFile) throws IOException {
        // Extract page objects
        Map<String, PageObject> pageObjects = PageObjectExtractor.extractPageObjects(projectPath);
        
        // Extract test cases
        List<TestCase> testCases = TestMethodExtractor.extractTestCases(projectPath, pageObjects);
        
        // Create output structure
        Map<String, Object> output = new HashMap<>();
        output.put("pageObjects", pageObjects);
        output.put("testCases", testCases);
        output.put("summary", createSummary(testCases, pageObjects));
        
        // Write to JSON file
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.writeValue(new File(outputFile), output);
    }
    
    private static Map<String, Object> createSummary(List<TestCase> testCases, Map<String, PageObject> pageObjects) {
        Map<String, Object> summary = new HashMap<>();
        summary.put("totalTestCases", testCases.size());
        summary.put("totalPageObjects", pageObjects.size());
        
        int totalSteps = testCases.stream()
                .mapToInt(tc -> tc.getSteps().size())
                .sum();
        summary.put("totalSteps", totalSteps);
        
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