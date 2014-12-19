package com.neenbedankt.gradle.androidapt

import org.gradle.api.Project
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Test

class AndroidAptPluginTest {

    @Test
    public void testRequireAndroidPlugin() {
        Project project = ProjectBuilder.builder().build()
        try {
            project.apply plugin: 'android-apt'
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
        p.apply plugin: 'com.android.application'
        p.apply plugin: 'android-apt'
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
        // FIXME assert that the test:jar task is added as a dependency
//        p.android.applicationVariants.all { v ->
//            dependencies = v.javaCompile.taskDependencies.getDependencies(v.javaCompile)
//        }
    }

    @Test
    public void testProjectandroidTestAptDependency() {
        Project root = ProjectBuilder.builder().build();
        Project testProject = ProjectBuilder.builder().withParent(root).build();
        testProject.apply plugin: 'java'
        Project p = ProjectBuilder.builder().withParent(root).build()
        p.apply plugin: 'com.android.application'
        p.apply plugin: 'android-apt'
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
                assert !v.testVariant.javaCompile.options.compilerArgs.empty
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

        p.apply plugin: 'android-unit-test'
        p.apply plugin: 'android-apt'

        p.dependencies {
            testAptCompile testProject
        }

        p.evaluate()
        println "Variants"
        println p.configurations

        // no test for actual functionality, because still unsure, how come it ever works :(

        println "Variants"
    }
}
