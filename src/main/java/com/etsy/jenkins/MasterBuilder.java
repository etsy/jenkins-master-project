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
import java.util.concurrent.CompletionService;
import java.util.concurrent.Future;
import java.util.concurrent.ExecutionException;

/*package*/ class MasterBuilder extends Builder {

  private final BuildWatcher.Factory buildWatcherFactory;
  private final CompletionService<AbstractBuild> completionService;
  private final ParametersActionPropagator parametersActionPropagator;

  @Inject
  public MasterBuilder(
      BuildWatcher.Factory buildWatcherFactory,
      CompletionService<AbstractBuild> completionService,
      ParametersActionPropagator parametersActionPropagator) {
    this.buildWatcherFactory = buildWatcherFactory;
    this.completionService = completionService;
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

  /*package*/ Set<AbstractBuild> waitForBuilds(MasterBuild masterBuild,
      Set<AbstractProject> subProjects, Cause cause, BuildListener listener) {
    
    Set<AbstractBuild> builds = Sets.<AbstractBuild>newHashSet();

    for (AbstractProject subProject : subProjects) {
      BuildWatcher watcher = 
          buildWatcherFactory.create(masterBuild, subProject, cause, listener);
      completionService.submit(watcher);
    }
    
    for (int i = 0; i < subProjects.size(); i++) {
      try {
        AbstractBuild build = completionService.take().get();
        builds.add(build);
      } catch (ExecutionException ignore) {
        // TODO
      } catch (InterruptedException ignore) {
        // TODO
      }
    }

    return builds;
  }
}

