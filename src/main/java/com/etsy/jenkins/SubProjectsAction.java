package com.etsy.jenkins;

import com.etsy.jenkins.finder.ProjectFinder;

import hudson.model.Action;
import hudson.model.AbstractProject;

import com.google.common.collect.Sets;
import com.google.inject.Inject;

import java.util.Set;

public class SubProjectsAction implements Action {

  @Inject static ProjectFinder projectFinder;

  private Set<String> subProjects;

  public SubProjectsAction(Set<String> subProjects) {
    this.subProjects = subProjects;
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

  public Set<AbstractProject> getSubProjects() {
    Set<AbstractProject> projects = Sets.<AbstractProject>newHashSet();
    for (String subProject : subProjects) {
      projects.add(projectFinder.findProject(subProject));
    }
    return projects;
  }
}

