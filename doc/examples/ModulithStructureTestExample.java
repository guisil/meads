// == ModulithStructureTestExample.java ==
// Verifies modular architecture. Catches illegal cross-module dependencies.
// Already present in src/test as ModulithStructureTest.java.

package com.example.app;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

class ModulithStructureTest {

    private final ApplicationModules modules = ApplicationModules.of(Application.class);

    @Test
    void shouldHaveValidModularStructure() {
        // FAILS if:
        //   - Any module references another module's internal sub-package
        //   - Cyclic dependencies exist
        //   - Undeclared inter-module dependency
        modules.verify();
    }

    @Test
    void shouldGenerateModuleDocumentation() {
        new Documenter(modules)
                .writeModulesAsPlantUml()
                .writeIndividualModulesAsPlantUml();
    }
}

// Exists from day one. When verify() fails, fix the module code — not this test.
