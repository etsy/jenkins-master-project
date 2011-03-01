package com.etsy.jenkins;

import com.etsy.jenkins.finder.BuildFinder;

import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Cause;
import hudson.model.Hudson;
import hudson.model.Result;

import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import java.io.PrintStream;
import java.util.concurrent.Callable;

public class BuildWatcher implements Callable<AbstractBuild> {

  public static interface Factory {
    BuildWatcher create(
        MasterBuild masterBuild,
        AbstractProject project,
        Cause cause,
        BuildListener listener);
  }

  private final Hudson hudson;
  private final BuildFinder buildFinder;
  private final long pingTime;

  private final MasterBuild masterBuild;
  private final AbstractProject project;
  private final Cause cause;
  private final BuildListener listener;

  @Inject
  public BuildWatcher(
      @Assisted MasterBuild masterBuild,
      @Assisted AbstractProject project,
      @Assisted Cause cause,
      @Assisted BuildListener listener,
      Hudson hudson,
      BuildFinder buildFinder,
      @MasterProject.PingTime long pingTime) {
    this.masterBuild = masterBuild;
    this.project = project;
    this.cause = cause;
    this.listener = listener;

    this.hudson = hudson;
    this.buildFinder = buildFinder;
    this.pingTime = pingTime;
  }

  public AbstractBuild call() {
    PrintStream logger = listener.getLogger();

    AbstractBuild build = null;
    do {
      build = buildFinder.findBuild(project, cause);
      logger.printf("......... %s (pending)\n", project.getDisplayName());
      rest();
    } while (build == null);

    masterBuild.addSubBuild(project.getDisplayName(), build.getNumber());

    while (build.isBuilding()) {
      logger.printf("......... %s (%s%s%s)\n", 
          project.getDisplayName(),
          hudson.getRootUrl(),
          build.getUrl(),
          "console");
      rest();
    }

    Result result = build.getResult();
    String page = "testReport";
    if (result.isWorseThan(Result.UNSTABLE)) {
        page = "console";
    }
    logger.printf("[%s] %s (%s%s%s)\n", 
        result, 
        project.getDisplayName(),
        hudson.getRootUrl(),
        build.getUrl(),
        page);

    return build;
  }

  private void rest() {
    try {
      Thread.sleep(pingTime);
    } catch (InterruptedException ignore) {}
  }
}

