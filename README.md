# Code Cleanup Maven Plugin

A custom Maven plugin that performs static code checks on Java source files to enforce code quality and consistency.
This plugin can detect unused imports, check line lengths, verify newline at the end of file, locate TODO comments,
and validate method parameter limits. If any violations are found, the plugin throws a `MojoExecutionException`, 
which can fail the build.

---

## Plugin Goal

**`cleanup:cleanup`** - Scans Java source files in the configured source directory and reports issues based on enabled rules.

---

## Configuration Parameters

| Parameter              | Property              | Type     | Default                    | Description |
|------------------------|------------------------|----------|----------------------------|-------------|
| `sourceDir`            | `sourceDir`            | File     | `${project.basedir}/src`   | Root directory containing Java source files to scan. |
| `checkUnusedImports`   | `checkUnusedImports`   | boolean  | `true`                     | If true, unused imports are reported. |
| `maxLineLength`        | `maxLineLength`        | int      | `-1`                       | Max number of characters per line. Lines exceeding this will be flagged. |
| `checkNewLineAtEnd`    | `checkNewLineAtEnd`    | boolean  | `true`                     | If true, missing newline at the end of file will be flagged. |
| `checkTODOs`           | `checkTODOs`           | boolean  | `true`                     | If true, lines containing 'TODO' comments are flagged. |
| `maxMethodParameters`  | `maxMethodParameters`  | int      | `-1`                       | Max number of parameters allowed per method. |

---

## Usage

Add the following to your `pom.xml`:

```xml
<build>
  <plugins>
    <plugin>
      <groupId>com.example</groupId>
      <artifactId>cleanup</artifactId>
      <version>1.0-SNAPSHOT</version>
      <executions>
        <execution>
          <goals>
            <goal>cleanup</goal>
          </goals>
        </execution>
      </executions>
      <configuration>
        <sourceDir>${project.basedir}/src</sourceDir>
        <checkUnusedImports>true</checkUnusedImports>
        <maxLineLength>120</maxLineLength>
        <checkNewLineAtEnd>true</checkNewLineAtEnd>
        <checkTODOs>true</checkTODOs>
        <maxMethodParameters>4</maxMethodParameters>
      </configuration>
    </plugin>
  </plugins>
</build>
```

If a code style rule's parameter is omitted from the configuration, that rule will not be enforced.

### Run Plugin

```bash
mvn com.example:code-cleanup-maven-plugin:cleanup
```
The plugin can also be executed during the build lifecycle by running `mvn clean install`, as long as it is properly 
configured in the project's `pom.xml`.

---

## Features

- Unused Import Detection using JavaParser
- Line Length Enforcement
- Newline at EOF Check
- TODO Comments Detection
- Method Parameter Limit Validation

---

## Example Output

```
[WARNING] Max line length exceeded found in file: /src/MyClass.java at line 42
[WARNING] TODO found in file: /src/MyClass.java at line 12: // TODO: Refactor this
[WARNING] Method 'doSomething' on line 55 in file /src/MyClass.java has 6 parameters (max allowed: 4)
[WARNING] Unused imports in file: /src/MyClass.java
  - import java.util.List;
```

---