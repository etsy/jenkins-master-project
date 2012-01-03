package com.etsy.jenkins;

import com.etsy.jenkins.finder.ProjectFinder;

import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.model.Item;
import hudson.model.Job;
import hudson.model.JobProperty;
import hudson.model.JobPropertyDescriptor;
import hudson.model.TopLevelItem;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import org.apache.commons.lang.StringUtils;

import net.sf.json.JSONObject;

import com.google.common.collect.Sets;
import com.google.inject.Inject;

import java.util.Set;
import java.util.StringTokenizer;

public class SubProjectsJobProperty extends JobProperty<MasterProject> {

  @Inject static Hudson hudson;

  private final String defaultSubProjects;
  private final String hiddenSubProjects;

  @DataBoundConstructor
  public SubProjectsJobProperty(
      String defaultSubProjects,
      String hiddenSubProjects) {
    this.defaultSubProjects = defaultSubProjects;
    this.hiddenSubProjects = hiddenSubProjects;
  }

  public boolean prebuild(AbstractBuild build, BuildListener listener) {
    SubProjectsAction action = build.getAction(SubProjectsAction.class);
    Set<String> subProjectNames = action.getSubProjectNames();
    if (action != null) {
       if (subProjectNames != null && !subProjectNames.isEmpty()) {
        ((MasterBuild) build).setSubProjects(action.getSubProjectNames());
      } else {
        ((MasterBuild) build).setSubProjects(
            getDefaultSubProjects(
                action.getExcludeProjectNames()));
      }
    } else {
      ((MasterBuild) build).setSubProjects(getDefaultSubProjects(null));
    }
    ((MasterBuild) build).setHiddenSubProjects(getHiddenSubProjects());
    return true;
  }

  public String getDefaultSubProjectsString() {
    return defaultSubProjects;
  }

  private Set<String> getDefaultSubProjects(Set<String> excludeProjects) {
    Set<String> projects = Sets.<String>newHashSet();
    if (defaultSubProjects == null || defaultSubProjects.isEmpty()) {
      projects = getMasterProject().getSubProjectNames();
    } else {
      projects = getProjectsFromString(getDefaultSubProjectsString());
    }

    for (String hidden : getHiddenSubProjects()) {
      projects.remove(hidden);
    }

    for (String exclude : excludeProjects) {
      projects.remove(exclude);
    }

    return projects;
  }

  public String getHiddenSubProjectsString() {
    return (hiddenSubProjects == null) ? "" : hiddenSubProjects;
  }

  private Set<String> getHiddenSubProjects() {
    return getProjectsFromString(getHiddenSubProjectsString());
  }

  private Set<String> getProjectsFromString(String projectsString) {
    Set<String> projects = Sets.<String>newHashSet();
    StringTokenizer tokenizer = new StringTokenizer(projectsString);
    while (tokenizer.hasMoreTokens()) {
      projects.add(tokenizer.nextToken());
    }
    return projects;
  }

  public MasterProject getMasterProject() {
    return (MasterProject) owner;
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
      return "Allow building and hiding a selection of sub-jobs.";
    }

    @Override
    public boolean isApplicable(Class<? extends Job> jobType) {
      return jobType.equals(MasterProject.class);
    }

    @Override
    public JobProperty<?> newInstance(
        StaplerRequest req, JSONObject formData) 
        throws Descriptor.FormException {
      JSONObject property = formData.optJSONObject("subProjectsJobProperty");
      if (property == null) {
        return null;
      }
      String subProjects = property.getString("defaultSubProjects");
      String hiddenProjects = property.getString("hiddenSubProjects");
      return new SubProjectsJobProperty(subProjects, hiddenProjects);
    }
  }
}

