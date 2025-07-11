package com.testparser.extractors;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.SwitchStmt;
import com.github.javaparser.ast.stmt.SwitchEntry;
import com.testparser.models.PageObject;
import com.testparser.models.TestCase;
import com.testparser.models.TestStep;
import com.testparser.utils.ConfigPropertiesReader;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TestMethodExtractor {
    
    // Define regex patterns for matching different action types
    private static final Map<String, String> ACTION_PATTERNS = Map.of(
        "click", "click|Click",
        "type", "sendKeys|type|setText|enterText|enter",
        "select", "select|Select",
        "wait", "wait|Wait|until",
        "verify", "assert|verify|expect|should|Assert",
        "navigate", "get|navigate|goTo",
        "drag", "drag|dragAndDrop"
    );
    
    /**
     * Main entry point - extracts test cases from Java project
     */
    public static List<TestCase> extractTestCases(String projectPath, Map<String, PageObject> pageObjects) {
        List<TestCase> testCases = new ArrayList<>();
        
        try {
            // Load URLs from config.properties
            Map<String, String> configUrls = ConfigPropertiesReader.loadUrlsFromProject(projectPath);
            
            File projectDir = new File(projectPath);
            scanForTestFiles(projectDir, testCases, pageObjects, configUrls);
        } catch (Exception e) {
            System.err.println("Error extracting test cases: " + e.getMessage());
            e.printStackTrace();
        }
        
        return testCases;
    }
    
    /**
     * Recursively scan directory for test files
     */
    private static void scanForTestFiles(File dir, List<TestCase> testCases, Map<String, PageObject> pageObjects, Map<String, String> configUrls) {
        if (!dir.exists() || !dir.isDirectory()) {
            return;
        }
        
        File[] files = dir.listFiles();
        if (files == null) return;
        
        for (File file : files) {
            if (file.isDirectory()) {
                // Recursively scan subdirectories
                scanForTestFiles(file, testCases, pageObjects, configUrls);
            } else if (file.getName().endsWith(".java")) {
                if (isTestFile(file)) {
                    try {
                        parseTestFile(file, testCases, pageObjects, configUrls);
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
    private static boolean isTestFile(File file) {
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
    
    /**
     * Parse a test file and extract test cases
     */
    private static void parseTestFile(File file, List<TestCase> testCases, Map<String, PageObject> pageObjects, Map<String, String> configUrls) throws Exception {
        JavaParser parser = new JavaParser();
        CompilationUnit cu = parser.parse(new FileInputStream(file)).getResult().orElse(null);
        
        if (cu == null) {
            return;
        }
        
        String className = getClassName(cu);
        
        // Extract traditional @Test methods
        cu.findAll(MethodDeclaration.class).forEach(method -> {
            if (isTestMethod(method)) {
                TestCase testCase = extractTestCase(method, className, pageObjects, configUrls);
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
    private static String getClassName(CompilationUnit cu) {
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
    private static boolean isTestMethod(MethodDeclaration method) {
        return method.getAnnotations().stream()
                .anyMatch(ann -> ann.getNameAsString().equals("Test"));
    }
    
    /**
     * Check if method is data-driven (has @Test and contains switch statement)
     */
    private static boolean isDataDrivenTestMethod(MethodDeclaration method) {
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
    private static List<TestCase> extractDataDrivenTestCases(MethodDeclaration method, String className, 
                                                        Map<String, PageObject> pageObjects, CompilationUnit cu, Map<String, String> configUrls) {
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
                            TestCase testCase = extractTestCase(privateMethod, className, pageObjects, configUrls);
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
    private static String extractSwitchCaseValue(SwitchEntry entry) {
        if (entry.getLabels().isEmpty()) return "default";
        
        // Remove quotes from case value
        return entry.getLabels().get(0).toString().replace("\"", "");
    }
    
    /**
     * Find a private method by name in the compilation unit
     */
    private static MethodDeclaration findPrivateMethod(CompilationUnit cu, String methodName) {
        return cu.findAll(MethodDeclaration.class).stream()
                .filter(method -> method.getNameAsString().equals(methodName))
                .filter(method -> method.isPrivate() || method.isPublic()) // Include both private and public methods
                .findFirst()
                .orElse(null);
    }
    
    /**
     * Extract test case details from a method
     */
    private static TestCase extractTestCase(MethodDeclaration method, String className, Map<String, PageObject> pageObjects, Map<String, String> configUrls) {
        String testName = method.getNameAsString();
        String description = extractDescription(method);
        List<TestStep> steps = extractSteps(method, pageObjects);
        
        // Extract URL from config based on test method name
        String testUrl = ConfigPropertiesReader.findMatchingUrl(testName, configUrls);
        
        return new TestCase(testName, className, description, steps, testUrl);
    }
    /**
     * Extract description from Javadoc, comments, or method name
     */
    private static String extractDescription(MethodDeclaration method) {
        // Try Javadoc first
        String javadocDesc = method.getJavadoc()
                .map(javadoc -> javadoc.getDescription().toText())
                .orElse(null);
        
        if (javadocDesc != null && !javadocDesc.trim().isEmpty()) {
            return javadocDesc;
        }
        
        // Try comments above method
        String commentDesc = extractCommentDescription(method);
        if (commentDesc != null && !commentDesc.trim().isEmpty()) {
            return commentDesc;
        }
        
        // Fallback to method name
        return method.getNameAsString();
    }
    
    /**
     * Extract description from single-line comments
     */
    private static String extractCommentDescription(MethodDeclaration method) {
        String methodSource = method.toString();
        String[] lines = methodSource.split("\n");
        
        // Look for comments mentioning "test case"
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.startsWith("//") && line.contains("test case")) {
                return line.substring(2).trim();
            }
        }
        
        return null;
    }
    
    /**
     * Extract test steps from method body
     */
    private static List<TestStep> extractSteps(MethodDeclaration method, Map<String, PageObject> pageObjects) {
        List<TestStep> steps = new ArrayList<>();
        
        method.getBody().ifPresent(body -> {
            int stepNumber = 1;
            // Process each statement in the method
            for (Statement stmt : body.getStatements()) {
                List<TestStep> stepsFromStatement = analyzeStatement(stmt, pageObjects, stepNumber);
                steps.addAll(stepsFromStatement);
                stepNumber += stepsFromStatement.size();
            }
        });
        
        return steps;
    }
    
    /**
     * Analyze a statement to extract test steps
     */
    private static List<TestStep> analyzeStatement(Statement stmt, Map<String, PageObject> pageObjects, int startingStepNumber) {
        List<TestStep> steps = new ArrayList<>();
        List<MethodCallExpr> methodCalls = stmt.findAll(MethodCallExpr.class);
        
        int currentStepNumber = startingStepNumber;
        // Process each method call in the statement
        for (MethodCallExpr call : methodCalls) {
            String methodName = call.getNameAsString();
            String actionType = determineActionType(methodName);
            
            if (actionType != null) {
                String elementSelector = findElementSelector(call, pageObjects);
                String value = extractValue(call);
                String description = generateStepDescription(actionType, elementSelector, value);
                
                steps.add(new TestStep(currentStepNumber, description, actionType, elementSelector, value));
                currentStepNumber++;
            }
        }
        
        return steps;
    }
    
    /**
     * Determine action type based on method name patterns
     */
    private static String determineActionType(String methodName) {
        for (Map.Entry<String, String> entry : ACTION_PATTERNS.entrySet()) {
            Pattern pattern = Pattern.compile(entry.getValue(), Pattern.CASE_INSENSITIVE);
            if (pattern.matcher(methodName).find()) {
                return entry.getKey();
            }
        }
        return null;
    }
    
    /**
     * Find element selector using multiple strategies
     */
    private static String findElementSelector(MethodCallExpr call, Map<String, PageObject> pageObjects) {
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
        String elementSelector = findElementBySemanticMatching(methodName, pageObjects);
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
     * Find element using semantic matching patterns
     */
    private static String findElementBySemanticMatching(String methodName, Map<String, PageObject> pageObjects) {
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
    private static String extractCoreElementName(String methodName) {
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
    private static boolean isSemanticMatch(String coreElementName, String elementKey, String fullMethodName) {
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
    
    /**
     * Remove common action prefixes from method names
     */
    private static String removeActionPrefixes(String methodName) {
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
    
    /**
     * Extract parameter value from method call
     */
    private static String extractValue(MethodCallExpr call) {
        // Extract first parameter value (for methods like enterProductName("iPhone"))
        if (call.getArguments().size() > 0) {
            String arg = call.getArguments().get(0).toString();
            // Remove quotes if present
            if (arg.startsWith("\"") && arg.endsWith("\"")) {
                return arg.substring(1, arg.length() - 1);
            }
            return arg;
        }
        
        return null;
    }
    
    /**
     * Generate human-readable step description
     */
    private static String generateStepDescription(String actionType, String elementSelector, String value) {
        StringBuilder desc = new StringBuilder();
        // Capitalize first letter of action type
        desc.append(actionType.substring(0, 1).toUpperCase()).append(actionType.substring(1));
        
        if (elementSelector != null && !elementSelector.trim().isEmpty()) {
            desc.append(" on element: ").append(elementSelector);
        }
        
        if (value != null && !value.trim().isEmpty()) {
            desc.append(" with value: ").append(value);
        }
        
        return desc.toString();
    }
}