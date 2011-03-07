package com.etsy.jenkins.cli.handlers;

import hudson.model.User;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.OptionDef;
import org.kohsuke.args4j.spi.OptionHandler;
import org.kohsuke.args4j.spi.Parameters;
import org.kohsuke.args4j.spi.Setter;

public class ProxyUserOptionHandler extends OptionHandler<User> {

  public ProxyUserOptionHandler(
      CmdLineParser parser,
      OptionDef option,
      Setter<User> setter) {
    super(parser, option, setter);
  }

  @Override
  public int parseArguments(Parameters params) throws CmdLineException {
    String src = params.getParameter(0);
    User user = User.get(src);
    if (user == null) {
      throw new CmdLineException(
          owner,
          String.format("User does not exist: %s", src));
    }
    setter.addValue(user);
    return 1;
  }

  @Override
  public String getDefaultMetaVariable() {
    return "PROXY_USER";
  }
}

