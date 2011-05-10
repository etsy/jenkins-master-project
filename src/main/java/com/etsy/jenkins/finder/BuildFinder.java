package com.etsy.jenkins.finder;

import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Cause;
import hudson.model.Run;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.util.List;

@Singleton
public class BuildFinder {

  private final ProjectFinder projectFinder;

  @Inject
  public BuildFinder(ProjectFinder projectFinder) {
    this.projectFinder = projectFinder;
  }

  public AbstractBuild findBuild(String projectName, int buildNumber) {
    AbstractProject project = this.projectFinder.findProject(projectName);
    return this.findBuild(project, buildNumber);
  }

  public AbstractBuild findBuild(AbstractProject project, int buildNumber) {
    if (project == null) return null;
    return (AbstractBuild) project.getBuildByNumber(buildNumber);
  }

  public AbstractBuild findBuild(String projectName, Cause cause) {
    AbstractProject project = this.projectFinder.findProject(projectName);
    return this.findBuild(project, cause);
  }

  public AbstractBuild findBuild(AbstractProject project, Cause cause) {
    if (project == null) return null;
    List<Run> builds = project.getBuilds();
    for (Run build : builds) {
      List<Cause> causes = build.getCauses();
      if (causes.contains(cause)) {
        return (AbstractBuild) build;
      }
    }
    return null;
  }
}

