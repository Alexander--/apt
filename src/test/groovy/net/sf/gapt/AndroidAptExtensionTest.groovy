package net.sf.gapt

import org.junit.Test

class AndroidAptExtensionTest {
    @Test
    void testVariantArguments() {
        def extension = new AptExtension()
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
        def extension = new AptExtension()
        extension.arguments {
            manifest "test"
            manifest "test2${android.name}"
        }
        extension.aptArguments.android = ["name" : "dummyVariant"]
        assert extension.arguments() == ['-Amanifest=test', '-Amanifest=test2dummyVariant']
    }

    @Test
    void testProjectArguments() {
        def extension = new AptExtension()
        extension.arguments {
            manifest "test"
            manifest "test2${project.name}"
        }
        extension.aptArguments.project = ["name" : "dummyVariant"]
        assert extension.arguments() == ['-Amanifest=test', '-Amanifest=test2dummyVariant']
    }

    @Test
    void testMissingArguments() {
        def extension = new AptExtension()
        assert extension.arguments() == []
    }
}
