package com.etsy.jenkins;

import com.etsy.jenkins.finder.ProjectFinder;

import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Build;
import hudson.model.Cause;
import hudson.model.Result;
import hudson.security.Permission;

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Future;
import javax.servlet.ServletException;
import org.kohsuke.stapler.HttpResponse;

public class MasterBuild extends Build<MasterProject, MasterBuild> {

  @Inject static Provider<MasterResult> masterResultProvider;
  @Inject static MasterRebuilder rebuilder;
  @Inject static ProjectFinder projectFinder;

  private MasterResult masterResult;
  private Set<String> subProjects;
  private Set<String> hiddenSubProjects;

  private int maxRetries;

  private transient List<Future<AbstractBuild>> futuresToAbort =
      Lists.<Future<AbstractBuild>>newArrayList();

  public MasterBuild(MasterProject project) throws IOException {
    super(project);
    this.masterResult = masterResultProvider.get();
    this.subProjects = project.getSubProjectNames();
    this.hiddenSubProjects = Sets.<String>newHashSet();
    this.maxRetries = 0;
  }

  public MasterBuild(MasterProject project, File file) throws IOException {
    super(project, file);
  }

  public Set<AbstractProject> getSubProjects() {
    return getProjectsByNames(this.subProjects);
  }

  /*package*/ void setSubProjects(Set<String> subProjects) {
    this.subProjects = subProjects;
  }

  public Set<AbstractProject> getHiddenSubProjects() {
    return getProjectsByNames(this.hiddenSubProjects);
  }

  /*package*/ void setHiddenSubProjects(Set<String> subProjects) {
    this.hiddenSubProjects = subProjects;
  }

  public int getMaxRetries() {
    return this.maxRetries;
  }

  /*package*/ void setMaxRetries(int maxRetries) {
    this.maxRetries = maxRetries;
  }

  private Set<AbstractProject> getProjectsByNames(Set<String> subProjects) {
    Set<AbstractProject> projects = Sets.<AbstractProject>newHashSet();

    for (String subProject : subProjects) {
      AbstractProject project = projectFinder.findProject(subProject);
      if (project != null) {
        projects.add(project);
      }
    }
    return projects;
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

  @Override
  public boolean isBuilding() {
    if (super.isBuilding()) {
      // If this build's executor is running, defer to that.
      return true;
    }
    return this.masterResult.isBuilding();
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

  public void rebuild(AbstractProject project) throws ServletException {
    if (!getSubProjects().contains(project)) {
        throw new ServletException(
            "Not a sub-project of this master build: "
            + project.getDisplayName());
    }

    SubResult subResult = masterResult.getResult(project.getDisplayName());
    int rebuildNumber = subResult.getBuildNumbers().size();
    Cause cause = new MasterBuildCause(this, rebuildNumber);
    rebuild(project, cause);
  }

  /*package*/ void rebuild(AbstractProject project, Cause cause) {
    rebuilder.rebuild(this, project, cause);
  }

  public void doRefreshLatestBuilds(StaplerRequest req, StaplerResponse res)
      throws IOException, ServletException {
    checkPermission(Permission.READ);

    req.getView(this, "latestBuildList.jelly").forward(req, res);
  }

  /*package*/ void addFuture(Future<AbstractBuild> future) {
    futuresToAbort.add(future);
  }

  @Override
  public HttpResponse doStop()
      throws IOException, ServletException {
    for (Future<AbstractBuild> future : futuresToAbort) {
      future.cancel(true);
    }
    return super.doStop();
  }
}

