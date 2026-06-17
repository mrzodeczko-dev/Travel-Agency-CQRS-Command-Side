package com.rzodeczko;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

class HexagonalArchitectureTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.rzodeczko");
    }

    @Nested
    @DisplayName("Layer dependency rules")
    class LayerDependencyRules {

        @Test
        @DisplayName("Layered architecture is respected")
        void layeredArchitectureIsRespected() {
            layeredArchitecture()
                    .consideringAllDependencies()
                    .layer("Domain").definedBy("com.rzodeczko.domain..")
                    .layer("Application").definedBy("com.rzodeczko.application..")
                    .layer("Infrastructure").definedBy("com.rzodeczko.infrastructure..")
                    .layer("Presentation").definedBy("com.rzodeczko.presentation..")
                    .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Infrastructure", "Presentation")
                    .whereLayer("Application").mayOnlyBeAccessedByLayers("Infrastructure", "Presentation")
                    .whereLayer("Infrastructure").mayNotBeAccessedByAnyLayer()
                    .whereLayer("Presentation").mayNotBeAccessedByAnyLayer()
                    .check(classes);
        }

        @Test
        @DisplayName("Domain does not depend on application")
        void domainDoesNotDependOnApplication() {
            noClasses()
                    .that().resideInAPackage("com.rzodeczko.domain..")
                    .should().dependOnClassesThat().resideInAPackage("com.rzodeczko.application..")
                    .check(classes);
        }

        @Test
        @DisplayName("Domain does not depend on infrastructure")
        void domainDoesNotDependOnInfrastructure() {
            noClasses()
                    .that().resideInAPackage("com.rzodeczko.domain..")
                    .should().dependOnClassesThat().resideInAPackage("com.rzodeczko.infrastructure..")
                    .check(classes);
        }

        @Test
        @DisplayName("Domain does not depend on presentation")
        void domainDoesNotDependOnPresentation() {
            noClasses()
                    .that().resideInAPackage("com.rzodeczko.domain..")
                    .should().dependOnClassesThat().resideInAPackage("com.rzodeczko.presentation..")
                    .check(classes);
        }

        @Test
        @DisplayName("Application does not depend on infrastructure")
        void applicationDoesNotDependOnInfrastructure() {
            noClasses()
                    .that().resideInAPackage("com.rzodeczko.application..")
                    .should().dependOnClassesThat().resideInAPackage("com.rzodeczko.infrastructure..")
                    .check(classes);
        }

        @Test
        @DisplayName("Application does not depend on presentation")
        void applicationDoesNotDependOnPresentation() {
            noClasses()
                    .that().resideInAPackage("com.rzodeczko.application..")
                    .should().dependOnClassesThat().resideInAPackage("com.rzodeczko.presentation..")
                    .check(classes);
        }

        @Test
        @DisplayName("Presentation does not depend on infrastructure")
        void presentationDoesNotDependOnInfrastructure() {
            noClasses()
                    .that().resideInAPackage("com.rzodeczko.presentation..")
                    .should().dependOnClassesThat().resideInAPackage("com.rzodeczko.infrastructure..")
                    .check(classes);
        }
    }

    @Nested
    @DisplayName("Framework isolation rules")
    class FrameworkIsolationRules {

        @Test
        @DisplayName("Domain does not use Spring annotations")
        void domainDoesNotUseSpringAnnotations() {
            noClasses()
                    .that().resideInAPackage("com.rzodeczko.domain..")
                    .should().dependOnClassesThat().resideInAPackage("org.springframework..")
                    .check(classes);
        }

        @Test
        @DisplayName("Application does not use Spring annotations")
        void applicationDoesNotUseSpringAnnotations() {
            noClasses()
                    .that().resideInAPackage("com.rzodeczko.application..")
                    .should().dependOnClassesThat().resideInAPackage("org.springframework..")
                    .check(classes);
        }

        @Test
        @DisplayName("Domain does not use JPA annotations")
        void domainDoesNotUseJpaAnnotations() {
            noClasses()
                    .that().resideInAPackage("com.rzodeczko.domain..")
                    .should().dependOnClassesThat().resideInAPackage("jakarta.persistence..")
                    .check(classes);
        }

        @Test
        @DisplayName("Domain does not use Kafka classes")
        void domainDoesNotUseKafka() {
            noClasses()
                    .that().resideInAPackage("com.rzodeczko.domain..")
                    .should().dependOnClassesThat().resideInAPackage("org.apache.kafka..")
                    .check(classes);
        }
    }

    @Nested
    @DisplayName("Port rules")
    class PortRules {

        @Test
        @DisplayName("Input ports are interfaces")
        void inputPortsAreInterfaces() {
            classes()
                    .that().resideInAPackage("com.rzodeczko.application.port.in..")
                    .should().beInterfaces()
                    .check(classes);
        }

        @Test
        @DisplayName("Output ports are interfaces")
        void outputPortsAreInterfaces() {
            classes()
                    .that().resideInAPackage("com.rzodeczko.application.port.out..")
                    .should().beInterfaces()
                    .check(classes);
        }
    }
}
