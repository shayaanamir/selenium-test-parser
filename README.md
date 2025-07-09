# selenium-test-parser

A simple Java parser that extracts test cases from Selenium projects. Built with Maven.

## 📦 Requirements

- Java 8+
- Maven

## 🚀 Usage

Clone the repository and navigate to the project directory:

```bash
git clone https://github.com/yourusername/selenium-test-parser.git
cd selenium-test-parser
```

Compile the project:

```bash
mvn clean compile
```

Run the parser:
```bash
mvn exec:java -Dexec.args="<selenium-project-path> <output-file-name.json>"
```
