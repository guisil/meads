# Skill: Creating a New Spring Modulith Module

## When to activate
When the user asks to create a new domain module / bounded context.

## Steps

1. Create the package: `src/main/java/com/example/app/<modulename>/`

2. Create `package-info.java`:
   ```java
   @org.springframework.modulith.ApplicationModule(
       allowedDependencies = {"shared"}
   )
   package com.example.app.<modulename>;
   ```

3. Create `internal/` sub-package with `.gitkeep`.

4. Run `ModulithStructureTest`:
   `mvn test -Dtest=ModulithStructureTest -Dsurefire.useFile=false`

5. Write module integration test:
   ```java
   @ApplicationModuleTest
   @Import(TestcontainersConfiguration.class)
   class <ModuleName>ModuleIntegrationTest {
       @Test
       void shouldBootstrap<ModuleName>Module() {
           // passes if context starts correctly
       }
   }
   ```

6. Begin TDD for the module's first feature.
