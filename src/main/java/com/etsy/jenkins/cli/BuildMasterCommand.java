package com.etsy.jenkins.cli;

import com.etsy.jenkins.MasterProject;
import com.etsy.jenkins.cli.handlers.MasterProjectOptionHandler;
import com.etsy.jenkins.cli.handlers.ProxyUserOptionHandler;

import hudson.AbortException;
import hudson.Extension;
import hudson.cli.CLICommand;
import hudson.console.HyperlinkNote;
import hudson.model.AbstractBuild;
import hudson.model.Cause;
import hudson.model.Hudson;
import hudson.model.Item;
import hudson.model.ParametersAction;
import hudson.model.ParameterValue;
import hudson.model.ParameterDefinition;
import hudson.model.ParametersDefinitionProperty;
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

import java.io.PrintStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

@Extension
public class BuildMasterCommand extends CLICommand {

  @Override
  public String getShortDescription() {
    return "Builds a master project,"
        + " and optionally waits until its completion.";
  }

  @Argument(
      metaVar="MASTER_JOB", 
      usage="Name of the master project to build.", 
      required=true,
      handler=MasterProjectOptionHandler.class,
      index=0)
  public MasterProject masterProject;

  @Argument(
      metaVar="PROXY_USER",
      usage="LDAP of user that this build is being triggered for.",
      required=false,
      handler=ProxyUserOptionHandler.class,
      index=1)
  public User user = User.getUnknown();

  @Option(
      name="-d",
      usage="Description to be added to this build.")
  public String description = "";

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
    masterProject.checkPermission(Item.BUILD);

    ParametersAction a = null;
    if (!parameters.isEmpty()) {
      ParametersDefinitionProperty pdp = 
          masterProject.getProperty(ParametersDefinitionProperty.class);
      if (pdp == null) {
        throw new AbortException(
            String.format(
                "%s is not parameterized but the -p option was specified.",
                masterProject.getDisplayName()));
      }

      List<ParameterValue> values = Lists.<ParameterValue>newArrayList();

      for (Map.Entry<String, String> e : parameters.entrySet()) {
        String name = e .getKey();
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

     User authUser = User.get(Hudson.getAuthentication().getName());
     if (authUser == null) {
       authUser = User.getUnknown();
     }
     
     Cause cause = new CLICause(authUser, user, description);
     Future<? extends AbstractBuild> f = 
         masterProject.scheduleBuild2(0, cause, a);
     if (!sync) return 0;

     AbstractBuild b = f.get();  // wait for completion
     stdout.println(
         String.format(
             "Completed %s : %s",
             b.getFullDisplayName(),
             b.getResult()));
     return b.getResult().ordinal;
  }

  @Override
  protected void printUsageSummary(PrintStream stderr) {
    stderr.println(
        "Starts a master project build, and optionally waits for a"
        + " completion.\n\n"
        + "Aside from general scripting use, this command can be\n"
        + "used to invoke another job from within a build of one job.\n"
        + "With the -s option, this command changes the exit code based on\n"
        + "the outcome of the build (exit code 0 indicates success.)\n");
  }

  public static class CLICause extends Cause {

    private final User authUser;
    private final User proxyUser;
    private final String description;

    public CLICause(User authUser, User proxyUser, String description) {
      this.authUser = authUser;
      this.proxyUser = proxyUser;
      this.description = description;
    }

    @Exported(visibility=3)
    public String getAuthUserLink() {
      return HyperlinkNote.encodeTo(
          authUser.getAbsoluteUrl(),
          authUser.getDisplayName());
    }

    @Exported(visibility=3)
    public String getProxyUserLink() {
      return HyperlinkNote.encodeTo(
          proxyUser.getAbsoluteUrl(),
          proxyUser.getDisplayName());
    }

    @Exported(visibility=3)
    public String getDescription() {
      return this.description;
    }

    @Override
    public String getShortDescription() {
      return String.format(
          "Started by command line by %s for %s\nDetails: %s\n",
           getAuthUserLink(), getProxyUserLink(), getDescription());
    }

    @Override
    public void print(TaskListener listener) {
        listener.getLogger().println(getShortDescription());
    }

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof CLICause)) {
        return false;
      }
      CLICause other = (CLICause) o;
      return Objects.equal(this.authUser, other.authUser)
          && Objects.equal(this.proxyUser, other.proxyUser)
          && Objects.equal(this.description, other.description);
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(this.authUser, this.proxyUser, this.description);
    }
  }
}

