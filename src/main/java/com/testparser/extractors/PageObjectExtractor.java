package com.testparser.extractors;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.testparser.models.PageObject;

import java.io.File;
import java.io.FileInputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class PageObjectExtractor {
    
    public static Map<String, PageObject> extractPageObjects(String projectPath) {
        Map<String, PageObject> pageObjects = new HashMap<>();
        
        try {
            File projectDir = new File(projectPath);
            scanForPageObjects(projectDir, pageObjects);
        } catch (Exception e) {
            System.err.println("Error extracting page objects: " + e.getMessage());
            e.printStackTrace();
        }
        
        return pageObjects;
    }
    
    private static void scanForPageObjects(File dir, Map<String, PageObject> pageObjects) {
        if (!dir.exists() || !dir.isDirectory()) {
            return;
        }
        
        File[] files = dir.listFiles();
        if (files == null) return;
        
        for (File file : files) {
            if (file.isDirectory()) {
                scanForPageObjects(file, pageObjects);
            } else if (file.getName().endsWith(".java")) {
                if (isPageObjectFile(file)) {
                    try {
                        parsePageObjectFile(file, pageObjects);
                    } catch (Exception e) {
                        System.err.println("Error parsing file " + file.getName() + ": " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            }
        }
    }
    
    private static boolean isPageObjectFile(File file) {
        String fileName = file.getName();
        String parentDirName = file.getParentFile().getName().toLowerCase();
        
        // Check if file is in page/pages folder
        if (parentDirName.equals("page") || parentDirName.equals("pages")) {
            return true;
        }
        
        // Check if any parent directory is page/pages
        File current = file.getParentFile();
        while (current != null) {
            String dirName = current.getName().toLowerCase();
            if (dirName.equals("page") || dirName.equals("pages")) {
                return true;
            }
            current = current.getParentFile();
        }
        
        // Check if file contains @FindBy annotations or extends page object patterns
        return containsPageObjectPatterns(file);
    }
    
    private static boolean containsPageObjectPatterns(File file) {
        try {
            JavaParser parser = new JavaParser();
            CompilationUnit cu = parser.parse(new FileInputStream(file)).getResult().orElse(null);
            
            if (cu == null) {
                return false;
            }
            
            // Check for @FindBy annotations
            boolean hasFindByAnnotations = cu.findAll(FieldDeclaration.class).stream()
                    .anyMatch(field -> field.getAnnotations().stream()
                            .anyMatch(ann -> ann.getNameAsString().equals("FindBy") || 
                                           ann.getNameAsString().equals("FindElement")));
            
            // Check for WebElement fields
            boolean hasWebElementFields = cu.findAll(FieldDeclaration.class).stream()
                    .anyMatch(field -> field.getElementType().asString().contains("WebElement"));
            
            // Check for PageFactory usage
            boolean hasPageFactory = cu.toString().contains("PageFactory");
            
            return hasFindByAnnotations || hasWebElementFields || hasPageFactory;
            
        } catch (Exception e) {
            return false;
        }
    }
    
    private static void parsePageObjectFile(File file, Map<String, PageObject> pageObjects) throws Exception {
        // Parse the file
        CompilationUnit cu;
        try (FileInputStream in = new FileInputStream(file)) {
            JavaParser parser = new JavaParser();
            cu = parser.parse(in).getResult().orElse(null);

            if (cu == null) {
                return;
            }
        }

        String className = getClassName(cu);
        if (className == null) {
            return;
        }

        for (FieldDeclaration field : cu.findAll(FieldDeclaration.class)) {
            if (isWebElementField(field)) {
                PageObject pageObject = pageObjects.computeIfAbsent(className, k -> 
                    new PageObject(k, new HashMap<>()));

                for (VariableDeclarator var : field.getVariables()) {
                    String elementName = var.getNameAsString();
                    String selector = extractSelector(field);

                    if (selector != null) {
                        pageObject.getElements().put(elementName, selector);
                    }
                }
            }
        }
    }

    private static boolean isWebElementField(FieldDeclaration field) {
        // Check if the field type contains WebElement
        String fieldType = field.getElementType().asString();
        boolean isWebElement = fieldType.contains("WebElement");
        
        // Also check if it has @FindBy annotation
        boolean hasFindByAnnotation = field.getAnnotations().stream()
                .anyMatch(ann -> ann.getNameAsString().equals("FindBy") || 
                               ann.getNameAsString().equals("FindElement"));
        
        return isWebElement || hasFindByAnnotation;
    }
    
    private static String getClassName(CompilationUnit cu) {
        // First try the original method
        Optional<String> primaryTypeName = cu.getPrimaryTypeName();
        if (primaryTypeName.isPresent()) {
            return primaryTypeName.get();
        }
        
        // If that fails, look for class declarations directly
        return cu.findAll(com.github.javaparser.ast.body.ClassOrInterfaceDeclaration.class).stream()
                .filter(classDecl -> !classDecl.isInterface()) // Only classes, not interfaces
                .map(classDecl -> classDecl.getNameAsString())
                .findFirst()
                .orElse(null);
    }
    
    private static String extractSelector(FieldDeclaration field) {
        for (AnnotationExpr annotation : field.getAnnotations()) {
            String annName = annotation.getNameAsString();
            if ("FindBy".equals(annName) || "FindElement".equals(annName)) {
                return extractSelectorFromAnnotation(annotation);
            }
        }
        return null;
    }
    
    private static String extractSelectorFromAnnotation(AnnotationExpr annotation) {
        if (annotation instanceof SingleMemberAnnotationExpr) {
            // @FindBy("value") format
            SingleMemberAnnotationExpr singleMember = (SingleMemberAnnotationExpr) annotation;
            String value = singleMember.getMemberValue().toString();
            return cleanSelectorValue(value);
        } else if (annotation instanceof NormalAnnotationExpr) {
            // @FindBy(xpath="value", id="value", etc.) format
            NormalAnnotationExpr normalAnnotation = (NormalAnnotationExpr) annotation;
            
            // Priority order: xpath, id, name, className, css, tagName
            String[] priorityOrder = {"xpath", "id", "name", "className", "css", "tagName"};
            
            for (String locatorType : priorityOrder) {
                Optional<String> value = normalAnnotation.getPairs().stream()
                        .filter(pair -> pair.getNameAsString().equals(locatorType))
                        .map(MemberValuePair::getValue)
                        .map(Object::toString)
                        .findFirst();
                
                if (value.isPresent()) {
                    String cleanValue = cleanSelectorValue(value.get());
                    return "By." + locatorType + "(" + cleanValue + ")";
                }
            }
        }
        
        // Fallback - return the entire annotation string
        return annotation.toString();
    }
    
    private static String cleanSelectorValue(String value) {
        // Remove surrounding quotes if present
        if (value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1);
        }
        return "\"" + value + "\"";
    }
}