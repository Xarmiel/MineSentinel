# Java Runtime Upgrade Summary

## Outcome

Upgraded MineSentinel from Java 17 to Java 25 LTS. Maven now compiles production and test code with Java 25.

## Changes

- Set the Java version, compiler release, and compiler argument to 25 in `pom.xml`.
- Updated Java prerequisites and project description in `README.md`.
- Updated the Java target in the `run.ps1` compilation error message.
- Upgraded PostgreSQL JDBC from 42.7.4 to 42.7.12 and Spring Security Crypto from 6.3.3 to 6.3.8 to address reported CVEs.

## Validation

- Baseline on Java 17: compilation passed; 34/34 tests passed.
- Final on Java 25: `mvn.cmd clean verify -Djacoco.skip=false -q` succeeded; 34/34 tests passed.
- CVE re-scan: no known CVEs in the scanned direct dependencies.
- Test coverage metrics are unavailable because no JaCoCo or other coverage plugin is configured.

## Risks and Notes

- Java 25 tests emit a deprecated `sun.misc.Unsafe` warning from Byte Buddy 1.14.19; the application source scan found no JDK internal API usage, and the tests pass.
- The workspace is not a Git repository, so changes were not branched or committed.
