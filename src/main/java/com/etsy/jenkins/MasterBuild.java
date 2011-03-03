package com.etsy.jenkins;

import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Build;
import hudson.model.Cause;
import hudson.model.Hudson;
import hudson.model.Item;
import hudson.model.Result;
import hudson.model.Run;

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

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
import javax.servlet.ServletException;

public class MasterBuild extends Build<MasterProject, MasterBuild> {

  @Inject static Provider<MasterResult> masterResultProvider;
  @Inject static MasterRebuilder rebuilder;

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

    for (int i = 0; i < 5; i++) {
      try {
        this.save();
        return;
      } catch (IOException retry) {}
    }
  }

  public List<AbstractBuild> getLatestBuilds() {
    return masterResult.getLatestBuilds();
  }

  public void rebuild(AbstractProject project) {
    SubResult subResult = masterResult.getResult(project.getDisplayName());
    int rebuildNumber = subResult.getBuildNumbers().size();
    Cause cause = new MasterBuildCause(this, rebuildNumber);

    rebuilder.rebuild(this, project, cause);
  }

  public void doRefreshLatestBuilds(StaplerRequest req, StaplerResponse res)
      throws IOException, ServletException {
    req.getView(this, "latestBuildList.jelly").forward(req, res);
  }
}

