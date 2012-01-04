package com.etsy.jenkins;

import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.model.Job;
import hudson.model.JobProperty;
import hudson.model.JobPropertyDescriptor;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import net.sf.json.JSONObject;

public class RebuildRedsJobProperty extends JobProperty<MasterProject> {

  private int maxRetries;

  @DataBoundConstructor
  public RebuildRedsJobProperty(int maxRetries) {
    this.maxRetries = maxRetries;
  }

  @Override
  public boolean prebuild(AbstractBuild build, BuildListener listener) {
    RebuildRedsAction action = build.getAction(RebuildRedsAction.class);
    if (action != null) {
      ((MasterBuild) build).setMaxRetries(action.getMaxRetries());
    } else {
      ((MasterBuild) build).setMaxRetries(this.maxRetries);
    }
    return true;
  }

  public int getMaxRetries() {
     return this.maxRetries;
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
      return "Rebuild Failed Sub-Builds";
    }

    @Override
    public boolean isApplicable(Class<? extends Job> jobType) {
        return jobType.equals(MasterProject.class);
    }

    @Override
    public JobProperty<?> newInstance(
        StaplerRequest req,
        JSONObject formData)
        throws Descriptor.FormException {
      JSONObject property = formData.optJSONObject("rebuildRedsJobProperty");
      if (property == null) {
        return null;
      }
      int maxRetries = Integer.parseInt(property.getString("maxRetries"));
      return new RebuildRedsJobProperty(maxRetries);
    }
  }
}

