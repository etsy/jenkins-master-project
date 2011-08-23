package com.etsy.jenkins;

import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Cause;
import hudson.model.ParametersAction;
import hudson.tasks.Builder;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.inject.Inject;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/*package*/ class MasterBuilder extends Builder {

  private final BuildWatcher.Factory buildWatcherFactory;
  private final ExecutorService executorService;
  private final ParametersActionPropagator parametersActionPropagator;

  @Inject
  public MasterBuilder(
      BuildWatcher.Factory buildWatcherFactory,
      ExecutorService executorService,
      ParametersActionPropagator parametersActionPropagator) {
    this.buildWatcherFactory = buildWatcherFactory;
    this.executorService = executorService;
    this.parametersActionPropagator = parametersActionPropagator;
  }

  @Override
  public boolean perform(
      AbstractBuild build, Launcher launcher, BuildListener listener)
      throws InterruptedException, IOException {
    MasterBuild masterBuild = (MasterBuild) build;
    Set<AbstractProject> subProjects = masterBuild.getSubProjects();
    Set<AbstractProject> hiddenSubProjects = masterBuild.getHiddenSubProjects();

    Cause cause = new MasterBuildCause(masterBuild);

    scheduleBuilds(masterBuild, subProjects, cause, listener);

    scheduleHiddenBuilds(masterBuild, hiddenSubProjects, cause, listener);

    waitForBuilds(masterBuild, subProjects, cause, listener);

    return false; // This should be the only builder
  }

  /*package*/ void scheduleBuilds(
      MasterBuild masterBuild,
      Set<AbstractProject> subProjects, 
      Cause cause,
      BuildListener listener) {
    for (AbstractProject subProject : subProjects) {
      Future<AbstractBuild> future =
          scheduleBuild(masterBuild, subProject, cause, listener);
      masterBuild.addFuture(future);
    }
  }

  /*package*/ void scheduleHiddenBuilds(
      MasterBuild masterBuild,
      Set<AbstractProject> subProjects,
      Cause cause,
      BuildListener listener) {
    for (AbstractProject subProject : subProjects) {
      scheduleBuild(masterBuild, subProject, cause, listener);
    }
  }

  /*package*/ Future<AbstractBuild> scheduleBuild(
      MasterBuild masterBuild,
      AbstractProject subProject,
      Cause cause,
      BuildListener listener) {
    ParametersAction[] parametersActions =
        parametersActionPropagator
            .getPropagatedActions(masterBuild, subProject);
    Future<AbstractBuild> future =
        subProject.scheduleBuild2(0, cause, parametersActions);
    listener.getLogger().printf("Build scheduled: %s\n", 
        subProject.getDisplayName());
    return future;
  }

  /*package*/ void waitForBuilds(MasterBuild masterBuild,
      Set<AbstractProject> subProjects, Cause cause, BuildListener listener) {
    BuildWatcher watcher = 
        buildWatcherFactory.create(masterBuild, subProjects, cause, listener);
    try {
      Future<?> future = executorService.submit(watcher);
      future.get();
    } catch (ExecutionException e) {
      e.printStackTrace();
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }
}

