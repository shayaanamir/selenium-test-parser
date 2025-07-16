package com.testparser.extractors;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.stmt.SwitchStmt;
import com.github.javaparser.ast.stmt.SwitchEntry;
import com.testparser.models.PageObject;
import com.testparser.models.TestCase;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Handles parsing of individual test files and extraction of test methods
 */
public class TestFileParser {
    
    private final TestCaseExtractor testCaseExtractor;
    
    public TestFileParser() {
        this.testCaseExtractor = new TestCaseExtractor();
    }
    
    /**
     * Parse a test file and extract test cases
     */
    public void parseTestFile(File file, List<TestCase> testCases, Map<String, PageObject> pageObjects, 
                             Map<String, String> configUrls) throws Exception {
        JavaParser parser = new JavaParser();
        CompilationUnit cu = parser.parse(new FileInputStream(file)).getResult().orElse(null);
        
        if (cu == null) {
            return;
        }
        
        String className = getClassName(cu);
        
        // Extract traditional @Test methods
        cu.findAll(MethodDeclaration.class).forEach(method -> {
            if (isTestMethod(method)) {
                TestCase testCase = testCaseExtractor.extractTestCase(method, className, pageObjects, configUrls);
                if (testCase != null) {
                    testCases.add(testCase);
                }
            }
        });
        
        // Extract data-driven test methods (containing switch statements)
        cu.findAll(MethodDeclaration.class).forEach(method -> {
            if (isDataDrivenTestMethod(method)) {
                List<TestCase> dataDrivenCases = extractDataDrivenTestCases(method, className, pageObjects, cu, configUrls);
                testCases.addAll(dataDrivenCases);
            }
        });
    }
    
    /**
     * Extract class name from compilation unit
     */
    private String getClassName(CompilationUnit cu) {
        // Try primary type name first
        Optional<String> primaryTypeName = cu.getPrimaryTypeName();
        if (primaryTypeName.isPresent()) {
            return primaryTypeName.get();
        }
        
        // Fallback: find class declarations directly
        return cu.findAll(com.github.javaparser.ast.body.ClassOrInterfaceDeclaration.class).stream()
                .filter(classDecl -> !classDecl.isInterface()) // Only classes, not interfaces
                .map(classDecl -> classDecl.getNameAsString())
                .findFirst()
                .orElse("Unknown");
    }
    
    /**
     * Check if method has @Test annotation
     */
    private boolean isTestMethod(MethodDeclaration method) {
        return method.getAnnotations().stream()
                .anyMatch(ann -> ann.getNameAsString().equals("Test"));
    }
    
    /**
     * Check if method is data-driven (has @Test and contains switch statement)
     */
    private boolean isDataDrivenTestMethod(MethodDeclaration method) {
        // Must have @Test annotation
        boolean hasTestAnnotation = method.getAnnotations().stream()
                .anyMatch(ann -> ann.getNameAsString().equals("Test"));
        
        if (!hasTestAnnotation) return false;
        
        // Must contain switch statement for data-driven logic
        return method.getBody().map(body -> 
            body.findAll(SwitchStmt.class).size() > 0
        ).orElse(false);
    }
    
    /**
     * Extract test cases from data-driven test methods
     */
    private List<TestCase> extractDataDrivenTestCases(MethodDeclaration method, String className, 
                                                     Map<String, PageObject> pageObjects, CompilationUnit cu, 
                                                     Map<String, String> configUrls) {
        List<TestCase> testCases = new ArrayList<>();
        
        method.getBody().ifPresent(body -> {
            // Find switch statements in the method
            body.findAll(SwitchStmt.class).forEach(switchStmt -> {
                // Process each switch case
                switchStmt.getEntries().forEach(entry -> {
                    String caseValue = extractSwitchCaseValue(entry);
                    if (caseValue != null && !caseValue.equals("default")) {
                        // Find the corresponding private method for this case
                        MethodDeclaration privateMethod = findPrivateMethod(cu, caseValue);
                        if (privateMethod != null) {
                            TestCase testCase = testCaseExtractor.extractTestCase(privateMethod, className, pageObjects, configUrls);
                            if (testCase != null) {
                                testCases.add(testCase);
                            }
                        }
                    }
                });
            });
        });
        
        return testCases;
    }
    
    /**
     * Extract the value from a switch case
     */
    private String extractSwitchCaseValue(SwitchEntry entry) {
        if (entry.getLabels().isEmpty()) return "default";
        
        // Remove quotes from case value
        return entry.getLabels().get(0).toString().replace("\"", "");
    }
    
    /**
     * Find a private method by name in the compilation unit
     */
    private MethodDeclaration findPrivateMethod(CompilationUnit cu, String methodName) {
        return cu.findAll(MethodDeclaration.class).stream()
                .filter(method -> method.getNameAsString().equals(methodName))
                .filter(method -> method.isPrivate() || method.isPublic()) // Include both private and public methods
                .findFirst()
                .orElse(null);
    }
}