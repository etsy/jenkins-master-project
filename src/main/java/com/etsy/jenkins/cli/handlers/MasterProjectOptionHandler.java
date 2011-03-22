package com.etsy.jenkins.cli.handlers;

import com.etsy.jenkins.MasterProject;
import com.etsy.jenkins.finder.ProjectFinder;

import hudson.model.AbstractProject;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.OptionDef;
import org.kohsuke.args4j.spi.OptionHandler;
import org.kohsuke.args4j.spi.Parameters;
import org.kohsuke.args4j.spi.Setter;

import com.google.inject.Inject;

public class MasterProjectOptionHandler extends OptionHandler<MasterProject> {

  @Inject static ProjectFinder projectFinder;

  public MasterProjectOptionHandler(
      CmdLineParser parser,
      OptionDef option,
      Setter<MasterProject> setter) {
    super(parser, option, setter);
  }

  @Override
  public int parseArguments(Parameters params) throws CmdLineException {
    String src = params.getParameter(0);
    AbstractProject project = projectFinder.findProject(src);
    if (project == null) {
      throw new CmdLineException(
          owner,
          String.format("Project does not exist: %s", src));
    }
    if (!(project instanceof MasterProject)) {
      throw new CmdLineException(
          owner,
          String.format("Project is not a Master Project: %s", src));
    }
    setter.addValue((MasterProject) project);
    return 1;
  }

  @Override
  public String getDefaultMetaVariable() {
    return "MASTER_JOB";
  }
}

