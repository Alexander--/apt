package com.neenbedankt.gradle.androidapt

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.ProjectConfigurationException
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.invocation.Gradle
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.api.tasks.compile.JavaCompile

class AndroidAptPlugin implements Plugin<Project> {
    void apply(Project project) {
        def variants = null;
        if (project.plugins.findPlugin("com.android.application") || project.plugins.findPlugin("android")) {
            variants = "applicationVariants";
        } else if (project.plugins.findPlugin("com.android.library") || project.plugins.findPlugin("android-library")) {
            variants = "libraryVariants";
        } else {
            throw new ProjectConfigurationException("The android or android-library plugin must be applied to the project", null)
        }

        def aptConfiguration = project.configurations.create('apt').extendsFrom(project.configurations.compile)
        def aptTestConfiguration = project.configurations.create('androidTestApt').extendsFrom(project.configurations.androidTestCompile)
        def unitTestConfiguration = project.configurations.findByName('testCompile')
        if (unitTestConfiguration) {
            unitTestConfiguration = project.configurations.create('testAptCompile').extendsFrom(unitTestConfiguration)
        }

        project.extensions.create("apt", AndroidAptExtension)

        project.afterEvaluate {
            project.android[variants].all { variant ->

                if (unitTestConfiguration) {
                    configureVariant(project, variant, aptConfiguration, unitTestConfiguration)
                } else {
                    configureVariant(project, variant, aptConfiguration)
                }

                if (variant.testVariant) {
                    configureVariant(project, variant.testVariant, aptTestConfiguration)
                }
            }
        }
    }

    static void configureVariant(def project, def variant, Configuration aptConfiguration, Configuration... auxiliaryConfigurations) {

        if (aptConfiguration.empty) {
            // no apt dependencies, nothing to see here
            return;
        }

        // This ensures, that generated code directory exists and can be seen in IDE
        def aptOutputDir = project.file(new File(project.buildDir, "generated/source/apt"))
        def aptOutput = new File(aptOutputDir, variant.dirName)

        variant.javaCompile.doFirst {
            aptOutput.mkdirs()
        }

        variant.addJavaSourceFoldersToModel(aptOutput);

        // construct compiler arguments
        project.apt.aptArguments.variant = variant
        project.apt.aptArguments.project = project
        project.apt.aptArguments.android = project.android

        def processorPath = aptConfiguration
        auxiliaryConfigurations.each { auxiliaryConfiguration ->
            processorPath += auxiliaryConfiguration
        }

        def processors = [] as Set

        if (project.apt.excluded || project.apt.included) {
            def urls = [] as Set<URL>

            for (File pathElement:processorPath) {
                // TODO: is there a better way?
                if (pathElement.name.endsWith(".jar"))
                    urls.add(pathElement.toURI().toURL())
            }

            def tsl = TrimmedServiceLoader.load(new URLClassLoader(urls.toArray(new URL[urls.size()])))
            for (String processor:tsl) {
                if (!project.apt.excluded?.contains(processor)) {
                    processors.add(processor)
                }
            }
            processors.addAll(project.apt.included)
        }

        processorPath = processorPath.getAsPath()

        def bonusArgs = [
                '-processorpath', processorPath,
                '-s', aptOutput
        ]

        if (!processors.empty) {
            bonusArgs += '-processor'
            bonusArgs += processors.join(',')
        }

        bonusArgs += project.apt.arguments()

        // step 1: add options to the main compile task
        variant.javaCompile.options.compilerArgs += bonusArgs

        // step 2: find every JavaCompile, that may run after variant compile task during this invocation
        // Let's assume, that those JavaCompile tasks too need our annotation processing
        // (there really shouldn't be any side-affects, he-he-he-he)
        project.gradle.taskGraph.whenReady {
            project.tasks.withType(JavaCompile).each { JavaCompile compileTask ->
                // TODO: do not handle test variants twice
                if (compileTask.taskDependencies.getDependencies(compileTask).contains(variant.javaCompile)) {
                    compileTask.options.compilerArgs += bonusArgs
                }
            }
        }

        // Get all dependencies of our configurations (see below)
        // Need new Set here, because FilteredSet is immutable
        def projectDependencies = new HashSet()
        projectDependencies.addAll(aptConfiguration.allDependencies.withType(ProjectDependency))
        auxiliaryConfigurations.each { auxiliaryConfiguration ->
            projectDependencies.addAll(auxiliaryConfiguration.allDependencies.withType(ProjectDependency))
        }

        // There must be a better way, but for now grab the tasks that produce some kind of archive and make sure those
        // run before this javaCompile. Packaging makes sure that processor meta data is on the classpath
        projectDependencies.each { ProjectDependency p ->
            def archiveTasks = p.dependencyProject.tasks.withType(AbstractArchiveTask)
            archiveTasks.each { t -> variant.javaCompile.dependsOn t.path }
        }
    }
}
