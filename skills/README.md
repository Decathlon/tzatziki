# Tzatziki Skills

## Available Skills

### `add-cucumber-tests`

Generates Tzatziki-based Cucumber BDD tests (`.feature` files) from a functional specification.

Use this skill whenever you want to:
- Write Cucumber/BDD tests for a Java or Spring application
- Generate `.feature` files from a functional description
- Get step definitions for HTTP, JPA, Kafka, MongoDB, OpenSearch, Logback, or MCP modules

The skill knows every legal Tzatziki step pattern, follows a structured validation workflow, and will run the generated tests to ensure zero undefined-step errors before considering the task done.

## Installation

Install all skills from this repository into your agent environment with:

```bash
npx skills add https://github.com/Decathlon/tzatziki
```

## Refreshing Reference Docs

The skill references are auto-generated from the Tzatziki source code using [repomix](https://github.com/yamadashy/repomix).
To regenerate them after source changes:

```bash
# Install repomix if needed
npm install -g repomix

# Regenerate all module references
./skills/add-cucumber-tests/repomix/generate-skill-references.sh

# Regenerate specific modules only
./skills/add-cucumber-tests/repomix/generate-skill-references.sh core http spring
```
