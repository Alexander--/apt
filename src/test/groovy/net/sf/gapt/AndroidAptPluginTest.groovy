package net.sf.gapt

import java.util.concurrent.CountDownLatch;

import org.gradle.api.Project
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Test

class AndroidAptPluginTest {

    @Test
    public void testRequireAndroidPlugin() {
        Project project = ProjectBuilder.builder().build()
        try {
            project.apply plugin: 'net.sf.gapt'
            fail();
        } catch (expected) {
        }
    }

    @Test
    public void testProjectAptDependency() {
        Project root = ProjectBuilder.builder().build();
        Project testProject = ProjectBuilder.builder().withParent(root).build();
        testProject.apply plugin: 'java'
        Project p = ProjectBuilder.builder().withParent(root).build()

        p.apply plugin: 'android-sdk-manager'
        p.apply plugin: 'com.android.application'
        p.apply plugin: 'net.sf.gapt'

        p.dependencies {
            apt testProject
        }
        p.android {
            compileSdkVersion 21
            buildToolsVersion "21.1.1"

            defaultConfig {
                minSdkVersion 14
                targetSdkVersion 21
                versionCode 1
                versionName "1.0"
            }
        }
        p.evaluate()

        p.android.applicationVariants.all { v ->
            assert AptPlugin.insertArgTaskName(v.javaCompile) in p.tasks.withType(AdjustArgumentsTask).name
        }
    }

    @Test
    public void testProjectandroidTestAptDependency() {
        Project root = ProjectBuilder.builder().build();
        Project testProject = ProjectBuilder.builder().withParent(root).build();
        testProject.apply plugin: 'java'
        Project p = ProjectBuilder.builder().withParent(root).build()

        p.apply plugin: 'android-sdk-manager'
        p.apply plugin: 'com.android.application'
        p.apply plugin: 'net.sf.gapt'

        p.repositories {
            mavenCentral()
        }
        p.dependencies {
            androidTestApt testProject
        }
        p.android {
            compileSdkVersion 21
            buildToolsVersion "21.1.1"

            defaultConfig {
                minSdkVersion 14
                targetSdkVersion 21
                versionCode 1
                versionName "1.0"
            }
        }
        p.evaluate()
        println "Variants"
        println p.configurations
        p.android.applicationVariants.all { v ->
            if (v.testVariant) {
                assert AptPlugin.insertArgTaskName(v.testVariant.javaCompile) in
                        p.tasks.withType(AdjustArgumentsTask).name
            }
        }
        println "Variants"
    }

    @Test
    public void unitTestOk() {
        Project root = ProjectBuilder.builder().build();
        Project testProject = ProjectBuilder.builder().withParent(root).build();
        testProject.apply plugin: 'java'
        Project p = ProjectBuilder.builder().withParent(root).build()

        p.apply plugin: 'android-sdk-manager'
        p.apply plugin: 'com.android.application'

        p.repositories {
            mavenCentral()
        }
        p.android {
            compileSdkVersion 21
            buildToolsVersion "21.1.1"

            defaultConfig {
                applicationId "com.example"
                minSdkVersion 14
                targetSdkVersion 21
                versionCode 1
                versionName "1.0"
            }
        }

        p.apply plugin: 'net.sf.gapt'

        p.dependencies {
            testAptCompile testProject
        }

        def latch = new CountDownLatch(1)
        p.gradle.taskGraph.whenReady {
            p.android.applicationVariants.all { v ->
                p.tasks.withType(JavaCompile.class).each { aTask ->
                    if (aTask.taskDependencies.getDependencies(aTask).contains(variant.javaCompile)) {
                        assert !aTask.javaCompile.options.compilerArgs.empty
                    }
                }
                latch.countDown()
            }
        }

        p.evaluate()

        println "Variants"
        println p.configurations
        println "Variants"

        latch.countDown()
    }

    @Test
    public void javaExcludeIncludeOk() {
        Project root = ProjectBuilder.builder().build();
        Project p = ProjectBuilder.builder().withParent(root).build()

        p.apply plugin: 'java'
        p.repositories {
            mavenCentral()
        }

        p.apply plugin: 'net.sf.gapt'

        p.dependencies {
            // any annotation processor with ServiceLocator definition files would do
            apt 'com.jakewharton:butterknife:5.1.2'
            // anything without ServiceLocator definition files would do
            apt 'org.checkerframework:checker:1.8.9'
        }

        p.apt {
            excluded = [ 'butterknife.internal.ButterKnifeProcessor' ]
            included = [ 'org.checkerframework.checker.fenum.FenumChecker' ]
        }

        p.evaluate()

        assert p.compileJava.options.compilerArgs
                .contains('org.checkerframework.checker.fenum.FenumChecker')
    }
}
