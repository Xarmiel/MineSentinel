# Upgrade Plan: mine-sentinel-prototype (20260929212233)

- **Generated**: 2026-09-29 16:22 -05:00
- **HEAD Branch**: N/A
- **HEAD Commit ID**: N/A

> Version control is unavailable because this project directory is not a Git repository. Upgrade changes will remain uncommitted in the working directory.

## Available Tools

**JDKs**
- JDK 17.0.20.1: `C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot\bin` (current project JDK, baseline)
- JDK 25.0.2: `C:\Program Files\Eclipse Adoptium\jdk-25.0.2.10-hotspot\bin` (target LTS, upgrade validation)

**Build Tools**
- Maven 3.9.15: `C:\Users\eduar\.maven\maven-3.9.15\bin`
- Maven helper script: `mvnw.cmd` (custom Maven locator; no `.mvn/wrapper/maven-wrapper.properties` version pin)

## Guidelines

> Note: You can add any specific guidelines or constraints for the upgrade process here if needed, bullet points are preferred.

## Options

- Working branch: N/A (version control unavailable)
- Run tests before and after the upgrade: true
- Auto-execution: true

## Upgrade Goals

- Upgrade the configured Java runtime/compiler target from Java 17 to Java 25 (latest LTS).

## Technology Stack

| Technology/Dependency | Current | Min Compatible Version | Why Incompatible |
| --------------------- | ------- | ---------------------- | ---------------- |
| Java runtime/compiler target | 17 | 25 | User requested the latest LTS |
| Spring Boot | 3.3.4 | 3.3.4 | No source-level incompatibility identified; Java 25 runtime compatibility will be verified by the full suite |
| Maven | 3.9.15 | 3.9.15 | Compatible build tool is installed |
| maven-compiler-plugin | 3.13.0 | 3.13.0 | Existing plugin delegates compilation to the configured JDK; verify Java 25 release compilation |
| maven-surefire-plugin | 3.5.6 | 3.5.6 | Existing test plugin; verify test execution on Java 25 |

## Derived Upgrades

- Set Maven's `java.version` and compiler release to 25 so Spring Boot dependency defaults and `javac` compile for the target runtime.
- Update README runtime prerequisites and the PowerShell launcher’s compiler error message so user-facing guidance matches the configured Java release.
- No Maven or wrapper upgrade is planned: Maven 3.9.15 is installed, and the project has no wrapper-pinned version.

## Impact Analysis

### Dependency Changes

| File | Dependency | Current | Action | Target | Reason |
|------|-----------|---------|--------|--------|--------|
| `pom.xml` | `java.version` | 17 | upgrade | 25 | Configure Spring Boot and Maven for the requested LTS |
| `pom.xml` | `maven-compiler-plugin` `release` and compiler argument | 17 | upgrade | 25 | Compile production and test classes with Java 25 APIs |

### Source Code Changes

No Java source changes were identified. The source scan found no internal JDK package imports, reflective access to JDK internals, or `SecurityManager` usage requiring migration.

### Configuration Changes

| File | Property/Setting | Current | Required Change | Reason |
|------|------------------|---------|-----------------|--------|
| `pom.xml` | `java.version` | 17 | Set to 25 | Align the project’s configured Java runtime with the target LTS |
| `pom.xml` | `maven-compiler-plugin` `<release>` and compiler argument | 17 | Set both to 25 | Ensure consistent Java 25 bytecode/API compilation |

### CI/CD Changes

No CI/CD or container build files were found in the project.

### Documentation and Launch Script

| File | Location | Current | Required Change | Reason |
|------|----------|---------|----------------|--------|
| `README.md` | Project description, line 3 | Java 17/26 | State Java 25 | Remove outdated and inconsistent runtime versions |
| `README.md` | Prerequisites, line 241 | JDK 17 or later; Java 21/26 compatibility | Require JDK 25 | Document the configured LTS target |
| `run.ps1` | Compilation failure message | “bytecode Java 17” | “bytecode Java 25” | Keep runtime/compile failure guidance accurate |

### Risks & Warnings

- **Spring Boot 3.3.4 on Java 25**: This older framework line was not changed because the request is limited to the Java runtime. **Mitigation**: Run clean test compilation and the full test suite on JDK 25; resolve any Java 25 runtime or test compatibility failures before completion.
- **Maven compiler-plugin Java 25 release support**: The project currently pins plugin 3.13.0. **Mitigation**: Verify Java 25 `test-compile`; upgrade only this plugin if the compiler invocation rejects release 25.
- **No Git repository**: Changes cannot be isolated in a branch or committed. **Mitigation**: Keep changes scoped to the files listed above and explicitly report the unversioned state.

## Upgrade Steps

- Step 1: Setup Environment
  - **Rationale**: Ensure the target JDK and build tool are available before baseline and target validation.
  - **Changes to Make**: None; JDK 25.0.2 and Maven 3.9.15 are already installed.
  - **Verification**: Confirm JDK 25 is detected with `appmod-list-jdks`; confirm Maven 3.9.15 is available.

- Step 2: Setup Baseline
  - **Rationale**: Establish the current Java 17 compile and test result for comparison.
  - **Changes to Make**: None.
  - **Verification**: With `JAVA_HOME` set to the installed JDK 17, run `mvnw.cmd clean compile test-compile -q` and `mvnw.cmd clean test -q`; record success/failure and test counts.

- Step 3: Configure Java 25 and Align Runtime Guidance
  - **Rationale**: Update all discovered runtime version declarations together so the project compiles and its instructions agree.
  - **Changes to Make**: Apply the `pom.xml`, `README.md`, and `run.ps1` changes listed in Impact Analysis.
  - **Verification**: With `JAVA_HOME` set to JDK 25, run `mvnw.cmd clean test-compile -q`; both production and test sources must compile.

- Step 4: CVE Validation & Fix
  - **Rationale**: Check project dependencies for known vulnerabilities after dependency resolution under the upgraded build.
  - **Changes to Make**: Scan resolved direct dependencies; upgrade only dependencies with actionable patched CVEs, preserving compatibility and existing pins.
  - **Verification**: Run the dependency CVE scan, compile after any fixes, and rescan to confirm resolution.

- Step 5: Final Validation
  - **Rationale**: Confirm the requested Java target and preserve the baseline behavior with a clean build and full tests.
  - **Changes to Make**: Resolve all Java 25 compilation/test failures and any CVEs identified in Step 4.
  - **Verification**: With JDK 25, run `mvnw.cmd clean test-compile -q` and `mvnw.cmd clean test -q`; all tests must pass or meet/exceed baseline.
