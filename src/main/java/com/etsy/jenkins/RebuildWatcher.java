package com.etsy.jenkins;

import com.etsy.jenkins.finder.BuildFinder;

import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Cause;

import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

public class RebuildWatcher implements Runnable {

  public static interface Factory {
    RebuildWatcher create(
        MasterBuild masterBuild,
        AbstractProject project,
        Cause cause);
  }

  private final BuildFinder buildFinder;
  private final long pingTime;

  private final MasterBuild masterBuild;
  private final AbstractProject project;
  private final Cause cause;

  @Inject
  public RebuildWatcher(
       @Assisted MasterBuild masterBuild,
       @Assisted AbstractProject project,
       @Assisted Cause cause,
       BuildFinder buildFinder,
       @MasterProject.PingTime long pingTime) {
    this.masterBuild = masterBuild;
    this.project = project;
    this.cause = cause;

    this.buildFinder = buildFinder;
    this.pingTime = pingTime;
  }

  public void run() {
    AbstractBuild build = null;
    do {
      build = buildFinder.findBuild(project, cause);
      rest();
    } while(build == null);

    masterBuild.addSubBuild(project.getDisplayName(), build.getNumber());
  }

  private void rest() {
    try {
      Thread.sleep(pingTime);
    } catch (InterruptedException ignore) {}
  }
}

