package com.etsy.jenkins.cli;

import com.etsy.jenkins.MasterProject;
import com.etsy.jenkins.SubProjectsAction;
import com.etsy.jenkins.SubProjectsJobProperty;
import com.etsy.jenkins.cli.handlers.MasterProjectOptionHandler;
import com.etsy.jenkins.finder.BuildFinder;
import com.etsy.jenkins.finder.ProjectFinder;

import hudson.AbortException;
import hudson.EnvVars;
import hudson.Extension;
import hudson.cli.CLICommand;
import hudson.console.HyperlinkNote;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Cause;
import hudson.model.EnvironmentContributor;
import hudson.model.Hudson;
import hudson.model.Item;
import hudson.model.ParametersAction;
import hudson.model.ParameterValue;
import hudson.model.ParameterDefinition;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.TopLevelItem;
import hudson.model.User;
import hudson.util.EditDistance;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;
import org.kohsuke.stapler.export.Exported;

import com.google.common.base.Objects;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.inject.Inject;

import java.io.PrintStream;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;

@Extension
public class BuildMasterCommand extends CLICommand {

  @Inject static Hudson hudson;
  @Inject static BuildFinder buildFinder;
  @Inject static ProjectFinder projectFinder;

  @Override
  public String getShortDescription() {
    return "Builds a try job,"
        + " and optionally waits until its completion.";
  }

  @Argument(
      handler=MasterProjectOptionHandler.class,
      metaVar="MASTER_JOB", 
      usage="Name of the job to build", 
      required=true)
  public MasterProject job;

  @Argument(
      metaVar="SUB_JOBS",
      usage="List of sub jobs to execute. If Master Project plugin is"
          + " installed and the JOB is a Master Project.",
      index=1,
      multiValued=true)
  public List<String> subJobs = Lists.<String>newArrayList();

  @Option(
      name="-s",
      usage="Wait until the completion/abortion of the command.")
  public boolean sync = false;

  @Option(
      name="-p",
      usage="Specify the build parameters in the key=value format.")
  public Map<String, String> parameters = Maps.<String, String>newHashMap();

  @Override
  protected int run() throws Exception {
    job.checkPermission(Item.BUILD);

    SubProjectsAction sp = null;
    SubProjectsJobProperty spjp = (SubProjectsJobProperty)
        job.getProperty(SubProjectsJobProperty.class);
    if (!subJobs.isEmpty()) {
      if (spjp == null) {
        throw new AbortException(
            String.format(
                "%s does not allow sub-job selection but sub-jobs were"
                + " specified.",
                job.getDisplayName()));
      }
      Set<String> subProjects = Sets.<String>newHashSet();
      for (String subJob : subJobs) {
        AbstractProject project = projectFinder.findProject(subJob);
        if (project == null) {
          throw new AbortException(
            String.format(
                "Project does not exist: %s",
                subJob));
        }
        if (!job.contains((TopLevelItem) project)) {
          throw new AbortException(
              String.format(
                  "%s does not exist in Master Project: %s",
                  subJob,
                  job.getDisplayName()));
        }
        subProjects.add(subJob);
      }
      sp = new SubProjectsAction(subProjects);
    } else if (spjp != null) {
      stdout.println(
          String.format(
              "Executing DEFAULT sub-jobs: %s", 
              spjp.getDefaultSubProjectsString()));
    }

    ParametersAction a = null;
    if (!parameters.isEmpty()) {
      ParametersDefinitionProperty pdp = 
          job.getProperty(ParametersDefinitionProperty.class);
      if (pdp == null) {
        throw new AbortException(
            String.format(
                "%s is not parameterized but the -p option was specified.",
                job.getDisplayName()));
      }

      List<ParameterValue> values = Lists.<ParameterValue>newArrayList();

      for (Map.Entry<String, String> e : parameters.entrySet()) {
        String name = e.getKey();
        ParameterDefinition pd = pdp.getParameterDefinition(name);
        if (pd == null) {
          throw new AbortException(
              String.format(
                  "\'%s\' is not a valid parameter. Did you mean %s?",
                  name,
                  EditDistance.findNearest(
                      name,
                      pdp.getParameterDefinitionNames())));
         }
         values.add(pd.createValue(this, e.getValue()));
       }

       a = new ParametersAction(values);
     }

     String user = Hudson.getAuthentication().getName();
     
     CLICause cause = new CLICause(user, a, sp);
     Future<? extends AbstractBuild> f = 
         job.scheduleBuild2(0, cause, a, sp);

     AbstractBuild build = null;
     do {
       build = buildFinder.findBuild(job, cause);
       stdout.println(
           String.format("......... %s ( pending )", job.getDisplayName()));
       rest();
     } while(build == null);

     stdout.println(
         String.format("......... %s ( %s%s%s )", 
          job.getDisplayName(),
          hudson.getRootUrl(),
          build.getUrl(),
          "console"));
     build.setDescription("<h2>" + cause.getUserName() + "</h2>");

     if (!sync) return 0;

     AbstractBuild b = f.get();  // wait for completion
     stdout.println(
         String.format(
             "Completed %s : %s",
             b.getFullDisplayName(),
             b.getResult()));
     return b.getResult().ordinal;
  }

  private void rest() {
    try {
      Thread.sleep(7000L);
    } catch (InterruptedException ignore) {}
  }

  @Override
  protected void printUsageSummary(PrintStream stderr) {
    stderr.println(
        "Starts a master job, and optionally waits for a completion.\n\n"
        + "Aside from general scripting use, this command can be\n"
        + "used to invoke another job from within a build of one job.\n"
        + "With the -s option, this command changes the exit code based on\n"
        + "the outcome of the build (exit code 0 indicates success.)\n");
  }

  public static class CLICause extends Cause {

    private final String user;
    private final ParametersAction parameters;
    private final SubProjectsAction subProjects;

    public CLICause(
        String user,
        ParametersAction parameters,
        SubProjectsAction subProjects) {
      this.user = user;
      this.parameters = parameters;
      this.subProjects = subProjects;
    }

    @Exported(visibility=3)
    public String getUserName() {
      User u = User.get(user, false);
      return (u == null) ? user : u.getDisplayName();
    }

    @Exported(visibility=3)
    public String getUserUrl() {
      User u = User.get(user, false);
      if (u == null) {
        u = User.getUnknown();
      }
      return u.getAbsoluteUrl();
    }

    @Override
    public String getShortDescription() {
      return "Started by command line";
    }

    @Override
    public void print(TaskListener listener) {
        listener.getLogger().println(String.format(
          "Started by command line for %s\n",
           HyperlinkNote.encodeTo(getUserUrl(), getUserName()))); 
    }

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof CLICause)) {
        return false;
      }
      CLICause other = (CLICause) o;
      return Objects.equal(this.user, other.user)
          && Objects.equal(this.parameters, other.parameters)
          && Objects.equal(this.subProjects, other.subProjects);
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(this.user, this.parameters, this.subProjects);
    }
  }

  @Extension
  public static class CLIEnvironmentContributor
  extends EnvironmentContributor {

    @Override
    public void buildEnvironmentFor(Run r, EnvVars envs, TaskListener listener) {
      CLICause cause = (CLICause) r.getCause(CLICause.class);
      if (cause == null) return;
      envs.put("TRIGGERING_USER", cause.getUserName());
    }
  }
}

