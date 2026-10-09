package com.carevoice;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import jakarta.persistence.Entity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class ArchitectureTest {
    private static JavaClasses application;

    @BeforeAll
    static void importApplication() {
        application = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.carevoice");
    }

    @Test
    void controllersDoNotDependOnRepositories() {
        noClasses().that().resideInAPackage("com.carevoice.controller..")
                .should().dependOnClassesThat().resideInAPackage("com.carevoice.repository..")
                .check(application);
    }

    @Test
    void repositoriesDoNotDependOnControllersOrServices() {
        noClasses().that().resideInAPackage("com.carevoice.repository..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.carevoice.controller..",
                        "com.carevoice.service..")
                .check(application);
    }

    @Test
    void mappersDoNotDependOnRepositoriesOrServices() {
        noClasses().that().resideInAPackage("com.carevoice.mapper..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.carevoice.repository..",
                        "com.carevoice.service..")
                .check(application);
    }

    @Test
    void domainDoesNotDependOnControllersServicesOrRepositories() {
        noClasses().that().resideInAPackage("com.carevoice.domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.carevoice.controller..",
                        "com.carevoice.service..",
                        "com.carevoice.repository..")
                .check(application);
    }

    @Test
    void controllersLiveInTheControllerPackage() {
        classes().that().areAnnotatedWith(RestController.class)
                .should().resideInAPackage("com.carevoice.controller..")
                .check(application);
    }

    @Test
    void jpaRepositoriesLiveInTheRepositoryPackage() {
        classes().that().areAssignableTo(JpaRepository.class)
                .should().resideInAPackage("com.carevoice.repository..")
                .check(application);
    }

    @Test
    void entitiesLiveInTheDomainPackage() {
        classes().that().areAnnotatedWith(Entity.class)
                .should().resideInAPackage("com.carevoice.domain..")
                .check(application);
    }
}
