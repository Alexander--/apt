package com.neenbedankt.gradle.androidapt

import org.gradle.api.Project
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Test

import java.util.concurrent.CountDownLatch

class AndroidAptExtensionTest {
    @Test
    void testVariantArguments() {
        def extension = new AndroidAptExtension()
        extension.arguments {
            manifest "test"
            manifest "test2${variant.name}"
        }
        extension.aptArguments.variant = ["name" : "dummyVariant"]
        assert extension.arguments() == ['-Amanifest=test', '-Amanifest=test2dummyVariant']
        extension.aptArguments.variant = ["name" : "dummyVariant2"]
        assert extension.arguments() == ['-Amanifest=test', '-Amanifest=test2dummyVariant2']
    }

    @Test
    void testAndroidArguments() {
        def extension = new AndroidAptExtension()
        extension.arguments {
            manifest "test"
            manifest "test2${android.name}"
        }
        extension.aptArguments.android = ["name" : "dummyVariant"]
        assert extension.arguments() == ['-Amanifest=test', '-Amanifest=test2dummyVariant']
    }

    @Test
    void testProjectArguments() {
        def extension = new AndroidAptExtension()
        extension.arguments {
            manifest "test"
            manifest "test2${project.name}"
        }
        extension.aptArguments.project = ["name" : "dummyVariant"]
        assert extension.arguments() == ['-Amanifest=test', '-Amanifest=test2dummyVariant']
    }

    @Test
    void testMissingArguments() {
        def extension = new AndroidAptExtension()
        assert extension.arguments() == []
    }
}
