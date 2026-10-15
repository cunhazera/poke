package com.app.poke.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Enforces the Dependency Rule: source code dependencies point only inward.
 * See .claude/skills/clean-architecture/SKILL.md.
 */
@AnalyzeClasses(packages = "com.app.poke", importOptions = ImportOption.DoNotIncludeTests.class)
class CleanArchitectureTest {

	private static final String BASE = "com.app.poke";

	@ArchTest
	static final ArchRule dependenciesPointInward = layeredArchitecture()
			.consideringOnlyDependenciesInLayers()
			.withOptionalLayers(true) // layers fill up story by story; empty is not a violation
			.layer("Domain").definedBy(BASE + ".domain..")
			.layer("Application").definedBy(BASE + ".application..")
			.layer("InboundAdapters").definedBy(BASE + ".adapter.in..")
			.layer("OutboundAdapters").definedBy(BASE + ".adapter.out..")
			.layer("Config").definedBy(BASE + ".config..")
			.whereLayer("Config").mayNotBeAccessedByAnyLayer()
			.whereLayer("InboundAdapters").mayOnlyBeAccessedByLayers("Config")
			.whereLayer("OutboundAdapters").mayOnlyBeAccessedByLayers("Config")
			.whereLayer("Application").mayOnlyBeAccessedByLayers("InboundAdapters", "OutboundAdapters", "Config")
			.whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "InboundAdapters", "OutboundAdapters", "Config");

	@ArchTest
	static final ArchRule innerRingsAreFrameworkFree = noClasses()
			.that().resideInAnyPackage(BASE + ".domain..", BASE + ".application..")
			.should().dependOnClassesThat().resideInAnyPackage(
					"org.springframework..",
					"jakarta.persistence..",
					"jakarta.servlet..",
					"tools.jackson..",
					"com.fasterxml.jackson..",
					"org.hibernate..")
			.because("Entities and Use Cases must not know about frameworks (Uncle Bob's Dependency Rule)")
			.allowEmptyShould(true);

	@ArchTest
	static final ArchRule inboundAdaptersUseInputPortsNotInteractors = noClasses()
			.that().resideInAPackage(BASE + ".adapter.in..")
			.should().dependOnClassesThat().resideInAnyPackage(BASE + ".application.usecase..", BASE + ".application.port.out..")
			.because("controllers must call use cases through their input boundary interfaces")
			.allowEmptyShould(true);

	@ArchTest
	static final ArchRule interactorsImplementInputPorts = classes()
			.that().resideInAPackage(BASE + ".application.usecase..")
			.and().haveSimpleNameEndingWith("Interactor")
			.should().implement(DescribedPredicate.describe(
					"an input port",
					(JavaClass c) -> c.getPackageName().startsWith(BASE + ".application.port.in")))
			.allowEmptyShould(true);

	@ArchTest
	static final ArchRule portsAreInterfaces = classes()
			.that().resideInAnyPackage(BASE + ".application.port.in..", BASE + ".application.port.out..")
			.and().haveNameMatching(".*(UseCase|Gateway)")
			.should().beInterfaces()
			.allowEmptyShould(true);
}
