[![Build Status](https://travis-ci.org/Alexander--/apt.svg?branch=fork-beginning)](https://travis-ci.org/Alexander--/apt)

[![Download](https://api.bintray.com/packages/alexanderr/maven/gradle-apt/images/download.svg)](https://bintray.com/alexanderr/maven/gradle-apt/_latestVersion)

What is this?
---------------

The Gradle apt plugin assists in working with Java [annotation processors][1]. It has two primary purposes:

* Allow to configure a compile time only annotation processor as a dependency, not including the artifact in the final APK or library;
* Set up the source paths so that code that is generated from the annotation processor is correctly picked up by IDEs.

Additionally, it works around various corner cases, that may prevent annotation processors from working (such as when
you bundle an annotation processor as `project` depenency).

This plugin requires one of `android`, `android-library` or `java` plugins to be configured on your project. Version 1.x of Androd plugin
is supported, but older version may work too.

Including and using the plugin in your build script
---------------------------------------------------

Add the following to your build script to use the plugin:

```groovy
buildscript {
    repositories {
      mavenCentral()
    }
    dependencies {
        // replace with the current version of the Android plugin
        classpath 'com.android.tools.build:gradle:${andrpodPluginVersion}'
        // the latest version of the Gradle apt plugin
        classpath 'net.sf.gapt:gapt:${gradleAptVersion}'
    }
}

apply plugin: 'com.android.application' // or apply plugin: 'java'
apply plugin: 'net.sf.gapt' // apply after other plugins to minimize conflicts
```

New Gradle 2.1.+ notation should work too:

```groovy
...

plugins {
    id 'java'
    id 'net.sf.gapt' version '${gradleAptVersion}'
}
```

Note, that support for plain java project support is still in developement, so please report an issue if some
of your plugins happens to be incompatible with it.

Passing processor arguments
---------------------------

Some annotation processor may require to pass custom arguments, you can use `apt.arguments` for that purpose.
For instance AndroidAnnotations needs the following configuration:

```groovy
apt {
    arguments {
            resourcePackageName android.defaultConfig.packageName
            androidManifestFile variant.outputs[0].processResources.manifestFile
    }
}
```

Make sure to configure `packageName` in the `android` `defaultConfig` block for this purpose.
The arguments are processed for each variant when the compiler is configured. From this closure you can reference `android`, `project` and `variant` for the current variant.

Configurating a compiler style dependency
-----------------------------------------

Annotation processors generally have an API and a processor that generates code that is used by the API. Depending on the project the processor and the API might be split up in separate dependencies. For 
example, [Dagger][2] uses two artifacts called _dagger-compiler_ and _dagger_. The compiler artifact is only used during compilation, while the main _dagger_ artifact is required at runtime.

To aid in configuring this dependency, the plugin adds a Gradle [configuration][3] named **apt** that can be used like this:

```groovy
dependencies {
 apt 'com.squareup.dagger:dagger-compiler:1.1.0'
 compile 'com.squareup.dagger:dagger:1.1.0'
}
```

Note that you should often be able to use the `provided` configuration for API artefacts:

```groovy
dependencies {
 apt 'com.github.hamsterksu:android-annotatedsql-processor:1.10.3'
 provided 'com.github.hamsterksu:android-annotatedsql-api:1.10.3'
}
```

This is typically the case when an API consists from annotation only, see also [https://stackoverflow.com/q/3567413](this Stack Overflow quesion).

Tests support
--------------------------------------------

To perform annotation processing in Android integration tests use the `androidTestApt` configuration:

```groovy
dependencies {
 androidTestApt 'com.github.frankiesardo:android-auto-value-processor:0.1'
 androidTestCompile 'com.github.frankiesardo:android-auto-value:0.1'
}
```

Unit tests are supported by testAptCompile configuration, both for Java and Android projects (in later case you may need recent version of `android` plugin
or additional plugins, such as [android-unit-test][4]):

```groovy
dependencies {
 testAptCompile 'com.google.dagger:dagger-compiler:2.+'
 testCompile 'com.google.dagger:dagger:2.+'
}
```

Generated code placement
--------------------------------------------

The generated code is in `buid/build/generated/source/apt` for Android projects and
`generated` and `generated-test` for Java projects. This code is removed during clean task,
and sometimes you have to perform full rebuild to avoid errors (the reason for that is currently being investigated).

Configuration of other annotation processors
--------------------------------------------

For annotation processors that include the API and processor in one artifact, there's no special setup. You just add the artifact to the _compile_ configuration like usual and everything will work like normal. Additionally, if code that is generated by the processor is to be referenced in your own code, Android Studio will now correctly reference that code and resolve references to it.

If you want to include processors, that aren't declared in jar's `META-INF` (such as Checker Framework processors), use `included`, for exclusion of processors use `excluded`:

```
apt {
 ...

 included = [ 'org.checkerframework.checker.fenum.FenumChecker' ]

 ...
}
```

You can use both, one or none.

History & Credits
---------------

This plugin is a fork of the [android-apt][5]. It was created due to low responsiveness and lack of developement incetive on part of initial maintainer. Here is original "credits" text from
android-apt README:


This plugin is based on a [script][6] that I've been using for some time which is the result of [this post on Google+][7] and [this post on StackOverflow.com][8].
Variations of the SO post and my gists have been floating around for a while on the interwebs. That, and the fact that including scripts is a bit inconvenient pushed me to create this plugin.


License
-------

This plugin is released in the [public domain][9]. Feel free to use and adapt as you like. Use [Guthub issue tracker][10] to report bugs and request new features.

[1]:http://docs.oracle.com/javase/7/docs/technotes/guides/apt/
[2]:http://square.github.io/dagger
[3]:http://www.gradle.org/docs/current/userguide/artifact_dependencies_tutorial.html
[4]:https://github.com/JCAndKSolutions/android-unit-test
[5]:https://bitbucket.org/hvisser/android-apt/
[6]:https://bitbucket.org/qbusict/android-gradle-scripts/src/686ce2301245ab1f0e6a32fb20b4d246ef742223/annotations.groovy?at=default
[7]:https://plus.google.com/+HugoVisser/posts/VtGYV8RHwmo
[8]:http://stackoverflow.com/questions/16683944/androidannotations-nothing-generated-empty-activity
[9]:http://unlicense.org/
[10]:https://github.com/Alexander--/apt/issues
