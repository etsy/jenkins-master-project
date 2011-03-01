package com.etsy.jenkins;

import com.etsy.jenkins.finder.ProjectFinder;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.model.ItemGroup;
import hudson.model.Item;
import hudson.model.Project;
import hudson.model.TopLevelItem;
import hudson.model.TopLevelItemDescriptor;
import hudson.model.listeners.ItemListener;
import hudson.tasks.Builder;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.inject.BindingAnnotation;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.assistedinject.Assisted;

import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;
import java.util.Set;
import javax.servlet.ServletException;

public class MasterProject
extends Project<MasterProject, MasterBuild>
implements TopLevelItem {

  @BindingAnnotation 
  @Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD }) 
  @Retention(RetentionPolicy.RUNTIME)
  public @interface PingTime {}

  /*package*/ final Set<String> jobNames;

  @Inject static ProjectFinder projectFinder;
  @Inject static MasterBuilder masterBuilder;

  @DataBoundConstructor
  public MasterProject(
      ItemGroup parent, 
      String name) {
    super(parent, name);
    this.jobNames = Sets.<String>newHashSet();
  }

  public boolean contains(TopLevelItem item) {
    return jobNames.contains(item.getName());
  }

  /*package*/ void onDeleted(Item item) {
    jobNames.remove(item.getName());
  }

  /*package*/ void onRenamed(Item item, String oldName, String newName) {
    if ((newName != null) && jobNames.remove(oldName)) {
      jobNames.add(newName);
    }
  }

  @Override
  public List<Builder> getBuilders() {
    return Lists.<Builder>newArrayList(masterBuilder);
  }

  @Override
  protected Class<MasterBuild> getBuildClass() {
    return MasterBuild.class;
  }

  @Override
  protected void submit(StaplerRequest req, StaplerResponse res) 
      throws IOException, ServletException, Descriptor.FormException {
  
    // Handle the job list
    jobNames.clear();
    for (TopLevelItem item : Hudson.getInstance().getItems()) {
      if (req.getParameter(item.getName()) != null) {
        jobNames.add(item.getName());
      }
    }

    super.submit(req, res);
  }

  @Override
  public TopLevelItemDescriptor getDescriptor() {
    return DESCRIPTOR;
  }

  @Override
  public Hudson getParent() {
    return (Hudson) super.getParent();
  }

  public Set<AbstractProject> getSubProjects() {
    Set<AbstractProject> subProjects = Sets.<AbstractProject>newLinkedHashSet();
    for (String jobName : jobNames) {
      subProjects.add(projectFinder.findProject(jobName));
    }
    return subProjects;
  }

  @Extension
  public static final TopLevelItemDescriptor DESCRIPTOR = 
      new TopLevelItemDescriptor() {

    private Injector injector = Guice.createInjector(new MasterModule());

    @Override
    public String getDisplayName() {
      return "Master Project";
    }

    public MasterProject newInstance(ItemGroup group, String name) {
      return new MasterProject(group, name);
    }
  };

  @Extension
  public static final ItemListener ITEM_LISTENER = new ItemListener() {

    @Override
    public void onDeleted(Item item) {
      List<MasterProject> projects =
          Hudson.getInstance().getItems(MasterProject.class);
      for (MasterProject project : projects) {
        project.onDeleted(item);
      }
    }

    @Override
    public void onRenamed(Item item, String oldName, String newName) {
      List<MasterProject> projects =
          Hudson.getInstance().getItems(MasterProject.class);
      for (MasterProject project : projects) {
        project.onRenamed(item, oldName, newName);
      }
    }
  };
}

