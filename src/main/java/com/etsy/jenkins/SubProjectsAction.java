package com.etsy.jenkins;

import com.etsy.jenkins.finder.ProjectFinder;

import hudson.model.AbstractProject;
import hudson.model.Action;

import com.google.common.collect.Sets;
import com.google.inject.Inject;

import java.util.Set;

public class SubProjectsAction implements Action {

  @Inject static ProjectFinder projectFinder;

  private Set<String> subProjects;
  private Set<String> excludeProjects;

  public SubProjectsAction(
      Set<String> subProjects,
      Set<String> excludeProjects) {
    this.subProjects = subProjects;
    this.excludeProjects = excludeProjects;
  }

  public String getDisplayName() {
    return "Selected Sub-jobs";
  }

  public String getIconFileName() {
    return null;
  }

  public String getUrlName() {
    return "subJobs";
  }

  public Set<String> getSubProjectNames() {
    return subProjects;
  }

  public Set<String> getExcludeProjectNames() {
    return excludeProjects;
  }

  public Set<AbstractProject> getSubProjects() {
    Set<AbstractProject> projects = Sets.<AbstractProject>newHashSet();
    for (String subProject : subProjects) {
      AbstractProject project = projectFinder.findProject(subProject);
      projects.add(project);
    }
    return projects;
  }

}

