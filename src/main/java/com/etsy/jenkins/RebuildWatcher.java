package com.etsy.jenkins;

import com.etsy.jenkins.finder.BuildFinder;

import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Cause;
import hudson.model.Hudson;
import hudson.model.Result;

import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import hudson.model.Job;
import hudson.model.PermalinkProjectAction.Permalink;
import hudson.model.Run;
import hudson.model.queue.QueueTaskFuture;
import java.util.concurrent.ExecutionException;
import jenkins.model.PeepholePermalink;
import org.apache.commons.io.output.NullOutputStream;

import hudson.model.StreamBuildListener;
import java.util.Map;
import hudson.model.Descriptor;
import hudson.tasks.Publisher;
import jenkins.plugins.slack.SlackNotifier;
import jenkins.plugins.slack.ActiveNotifier;
import jenkins.plugins.slack.SlackService;

/**
 * A Runnable that watches a rebuilt sub-job from start to finish.
 *
 * It updates the parent (MasterBuild) job's state appropriately at the start
 * and end of sub-job execution.
 */
public class RebuildWatcher implements Runnable {

  public static interface Factory {
    RebuildWatcher create(
        MasterBuild masterBuild,
        AbstractProject project,
        Cause cause,
        QueueTaskFuture<?> buildFuture);
  }

  private final BuildFinder buildFinder;
  private final long pingTime;

  private final MasterBuild masterBuild;
  private final AbstractProject project;
  private final Cause cause;
  private final QueueTaskFuture<?> buildFuture;
  @Inject static Hudson hudson;

  @Inject
  public RebuildWatcher(
       @Assisted MasterBuild masterBuild,
       @Assisted AbstractProject project,
       @Assisted Cause cause,
       @Assisted QueueTaskFuture<?> buildFuture,
       BuildFinder buildFinder,
       @MasterProject.PingTime long pingTime) {
    this.masterBuild = masterBuild;
    this.project = project;
    this.cause = cause;
    this.buildFuture = buildFuture;

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

    try {
      this.buildFuture.get();
    } catch (InterruptedException ex) {
      throw new RuntimeException(ex);
    } catch (ExecutionException ex) {
      throw new RuntimeException(ex);
    }

    // Invoke SlackNotifier when rebuilds result in a successful build
    this.rebuildNotify();

    // Update the "last stable build" and "last successful build" permalinks.
    new UpdateableProxyPermalink(
        (PeepholePermalink) Permalink.LAST_STABLE_BUILD)
            .update(masterBuild.getProject(), masterBuild);
    new UpdateableProxyPermalink(
        (PeepholePermalink) Permalink.LAST_SUCCESSFUL_BUILD)
            .update(masterBuild.getProject(), masterBuild);
  }

  private void rebuildNotify() {
    if (masterBuild.getNotifyOnRebuild() && hudson.getPlugin("slack") != null && hudson.getPlugin("slack").getWrapper().isActive()) {
      if (masterBuild.getResult() == Result.SUCCESS) {

        SlackNotifier notifier = null;
        Map<Descriptor<Publisher>, Publisher> map = masterBuild.getProject().getPublishersList().toMap();
        for (Publisher publisher : map.values()) {
          if (publisher instanceof SlackNotifier) {
            notifier = (SlackNotifier) publisher;
          }
        }

        ActiveNotifier.MessageBuilder messageBuilder = new ActiveNotifier.MessageBuilder(notifier, masterBuild);
        String message = messageBuilder.append(" Rebuild was successful").appendOpenLink().toString();
        notifier.newSlackService(masterBuild, new StreamBuildListener(new NullOutputStream())).publish(message, "good");
      }
    }
  }

  private void rest() {
    try {
      Thread.sleep(pingTime);
    } catch (InterruptedException ignore) {}
  }

  /**
   * A PeepholePermalink that delegates to a given PeepholePeermalink and
   * exposes the @{code updateCache} method.
   *
   * These are used to update existing Permalinks, since the
   * @{code updateCache} method is protected in the Jenkins API. For example,
   * if a retried sub-job causes a master build to update from red to green,
   * we need to update the "last stable build" link. We use an instance of this
   * class to do so.
   *
   * Updating a build's result is a violation of the Jenkins API. A job's
   * result isn't supposed to change once it's finished running and been set
   * once. We accept that this hack makes us scofflaws.
   */
  private static final class UpdateableProxyPermalink
    extends PeepholePermalink {

    private final PeepholePermalink delegate;

    private UpdateableProxyPermalink(PeepholePermalink delegate) {
      this.delegate = delegate;
    }

    @Override
    public boolean apply(Run<?, ?> run) {
      return delegate.apply(run);
    }

    @Override
    public String getDisplayName() {
      return delegate.getDisplayName();
    }

    @Override
    public String getId() {
      return delegate.getId();
    }

    private void update(Job<?, ?> job, Run<?, ?> run) {
      super.updateCache(job, run);
    }
  }
}

