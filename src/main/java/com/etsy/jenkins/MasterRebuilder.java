package com.etsy.jenkins;

import hudson.model.AbstractProject;
import hudson.model.Cause;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.util.concurrent.Executor;

@Singleton
/*package*/ class MasterRebuilder {

  private final RebuildWatcher.Factory rebuildWatcherFactory;
  private final Executor executor;

  @Inject
  public MasterRebuilder(
      RebuildWatcher.Factory rebuildWatcherFactory,
      Executor executor) {
    this.rebuildWatcherFactory = rebuildWatcherFactory;
    this.executor = executor;
  }

  public void rebuild(
      MasterBuild masterBuild, AbstractProject project, Cause cause) {
    project.scheduleBuild(cause);
    executor.execute(
        rebuildWatcherFactory.create(masterBuild, project, cause)); 
  }
}

