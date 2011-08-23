package com.etsy.jenkins;

import com.etsy.jenkins.finder.BuildFinder;

import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Cause;
import hudson.model.Hudson;
import hudson.model.Result;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import java.io.PrintStream;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

public class BuildWatcher implements Runnable {

  public static interface Factory {
    BuildWatcher create(
        MasterBuild masterBuild,
        Set<AbstractProject> projects,
        Cause cause,
        BuildListener listener);
  }

  private final Hudson hudson;
  private final BuildFinder buildFinder;
  private final long pingTime;

  private final MasterBuild masterBuild;
  private final Set<AbstractProject> projects;
  private final Cause cause;
  private final BuildListener listener;

  @Inject
  public BuildWatcher(
      @Assisted MasterBuild masterBuild,
      @Assisted Set<AbstractProject> projects,
      @Assisted Cause cause,
      @Assisted BuildListener listener,
      Hudson hudson,
      BuildFinder buildFinder,
      @MasterProject.PingTime long pingTime) {
    this.masterBuild = masterBuild;
    this.projects = projects;
    this.cause = cause;
    this.listener = listener;

    this.hudson = hudson;
    this.buildFinder = buildFinder;
    this.pingTime = pingTime;
  }

  public void run() {
    PrintStream logger = listener.getLogger();

    Map<AbstractProject, AbstractBuild> projectBuildMap = 
        Maps.<AbstractProject, AbstractBuild>newHashMap();
    Set<AbstractProject> completed = Sets.<AbstractProject>newHashSet();
    do {
      for (AbstractProject project : projects) {
          AbstractBuild build = projectBuildMap.get(project);
          if (build != null) {
              if (build.isBuilding()) {
                  logger.printf("......... %s (%s%s%s)\n", 
                      project.getDisplayName(),
                      hudson.getRootUrl(),
                      build.getUrl(),
                      "console");
              } else if (!completed.contains(project)) {
                  completed.add(project);
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
              }
          } else {
              build = buildFinder.findBuild(project, cause);
              if (build != null) {
                  masterBuild.addSubBuild(
                      project.getDisplayName(),
                      build.getNumber());
                  projectBuildMap.put(project, build);
              } else {
                  logger.printf(
                      "......... %s (pending)\n", 
                      project.getDisplayName());
              }
          }
      }
      rest();
    } while (!completed.containsAll(projects));
  }

  private void rest() {
    try {
      Thread.sleep(pingTime);
    } catch (InterruptedException ignore) {}
  }
}

