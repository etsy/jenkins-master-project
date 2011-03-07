package com.etsy.jenkins.cli.handlers;

import com.etsy.jenkins.MasterProject;

import hudson.model.Hudson;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.OptionDef;
import org.kohsuke.args4j.spi.OptionHandler;
import org.kohsuke.args4j.spi.Parameters;
import org.kohsuke.args4j.spi.Setter;

public class MasterProjectOptionHandler extends OptionHandler<MasterProject> {

  public MasterProjectOptionHandler(
      CmdLineParser parser,
      OptionDef option,
      Setter<MasterProject> setter) {
    super(parser, option, setter);
  }

  @Override
  public int parseArguments(Parameters params) throws CmdLineException {
    Hudson hudson = Hudson.getInstance();
    String src = params.getParameter(0);

    MasterProject master = hudson.getItemByFullName(src, MasterProject.class);
    if (master == null) {
      throw new CmdLineException(
          owner,
          String.format("Not a master job: %s", src));
    }
    setter.addValue(master);
    return 1;
  }

  @Override
  public String getDefaultMetaVariable() {
    return "MASTER_JOB";
  }
}

