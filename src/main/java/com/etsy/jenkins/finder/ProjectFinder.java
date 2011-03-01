package com.etsy.jenkins.finder;

import hudson.model.AbstractProject;
import hudson.model.Hudson;

import com.google.inject.Singleton;
import com.google.inject.Inject;

@Singleton
public class ProjectFinder {

  private final Hudson instance;

  @Inject
  public ProjectFinder(Hudson instance) {
    this.instance = instance;
  }

  public AbstractProject findProject(String projectName) {
    return (AbstractProject) this.instance.getItem(projectName);
  }
}

