/*
 * Copyright 2020 Google LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.loosebazooka.gradle.consolidate;

import java.util.ArrayList;
import java.util.List;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetOutput;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.jvm.tasks.Jar;
import org.gradle.plugin.devel.tasks.PluginUnderTestMetadata;

public class ConsolidatePlugin implements Plugin<Project> {

  public static final String CONSOLIDATE_CONFIGURATION = "consolidate";
  private List<Project> consolidatedProject = new ArrayList<>();

  @Override
  public void apply(Project project) {
    createConfiguration(project);
    createEnsureNoProjectDependenciesTask(project);
  }

  private void createConfiguration(Project project) {
    Configuration configuration =
        project.getConfigurations().create(CONSOLIDATE_CONFIGURATION);

    // each time a dependency is added make sure we process it
    configuration
        .getDependencies()
        .whenObjectAdded(
            dependency -> {
              ProjectDependency projectDependency = verifyProjectDependency(dependency);
              verifyProjectDependencyUsesDefaultConfiguration(projectDependency);

              Project dependencyProject = projectDependency.getDependencyProject();

              project.evaluationDependsOn(dependencyProject.getPath());

              SourceSetOutput dependencyProjectMainOutput =
                  dependencyProject
                      .getConvention()
                      .getPlugin(JavaPluginConvention.class)
                      .getSourceSets()
                      .getByName(SourceSet.MAIN_SOURCE_SET_NAME)
                      .getOutput();

              DependencyHandler projectDependencies = project.getDependencies();

              projectDependencies.add(JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME, projectDependency);
              projectDependencies.add(
                  JavaPlugin.TEST_IMPLEMENTATION_CONFIGURATION_NAME, dependencyProjectMainOutput);

              dependencyProject
                  .getConfigurations()
                  .getByName(JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME)
                  .getDependencies()
                  .forEach(
                      dependency1 ->
                          projectDependencies.add(
                              JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME, dependency1));
              Configuration sourceProjectApiConfig =
                  dependencyProject.getConfigurations().findByName(JavaPlugin.API_CONFIGURATION_NAME);
              if (sourceProjectApiConfig != null) {
                sourceProjectApiConfig
                    .getDependencies()
                    .forEach(
                        dependency1 ->
                            projectDependencies.add(
                                JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME, dependency1));
              }

              consolidatedProject.add(dependencyProject);

              // remove references to sourceProjects if they are encountered
              project
                  .getConfigurations()
                  .getByName(JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME)
                  .getDependencies()
                  .removeIf(
                      dependency12 ->
                          (dependency12 instanceof ProjectDependency)
                              && consolidatedProject.contains(
                                  ((ProjectDependency) dependency12).getDependencyProject()));

              // add sources to jar
              project
                  .getTasks()
                  .withType(Jar.class)
                  .forEach(jar -> jar.from(dependencyProjectMainOutput));

              // also configure the java-gradle-plugin if necessary
              project
                  .getTasks()
                  .withType(PluginUnderTestMetadata.class)
                  .forEach(task -> task.getPluginClasspath().from(dependencyProjectMainOutput));
            });
  }

  private ProjectDependency verifyProjectDependency(Dependency dependency) {
    if (!(dependency instanceof ProjectDependency)) {
      throw new GradleException(
          "'"
              + CONSOLIDATE_CONFIGURATION
              + "' cannot process non-project dependency: "
              + dependency);
    }
    return (ProjectDependency) dependency;
  }

  private void verifyProjectDependencyUsesDefaultConfiguration(ProjectDependency projectDependency) {
    String targetConfiguration = projectDependency.getTargetConfiguration();
    if (targetConfiguration != null
        && !targetConfiguration.equals(Dependency.DEFAULT_CONFIGURATION)) {
      throw new GradleException(
          "'"
              + CONSOLIDATE_CONFIGURATION
              + "' can only process the 'default' target configuration for: "
              + projectDependency
              + ", not: '"
              + targetConfiguration
              + "'");
    }
  }

  private void createEnsureNoProjectDependenciesTask(Project project) {
    TaskProvider<EnsureNoProjectDependenciesTask> ensureNoProjectDependenciesTask =
        project
            .getTasks()
            .register(
                "ensureNoProjectDependencies", EnsureNoProjectDependenciesTask.class, project);
    ensureNoProjectDependenciesTask.configure(
        task -> task.setConfiguration(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME));

    project.afterEvaluate(
        ignored ->
            project
                .getTasks()
                .matching(task -> task.getName().startsWith("compile"))
                .forEach(task -> task.dependsOn(ensureNoProjectDependenciesTask)));
  }
}
