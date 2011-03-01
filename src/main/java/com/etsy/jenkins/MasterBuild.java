package com.etsy.jenkins;

import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Build;
import hudson.model.Hudson;
import hudson.model.Item;
import hudson.model.Result;
import hudson.model.Run;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;

public class MasterBuild extends Build<MasterProject, MasterBuild> {

  @Inject static Provider<MasterResult> masterResultProvider;

  private MasterResult masterResult;

  public MasterBuild(MasterProject project) throws IOException {
    super(project);
    this.masterResult = masterResultProvider.get();
  }

  public MasterBuild(MasterProject project, File file) throws IOException {
    super(project, file);
  }

  public Set<AbstractProject> getSubProjects() {
    return this.masterResult.getProjects();
  }

  @Override
  public void setResult(Result result) {
    super.result = result;
  }

  @Override
  public Result getResult() {
    Result result = this.masterResult.getOverallResult();
    setResult(result);
    return result;
  }

  /*package*/ void addSubBuild(String projectName, int buildNumber) {
    masterResult.addBuild(projectName, buildNumber);
  }

  public List<AbstractBuild> getLatestBuilds() {
    return masterResult.getLatestBuilds();
  }
}

