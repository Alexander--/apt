package com.neenbedankt.gradle.androidapt

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.ProjectConfigurationException
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.invocation.Gradle
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.plugins.ide.idea.IdeaPlugin

class AndroidAptPlugin implements Plugin<Project> {
    void apply(Project project) {
        def variants = null;
        if (project.plugins.findPlugin('com.android.application') || project.plugins.findPlugin('android')) {
            variants = 'applicationVariants';
        } else if (project.plugins.findPlugin('com.android.library') || project.plugins.findPlugin('android-library')) {
            variants = 'libraryVariant';
        } else if (!project.plugins.findPlugin('java')) {
            throw new ProjectConfigurationException("'java', 'android' or 'android-library' plugin must be applied to the project", null)
        }

        project.extensions.create('apt', AndroidAptExtension)

        def aptConfiguration = project.configurations.create('apt').extendsFrom(project.configurations.compile)
        def unitTestConfiguration = project.configurations.findByName('testCompile')
        if (unitTestConfiguration) {
            unitTestConfiguration = project.configurations.create('testAptCompile').extendsFrom(unitTestConfiguration)
        }
        def androidTestConfiguration;
        if (variants) {
            androidTestConfiguration = project.configurations.create('androidTestApt').extendsFrom(project.configurations.androidTestCompile)
        }

        project.afterEvaluate {
            if (variants) {
                project.android[variants].all { variant ->
                    if (unitTestConfiguration) {
                        configureAndroid(project, variant, aptConfiguration, unitTestConfiguration)
                    } else {
                        configureAndroid(project, variant, aptConfiguration)
                    }

                    if (variant.testVariant) {
                        configureAndroid(project, variant.testVariant, androidTestConfiguration)
                    }
                }
            } else {
                if (unitTestConfiguration) {
                    configureJava(project, aptConfiguration, unitTestConfiguration)
                } else {
                    configureJava(project, aptConfiguration)
                }
            }
        }
    }

    static void configureJava(Project project, Configuration aptConf, Configuration... auxConf) {
        // This ensures, that generated code directory exists and can be seen in IDE
        // $buildDir is excluded from pure java projects, so instead create generated source root
        // ourselves and mark it as such for the IDE
        File aptOutput = project.file(new File(project.projectDir, 'src/main/java-generated'))
        File aptOutputForConf  = project.file(new File(project.projectDir, 'src/test/java-generated'))

        // TODO: do we want generated classes to be visible?
        //def aptClassesOutput = project.file(new File(project.buildDir, "generated/classes"))

        project.sourceSets.main.java.srcDirs += aptOutput
        project.sourceSets.test.java.srcDirs += aptOutputForConf

        project.plugins.withType(IdeaPlugin) {
            project.idea.module {
                generatedSourceDirs += aptOutput
                generatedSourceDirs += aptOutputForConf
            }
        }

        project.tasks.create(name: 'cleanAptGeneratedSources', type: Delete) {
            delete aptOutput
            delete aptOutputForConf
        }

        project.tasks.clean.dependsOn 'cleanAptGeneratedSources'

        // process main and test compile tasks
        JavaCompile compileTask = project.compileJava;
        compileTask.source = compileTask.source.filter {
            !it.path.startsWith(aptOutput.path)
        }

        JavaCompile testCompileTask = project.compileTestJava
        testCompileTask.source = testCompileTask.source.filter {
            !it.path.startsWith(aptOutputForConf.path)
        }

        configureJavaCompile(project, aptOutput, compileTask, aptConf)
        configureJavaCompile(project, aptOutputForConf, testCompileTask, aptConf, auxConf)
    }

    static void configureAndroid(Project project, def variant, Configuration aptConf, Configuration... auxConf) {
        // This ensures, that generated code directory exists and can be seen in IDE
        // android projects store their generated code in generated/source
        def aptOutputDir = project.file(new File(project.buildDir, 'generated/source/apt'))
        def aptOutput = new File(aptOutputDir, variant.dirName)

        // TODO: do we want generated classes to be visible?
        //aptOutputDir = project.file(new File(project.buildDir, "generated/classes/apt"))
        //def aptClassesOutput = new File(aptOutputDir, variant.dirName)

        variant.addJavaSourceFoldersToModel(aptOutput);

        // construct compiler arguments
        project.apt.aptArguments.variant = variant
        project.apt.aptArguments.android = project.android

        // process main compile task
        JavaCompile compileTask = variant.javaCompile;
        configureJavaCompile(project, aptOutput, compileTask, aptConf, auxConf)
    }

    static void configureJavaCompile(Project project,
                                     File aptOutput,
                                     JavaCompile jcTask,
                                     Configuration aptConfiguration,
                                     Configuration... auxiliaryConfigurations) {
        if (!auxiliaryConfigurations && aptConfiguration.empty) {
            // no apt dependencies, nothing to see here
            return;
        }

        jcTask.doFirst {
            aptOutput.mkdirs()
        }

        project.apt.aptArguments.project = project

        def processorPath = aptConfiguration
        auxiliaryConfigurations.each { auxiliaryConfiguration ->
            processorPath += auxiliaryConfiguration
        }

        def processors = [] as Set

        if (project.apt.excluded || project.apt.included) {
            def urls = [] as Set<URL>

            for (File pathElement:processorPath) {
                // TODO: is there a better way?
                if (pathElement.name.endsWith('.jar'))
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
        jcTask.options.compilerArgs += bonusArgs

        // step 2: find every JavaCompile, that may run after variant compile task during this invocation
        // Let's assume, that those JavaCompile tasks too need our annotation processing
        // (there really shouldn't be any side-affects, he-he-he-he)
        project.gradle.taskGraph.whenReady {
            project.tasks.withType(JavaCompile).each { JavaCompile compileTask ->
                // TODO: do not handle test variants twice
                if (compileTask.taskDependencies.getDependencies(compileTask).contains(jcTask)) {
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
            archiveTasks.each { t -> jcTask.dependsOn t.path }
        }
    }
}
