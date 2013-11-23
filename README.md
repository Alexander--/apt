What is this?
---------------
The android-apt plugin assists in working with annotation processors in combination with Android Studio. It has two purposes:

* Allow to configure a compile time only annotation processor as a dependency, not including the actifact in the final APK
* Set up the source paths so that code that is generated from the annotation processor is correctly picked up by Android Studio.

This plugin requires the android plugin to be configured on your project.

Including and using the plugin in your build script
---------------------------------------------------
Add the following to your build script to use the plugin:
```
#!groovy
buildscript {
    repositories {
      mavenCentral()
    }
    dependencies {
        classpath 'com.android.tools.build:gradle:0.6+'
        classpath 'com.neenbedankt.gradle.plugins:android-apt:1.0'
    }
}

apply plugin: 'android'
apply plugin: 'android-apt'
```


Configurating a compiler style dependency
-----------------------------------------
Annotation processors generally have a API and a processor that generates code that is used by the API. Depending on the project the processor and the API might be split up in separate dependencies. For example, [Dagger][1] uses two artifacts called _dagger-compiler_ and _dagger_. The compiler artifact is only used during compilation, while the main _dagger_ artifact is required at runtime.

To aid in configurating this dependency, the plugin adds a Gradle [configuration][2] named **apt** that can be used like this:

```
#!groovy
dependencies {
 apt 'com.squareup.dagger:dagger-compiler:1.1.0'
 compile 'com.squareup.dagger:dagger:1.1.0'
}
```

Configuration of other annotation processors
--------------------------------------------
For annotation processors that include the API and processor in one artifact, there's no special setup. You just add the artifact to the _compile_ configuration like usual and everything will work like normal. Additionally, if code that is generated by the processor is to be referenced in your own code, Android Studio will now correctly reference that code and resolve references to it.

[1]:http://square.github.io/dagger
[2]:http://www.gradle.org/docs/current/userguide/artifact_dependencies_tutorial.html

License
-------
This plugin is created by Hugo Visser and released in the [public domain][3]. Feel free to use and adapt as you like.
To get in touch, hit me up on [Twitter][4] or [Google Plus][5].

[3]:http://unlicense.org/
[4]:https://twitter.com/botteaap
[5]:https://google.com/+hugovisser