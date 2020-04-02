![experimental](https://img.shields.io/badge/stability-experimental-brightorange.svg)

# Consolidate Gradle Plugin

Consolidate subprojects in a single project when creating an artifact while still keeping
third party dependencies separate.

Publish libraries for projects sharing a codebase without having to publish intermediate
shared libraries.

None of the mess of assembly/shading.

## History

This tool grew out of usage from the [jib](https://github.com/GoogleContainerTools/jib) project
as a way to share business logic between maven and gradle libraries without having to publish
our shared libraries.

## Usage

### Prepare environment

1. Clone the project
1. Install the plugin locally
    ```
    $ ./gradlew publish
    ```
1. Add `mavenLocal()` as a plugin source in your `settings.gradle`
    ```
    pluginManagement {
      repositories {
        mavenLocal()
        gradlePluginPortal()
      }
    }
    ```

### Configure your project

1. Add the plugin
    ```
    plugins {
      id 'com.loosebazooka.consolidate' version '0.0.1-SNAPSHOT'
     ...
    ```
1. To include a subproject use `consolidate` instead of `implementation`
    ```
    dependencies {
      consolidate project(':my-lib')
      // implementation project(':my-lib')
      ...
    }
    ```

### IDE support

1. You should be able to import your project into your IDE (eclipse, intellij) as before
