package com.neenbedankt.gradle.androidapt

class AndroidAptExtension {
    final def aptArguments = new AptArguments()

    private def argsClosure;

    def excluded;
    def included;

    def arguments(Closure closure) {
        closure.resolveStrategy = Closure.DELEGATE_FIRST
        closure.delegate = aptArguments;
        argsClosure = closure;
    }

    def setExcluded(String... excluded) {
        this.excluded = excluded;
    }

    def setIncluded(String... included) {
        this.included = included;
    }

    def arguments() {
        aptArguments.arguments.clear();
        if (argsClosure) {
            argsClosure()
        }
        return aptArguments.arguments
    }
}
