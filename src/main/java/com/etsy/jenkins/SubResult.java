package com.etsy.jenkins;

import com.etsy.jenkins.finder.BuildFinder;

import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Result;
import hudson.model.Hudson;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.inject.Inject;

import java.io.Serializable;
import java.util.Iterator;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

/*package*/ class SubResult implements Serializable {

  @Inject static BuildFinder buildFinder;

  /*package*/ final String projectName;
  /*package*/ final AbstractProject project;
  /*package*/ final TreeSet<Integer> buildNumbers;

  public SubResult(String projectName) {
    this.projectName = projectName;
    this.project = (AbstractProject) Hudson.getInstance().getItemMap().get(projectName);
    this.buildNumbers = Sets.<Integer>newTreeSet();
  }

  public String getProjectName() {
    return this.projectName;
  }

  public AbstractProject getProject() {
    return this.project;
  }

  public void addBuildNumber(int buildNumber) {
    this.buildNumbers.add(buildNumber);
  }

  public SortedSet<Integer> getBuildNumbers() {
    return this.buildNumbers;
  }

  public AbstractBuild getLatestBuild() {
    return buildFinder.findBuild(getProjectName(), buildNumbers.last());
  }

  public Result getResult() {
    Result result = Result.NOT_BUILT;

    if (buildNumbers.isEmpty()) {
      return result;
    }

    Iterator<Integer> it = buildNumbers.descendingIterator();
    while (it.hasNext()) {
      int buildNumber = it.next();
      AbstractBuild build = buildFinder
        .findBuild(getProjectName(), buildNumber);
      if (build == null) {
        build = findBuild(buildNumber);
      }
      if (build != null) {
        result = build.getResult();
        if (result != Result.NOT_BUILT) {
          return result;
        }
      }
    }

    return result;
  }

  private AbstractBuild findBuild(int buildNumber) {
    if (getProject() != null) {
      return buildFinder.findBuild(getProject(), buildNumber);
    } else {
      return buildFinder.findBuild(getProjectName(), buildNumber);
    }
  }
}
