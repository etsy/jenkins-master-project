package com.etsy.jenkins;

import com.etsy.jenkins.finder.ProjectFinder;

import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Result;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.inject.Inject;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Set;

/*package*/ class MasterResult implements Serializable {

  @Inject static ProjectFinder projectFinder;

  /*package*/ Map<String, SubResult> results;

  public MasterResult() {
    this.results = Maps.<String, SubResult>newHashMap();
  }

  public Set<AbstractProject> getProjects() {
    Set<AbstractProject> projects = Sets.<AbstractProject>newHashSet();
    for (String name : this.results.keySet()) {
      projects.add(projectFinder.findProject(name));
    }
    return projects;
  }

  public SubResult getResult(String projectName) {
    return this.results.get(projectName);
  }

  public void addBuild(String projectName, Integer buildNumber) {
    SubResult result = getResult(projectName);
    if (result == null) {
      result = new SubResult(projectName);
    }
    result.addBuildNumber(buildNumber);
    this.results.put(projectName, result);
  }

  public List<AbstractBuild> getLatestBuilds() {
    List<AbstractBuild> builds = Lists.<AbstractBuild>newArrayList();
    for (SubResult subResult : results.values()) {
      builds.add(subResult.getLatestBuild());
    }
    return builds;
  }

  public Result getOverallResult() {
    Result endResult = Result.SUCCESS;
    for (SubResult subResult : results.values()) {
      endResult = endResult.combine(subResult.getResult());
    }
    return endResult;
  }
}

