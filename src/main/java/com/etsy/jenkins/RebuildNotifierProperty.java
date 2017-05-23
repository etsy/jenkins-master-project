package com.etsy.jenkins;

import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.model.Job;
import hudson.model.Hudson;
import hudson.model.JobProperty;
import hudson.model.JobPropertyDescriptor;
import com.google.inject.Inject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import com.etsy.jenkins.MasterBuild;
import net.sf.json.JSONObject;

public class RebuildNotifierProperty extends JobProperty<MasterProject> {

  @Inject static Hudson hudson;
  private boolean notifyOnRebuild;

  @DataBoundConstructor
  public RebuildNotifierProperty(boolean notifyOnRebuild) {
    this.notifyOnRebuild = notifyOnRebuild;
  }

  @Override
  public boolean prebuild(AbstractBuild build, BuildListener listener) {
    ((MasterBuild) build).setNotifyOnRebuild(this.notifyOnRebuild);
    return true;
  }

  public boolean getNotifyOnRebuild() {
     return this.notifyOnRebuild;
  }

  @Override
  public JobPropertyDescriptor getDescriptor() {
    return DESCRIPTOR;
  }

  @Extension
  public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();
  public static class DescriptorImpl extends JobPropertyDescriptor {

    @Override
    public String getDisplayName() {
      return "Allow rebuilds to notify Slack when rebuilds are successful";
    }

    @Override
    public boolean isApplicable(Class<? extends Job> jobType) {
        return jobType.equals(MasterProject.class) && hudson.getPlugin("slack") != null && hudson.getPlugin("slack").getWrapper().isActive();
    }

    @Override
    public JobProperty<?> newInstance(
        StaplerRequest req,
        JSONObject formData)
        throws Descriptor.FormException {

      boolean notifyOnRebuild = formData.getBoolean("notifyOnRebuild");
      return new RebuildNotifierProperty(notifyOnRebuild);
    }
  }
}
