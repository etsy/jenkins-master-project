package com.etsy.jenkins;

import hudson.model.AbstractProject;
import hudson.model.Cause;
import hudson.model.ParametersAction;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.util.List;
import java.util.concurrent.Executor;

@Singleton
/*package*/ class MasterRebuilder {

  private final RebuildWatcher.Factory rebuildWatcherFactory;
  private final Executor executor;
  private final ParametersActionPropagator parametersActionPropagator;

  @Inject
  public MasterRebuilder(
      RebuildWatcher.Factory rebuildWatcherFactory,
      Executor executor,
      ParametersActionPropagator parametersActionPropagator) {
    this.rebuildWatcherFactory = rebuildWatcherFactory;
    this.executor = executor;
    this.parametersActionPropagator = parametersActionPropagator;
  }

  public void rebuild(
      MasterBuild masterBuild, AbstractProject project, Cause cause) {
    List<ParametersAction> parametersActionList =
        masterBuild.getActions(ParametersAction.class);
    ParametersAction[] parameterActions = 
        parametersActionPropagator
            .getPropagatedActions(masterBuild, project);
    project.scheduleBuild(0, cause, parameterActions);
    executor.execute(
        rebuildWatcherFactory.create(masterBuild, project, cause)); 
  }
}

