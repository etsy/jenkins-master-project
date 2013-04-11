package com.etsy.jenkins;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Cause;
import hudson.model.ParametersAction;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;

import java.io.IOException;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import com.google.inject.Inject;

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
    Set<AbstractProject> subProjects =  filterDisabledJobs(masterBuild.getSubProjects(), listener);
    Set<AbstractProject> hiddenSubProjects = filterDisabledJobs(masterBuild.getHiddenSubProjects(), listener);

    Cause cause = new MasterBuildCause(masterBuild);

    scheduleBuilds(masterBuild, subProjects, cause, listener);

    scheduleHiddenBuilds(masterBuild, hiddenSubProjects, cause, listener);

    waitForBuilds(masterBuild, subProjects, cause, listener);

    return true; // This should be the only builder
  }
  
  @SuppressWarnings("rawtypes")
  private Set<AbstractProject> filterDisabledJobs(Set<AbstractProject> projects, 
		  BuildListener listener) {
	Set<AbstractProject> subs = projects;
	for(Iterator<AbstractProject> i = subs.iterator(); i.hasNext();) {
      AbstractProject p = i.next();
		if(p.isDisabled()) {
		  listener.getLogger().printf("%s is disabled.\n",p.getDisplayName());
		  i.remove();
		}
	}
	return subs;
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

  @Override
  public DescriptorImpl getDescriptor() {
    return (DescriptorImpl) super.getDescriptor();
  }

  @Extension
  public static class DescriptorImpl extends BuildStepDescriptor<Builder> {
    @Override
    public String getDisplayName() {
      return "Execute multiple sub-projects";
    }

    @Override
    public boolean isApplicable(Class<? extends AbstractProject> jobType) {
      return MasterProject.class.equals(jobType);
    }
  }
}

