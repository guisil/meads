package app.meads;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

class ModulithStructureTest {

    private final ApplicationModules modules = ApplicationModules.of(MeadsApplication.class);

    @Test
    void shouldHaveValidModularStructure() {
        modules.verify();
    }

    @Test
    void shouldGenerateModuleDocumentation() {
        new Documenter(modules)
                .writeModulesAsPlantUml()
                .writeIndividualModulesAsPlantUml();
    }
}
