---
name: add-cucumber-tests
description: This skill outlines the process for adding Cucumber tests to an existing project using the Tzatziki library based on a functional specification.
---

# Skill: BDD Test Generation with Tzatziki

This skill outlines the process for adding Cucumber tests to an existing project using the Tzatziki library based on a functional specification.

## Workflow

### 1. Requirements Analysis
* **Goal**: Ensure the functional specification is complete and understood.
* **Action**: Analyze the input provided by the user.
* **Constraint**: If any part of the requirement is ambiguous or missing, **stop** and ask the user for clarification before proceeding.

### 2. Capability Discovery
* **Goal**: Identify available testing tools and steps.
* **Action**:
    1.  Inspect the project dependencies (e.g., run `mvn dependency:tree` or check `pom.xml`) to see which `tzatziki-*` modules (http, jpa, kafka, etc.) are present.
    2.  Read **`docs/repomix.md`** to understand the specific Cucumber steps available for the installed modules.

### 3. Environment Health Check
* **Goal**: Verify that the testing environment is operational.
* **Action**:
    1.  Attempt to run a single existing Cucumber test to validate the setup.
    2.  If the test fails to execute (technical error), **stop** and ask the user for the correct command to run the tests.

### 4. Style & Pattern Analysis
* **Goal**: Maintain consistency with the existing codebase.
* **Action**: Read existing `.feature` files to understand:
    * The granularity of scenarios.
    * Naming conventions.
    * Usage of `Background`, `Tags`, and project-specific DSL.

### 5. Strategy Proposal
* **Goal**: Align with user expectations before writing code.
* **Action**:
    1.  Identify if tests should be added to an existing `.feature` file or if a new one is required.
    2.  Present a plan to the user listing:
        * The files to be created or modified.
        * A summary of the scenarios to be implemented.
    3.  **Wait** for user validation.

### 6. Implementation & Validation Loop
* **Goal**: Produce valid Gherkin code that compiles.
* **Action**:
    1.  Write the `.feature` file content using the steps identified in step 2 and the style from step 4.
    2.  **Execute the tests** using Maven.
    3.  **Analyze output**:
        * **If Syntax/Step Error** (e.g., `UndefinedStepException`, `ParseError`): Modify the Gherkin to fix the error and retry.
        * **If Assertion/Functional Error**: **Accept** the failure. This is expected in BDD as the feature implementation might not exist yet.
    4.  Repeat until the tests run and fail only on functional assertions (or pass if the feature is already there).