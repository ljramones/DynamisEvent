# Repository Guidelines

## Project Structure & Module Organization
This repository is currently minimal and contains documentation only:
- `README.md`: architecture, API contracts, and usage notes for `DynamisEvent`.
- Future Java sources should follow standard Maven/Gradle layout:
  - `src/main/java/...` for implementation
  - `src/test/java/...` for tests
  - `src/main/resources/...` for non-code assets

Keep package names under `org.dynamisengine.event` (or a clearly aligned module path) and group code by bus concern (builder, dispatch, metrics, dead-letter handling).

## Build, Test, and Development Commands
No build wrapper (`mvnw`/`gradlew`) is committed yet. When adding runtime code, also add the corresponding build files and use documented commands, for example:
- `mvn clean verify`: compile and run tests.
- `mvn test`: run unit tests only.
- `mvn -q package`: produce an artifact with reduced log noise.

If Gradle is adopted instead, document equivalent `./gradlew build` and `./gradlew test` commands in `README.md`.

## Coding Style & Naming Conventions
- Language target: Java 25+ (per `README.md`).
- Indentation: 4 spaces, no tabs.
- Types: `PascalCase` (`AsyncEventBus`), methods/fields: `camelCase`, constants: `UPPER_SNAKE_CASE`.
- Event classes should be explicit and descriptive (for example, `CollisionEvent`, `StateChangedEvent`).
- Prefer immutable event payloads (records where appropriate).

Run the project formatter/linter once configured; do not mix formatting-only changes with behavioral changes in the same commit unless necessary.

## Testing Guidelines
The test framework is not yet committed. When adding tests:
- Place tests in `src/test/java`.
- Name test classes `*Test` (unit) or `*IT` (integration).
- Cover ordering, subscription lifecycle, async dispatch behavior, and dead-letter routing.
- Include deterministic tests for priority handling and unsubscribe semantics.

## Commit & Pull Request Guidelines
Current history uses short, imperative-style subjects (`initial`, `README.md`). Keep commit messages concise and scoped:
- Example: `add async bus dead-letter metric`
- Keep the first line under ~72 characters.

For pull requests, include:
- What changed and why.
- Any behavior/API impact.
- Test evidence (command + result summary).
- Linked issue/task when available.
