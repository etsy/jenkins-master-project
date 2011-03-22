package com.etsy.jenkins;

import hudson.model.Action;
import hudson.model.AbstractProject;

import java.util.Set;

public class SubProjectsAction implements Action {

  private final Set<AbstractProject> subProjects;

  public SubProjectsAction(Set<AbstractProject> subProjects) {
    this.subProjects = subProjects;
  }

  public String getDisplayName() {
    return "Build a selection of sub-jobs";
  }

  public String getIconFileName() {
    return "documents-properties.gif";
  }

  public String getUrlName() {
    return "subJobs";
  }

  public Set<AbstractProject> getSubProjects() {
    return subProjects;
  }
}

