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

import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Inject;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;

public class EnsureNoProjectDependenciesTask extends DefaultTask {

  private final Project project;

  private String configuration;

  @Inject
  public EnsureNoProjectDependenciesTask(Project project) {
    this.project = project;
  }

  public void setConfiguration(String configuration) {
    this.configuration = configuration;
  }

  @Input
  public String getConfiguration() {
    return configuration;
  }

  /**
   * Ensure no project dependencies are included in the {@link #configuration}.
   *
   * @throws GradleException if any projects are found
   */
  @TaskAction
  public void ensureNoProjectDependencies() {
    List<ProjectComponentIdentifier> projectDependencies =
        project
            .getConfigurations()
            .getByName(configuration)
            .getResolvedConfiguration()
            .getResolvedArtifacts()
            .stream()
            .filter(
                artifact ->
                    artifact.getId().getComponentIdentifier() instanceof ProjectComponentIdentifier)
            .map(ResolvedArtifact::getId)
            .map(ComponentArtifactIdentifier::getComponentIdentifier)
            .filter(ProjectComponentIdentifier.class::isInstance)
            .map(ProjectComponentIdentifier.class::cast)
            .collect(Collectors.toList());

    // fail if any project dependencies found
    if (projectDependencies.size() > 0) {
      String allProjects =
          projectDependencies
              .stream()
              .map(ProjectComponentIdentifier::getProjectPath)
              .map(path -> "'" + path + "'")
              .collect(Collectors.joining(", "));
      throw new GradleException(
          "Unexpected projects found in '" + configuration + "': [" + allProjects + "]");
    }
  }
}
