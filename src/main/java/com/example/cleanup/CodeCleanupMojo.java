package com.example.cleanup;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;

import static java.nio.file.Files.readAllBytes;
import static java.util.regex.Pattern.MULTILINE;
import static java.util.regex.Pattern.compile;

@Mojo(name = "cleanup")
public class CodeCleanupMojo extends AbstractMojo {
    @Parameter(defaultValue = "${project.basedir}/src", property = "sourceDir")
    private File sourceDir;
    @Parameter(defaultValue = "true", property = "checkUnusedImports")
    private boolean checkUnusedImports;
    @Parameter(property = "maxLineLength", defaultValue = "-1")
    private int maxLineLength;
    @Parameter(defaultValue = "true", property = "checkNewLineAtEnd")
    private boolean checkNewLineAtEnd;
    @Parameter(defaultValue = "true", property = "checkTODOs")
    private boolean checkTODOs;
    @Parameter(defaultValue = "-1", property = "maxMethodParameters")
    private int maxMethodParameters;

    @Override
    public void execute() throws MojoExecutionException {
        if (!sourceDir.exists()) {
            getLog().warn("Source directory does not exist: " + sourceDir.getAbsolutePath());
            return;
        }

        getLog().info("Scanning code for issues in: " + sourceDir.getAbsolutePath());

        try {
            var javaFiles = Files.walk(sourceDir.toPath())
                    .filter(p -> p.toString().endsWith(".java"))
                    .toList();
            getLog().info("Files: " + javaFiles.stream().toList());

            var violationsFound = javaFiles.stream()
                    .map(this::checkFileForViolations)
                    .reduce(Boolean::logicalOr)
                    .orElse(false);
            if (violationsFound) {
                throw new MojoExecutionException("Code cleanup violations found!");
            } else {
                getLog().info("No violations found.");
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Error walking source files", e);
        }
    }

    private boolean checkFileForViolations(Path file) {
        var violationsFound = false;
        try {
            var lines = Files.readAllLines(file);
            if (checkNewLineAtEnd) {
                getLog().warn("Missing newline at end of file: " + file);
                violationsFound = !hasNewlineAtEOF(file);
            }

            if (maxLineLength != -1) {
                violationsFound = violationsFound || hasMaxLineLengthExceeded(lines, file);
            }

            if (checkTODOs) {
                violationsFound = violationsFound || hasToDoComments(lines, file);
            }

            if (maxMethodParameters != -1) {
                violationsFound = violationsFound || hasMaxParamsExceeded(lines, file);
            }

            if (checkUnusedImports) {
                violationsFound = hasUnusedImports(file);
            }
        } catch (IOException e) {
            getLog().error("Error processing file: " + file, e);
        }

        return violationsFound;
    }

    private boolean hasMaxParamsExceeded(List<String> lines, Path file) {
        var violationsFound = false;
        var methodPattern = compile("^\\s*(?:[\\w<>\\[\\]]+\\s+)+?(\\w+)\\s*\\(([^)]*)\\)\\s*\\{?", MULTILINE);

        for (var i = 0; i < lines.size(); i++) {
            var line = lines.get(i).trim();
            var matcher = methodPattern.matcher(line);

            while (matcher.find()) {
                var methodName = matcher.group(1);
                var paramBlock = matcher.group(2).trim();

                if (!paramBlock.isEmpty()) {
                    var paramCount = paramBlock.split(",").length;

                    if (paramCount > maxMethodParameters) {
                        getLog().warn("Method '" + methodName + "' on line " + (i + 1) + " in file " + file +
                                " has " + paramCount + " parameters (max allowed: " + maxMethodParameters + ")");
                        violationsFound = true;
                    }
                }
            }
        }

        return violationsFound;
    }

    private boolean hasToDoComments(List<String> lines, Path file) {
        var violationsFound = false;
        for (var i = 0; i < lines.size(); i++) {
            if (lines.get(i).contains("TODO")) {
                getLog().warn("TODO found in file: " + file + " at line " + (i + 1) + ": " + lines.get(i).trim());
                violationsFound = true;
            }
        }

        return violationsFound;
    }

    private boolean hasMaxLineLengthExceeded(List<String> lines, Path file) {
        var violationsFound = false;
        for (var i = 0; i < lines.size(); i++) {
            if (lines.get(i).length() > maxLineLength - 1) {
                getLog().warn("Max line length exceeded found in file: " + file + " at line " + (i + 1));
                violationsFound = true;
            }
        }

        return violationsFound;
    }

    private boolean hasNewlineAtEOF(Path file) throws IOException {
        var bytes = readAllBytes(file);
        if (bytes.length == 0) return false;

        // Check if last byte is newline (LF or CR)
        var lastByte = bytes[bytes.length - 1];
        return lastByte == '\n' || lastByte == '\r';
    }

    private boolean hasUnusedImports(Path path) {
        var file = path.toFile();
        try {
            var in = new FileInputStream(file);
            var cu = new JavaParser()
                    .parse(in)
                    .getResult()
                    .orElse(null);
            in.close();

            assert cu != null;
            var imports = cu.getImports();
            var usedNames = new HashSet<>();

            cu.accept(new VoidVisitorAdapter<Void>() {
                @Override
                public void visit(NameExpr expr, Void arg) {
                    usedNames.add(expr.getNameAsString());
                    super.visit(expr, arg);
                }

                @Override
                public void visit(MethodDeclaration md, Void arg) {
                    md.getParameters().forEach(p -> usedNames.add(p.getType().asString()));
                    md.getThrownExceptions().forEach(te -> usedNames.add(te.getElementType().asString()));
                    super.visit(md, arg);
                }

                @Override
                public void visit(CatchClause catchClause, Void arg) {
                    // Capture exception names in catch blocks
                    var exceptionType = catchClause.getParameter().getType().asString();
                    usedNames.add(exceptionType);  // Mark the exception as used
                    super.visit(catchClause, arg);
                }
            }, null);

            var unusedImports = imports.stream()
                    .filter(imp -> !usedNames.contains(imp.getName().getIdentifier()))
                    .map(imp -> imp.toString().trim())
                    .toList();

            if (!unusedImports.isEmpty()) {
                getLog().warn("Unused imports in file: " + file.getPath());
                unusedImports.forEach(imp -> getLog().warn("  - " + imp));
                return true;
            } else {
                return false;
            }
        } catch (IOException ex) {
            getLog().error("Error processing file: " + file.getPath(), ex);
        }

        return false;
    }
}