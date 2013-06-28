package com.etsy.jenkins;

import com.etsy.jenkins.finder.ProjectFinder;

import hudson.Extension;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractProject;
import hudson.model.AutoCompletionCandidates;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.model.ItemGroup;
import hudson.model.Item;
import hudson.model.Project;
import hudson.model.TopLevelItem;
import hudson.model.TopLevelItemDescriptor;
import hudson.model.listeners.ItemListener;
import hudson.plugins.ircbot.IrcPublisher;
import hudson.plugins.postbuildtask.PostbuildTask;
import hudson.security.AccessControlled;
import hudson.security.Permission;
import hudson.tasks.Builder;
import hudson.tasks.Messages;
import hudson.tasks.Publisher;
import hudson.util.FormValidation;

import au.com.centrumsystems.hudson.plugin.buildpipeline.trigger.BuildPipelineTrigger;

import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;

import org.apache.commons.lang.StringUtils;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.inject.BindingAnnotation;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;

import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import javax.servlet.ServletException;

public class MasterProject
extends Project<MasterProject, MasterBuild>
implements TopLevelItem {

  @BindingAnnotation 
  @Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD }) 
  @Retention(RetentionPolicy.RUNTIME)
  public @interface PingTime {}

  /*package*/ final Set<String> jobNames;

  private String includeRegex;

  private transient Pattern includePattern;

  @Inject static Hudson hudson;
  @Inject static ProjectFinder projectFinder;
  @Inject static Provider<MasterBuilder> masterBuilderProvider;

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

  /*package*/ 
  void onDeleted(Item item) {
    jobNames.remove(item.getName());
  }

  /*package*/ 
  void onRenamed(Item item, String oldName, String newName) {
    if ((newName != null) && jobNames.remove(oldName)) {
      jobNames.add(newName);
    }
  }

  @Override
  public List<Builder> getBuilders() {
    return Lists.<Builder>newArrayList(masterBuilderProvider.get());
  }

  public List<Descriptor<Publisher>> getPotentialPublisherDescriptors() {
    List<Descriptor<Publisher>> publishers = 
        Lists.<Descriptor<Publisher>>newArrayList(MasterMailer.DESCRIPTOR);
    if (hudson.getPlugin("ircbot") != null) {
      publishers.add(IrcPublisher.DESCRIPTOR);
    }
    if (hudson.getPlugin("postbuild-task") != null) {
      publishers.add(hudson.getPublisher("PostbuildTask"));
    }
    if (hudson.getPlugin("build-pipeline-plugin") != null) {
      publishers.add(hudson.getPublisher("BuildPipelineTrigger"));
    }

    return publishers;
  }

  @Override
  protected Class<MasterBuild> getBuildClass() {
    return MasterBuild.class;
  }

  public void doRebuild(StaplerRequest req, StaplerResponse res) 
      throws IOException, ServletException {
    checkPermission(Permission.READ);

    AbstractProject subProject = this.getSubProject(req);
    MasterBuild masterBuild = this.getMasterBuild(req);
    masterBuild.rebuild(subProject);
    res.forward(masterBuild, masterBuild.getUrl(), req);
  }

  private AbstractProject getSubProject(StaplerRequest req)
      throws ServletException {
    String subProjectName = req.getParameter("subProject");
    if (subProjectName == null) {
      throw new ServletException(
          "Must provide a 'subProject' name parameter.");
    }

    AbstractProject subProject = projectFinder.findProject(subProjectName);
    if (subProject == null) {
      throw new ServletException("Project does not exist: " + subProjectName);
    }

    return subProject;
  }

  private MasterBuild getMasterBuild(StaplerRequest req)
      throws ServletException {
    String buildNumberString = req.getParameter("number");
    if (buildNumberString == null) {
      throw new ServletException(
          "Must provide a master build 'number' parameter.");
    }

    int buildNumber = -1;
    try {
      buildNumber = Integer.parseInt(buildNumberString);
    } catch (NumberFormatException e) {
      throw new ServletException(
         "Invalid 'number' parameter: " + buildNumberString);
    }

    return (MasterBuild) this.getBuildByNumber(buildNumber);
  }

  public FormValidation doCheck(
      @AncestorInPath AccessControlled subject,
      @QueryParameter String value) {
    if (!subject.hasPermission(Item.CONFIGURE)) return FormValidation.ok();
    StringTokenizer tokenizer = new StringTokenizer(value);
    while (tokenizer.hasMoreTokens()) {
      String projectName = tokenizer.nextToken().trim();
      if (StringUtils.isNotBlank(projectName)) {
        Item item = hudson.getItemByFullName(projectName, Item.class);
        if (item == null) {
          return FormValidation.error(
              Messages.BuildTrigger_NoSuchProject(
                  projectName, 
                  AbstractProject.findNearest(projectName).getName()));
        }
        if (!(item instanceof AbstractProject)) {
          return FormValidation.error(
              Messages.BuildTrigger_NotBuildable(projectName));
        }
        if (!contains((TopLevelItem) item)) {
          return FormValidation.error(
              String.format(
                  "%s is not a sub-job of this master project.",
                  projectName));
        }
      }
    }
    return FormValidation.ok();
  }

  public AutoCompletionCandidates doAutoCompleteSubProjects(
      @QueryParameter String value) {
    AutoCompletionCandidates candidates = new AutoCompletionCandidates();
    Set<AbstractProject> projects = getSubProjects();
    for (AbstractProject project : projects) {
      if (project.getFullName().startsWith(value)) {
        if (project.hasPermission(Item.READ)) {
          candidates.add(project.getFullName());
        }
      }
    }
    return candidates;
  }

  @Override
  protected void submit(StaplerRequest req, StaplerResponse res) 
      throws IOException, ServletException, Descriptor.FormException {
  
    // Handle the job list
    jobNames.clear();
    
    // Include Regex project names
    if (req.getParameter("useincluderegex") != null) {
    	System.out.println("outputting the useincluderegex: " + req.getParameter("useincluderegex"));
        includeRegex = Util.nullify(req.getParameter("includeRegex"));
        if (includeRegex == null)
            includePattern = null;
        else
            includePattern = Pattern.compile(includeRegex);
    } else {
        includeRegex = null;
        includePattern = null;
    }
    
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
  
  public String getIncludeRegex() {
      return includeRegex;
  }

  public Set<AbstractProject> getSubProjects() {
	  
	if (includePattern != null) {
	  for (Item item : Hudson.getInstance().getItems()) {
	    String itemName = item.getName();
	    if (includePattern.matcher(itemName).matches()) {
	      jobNames.add(itemName);
	    }
	  }
	}
	  
    Set<AbstractProject> subProjects = Sets.<AbstractProject>newLinkedHashSet();
    for (String jobName : jobNames) {
      subProjects.add(projectFinder.findProject(jobName));
    }
    return subProjects;
  }

  public Set<String> getSubProjectNames() {
    return jobNames;
  }

  @Extension
  public static final TopLevelItemDescriptor DESCRIPTOR = 
      new TopLevelItemDescriptor() {

    private Injector injector = Guice.createInjector(new MasterModule());

    @Override
    public String getDisplayName() {
      return "Master Project";
    }
    
//    public FormValidation doCheckIncludeRegex( @QueryParameter String value ) throws IOException, ServletException, InterruptedException  {
//        String v = Util.fixEmpty(value);
//        if (v != null) {
//            try {
//                Pattern.compile(v);
//            } catch (PatternSyntaxException pse) {
//                return FormValidation.error(pse.getMessage());
//            }
//        }
//        return FormValidation.ok();
//    }

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
