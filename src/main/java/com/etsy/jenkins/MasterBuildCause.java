package com.etsy.jenkins;

import hudson.model.AbstractBuild;
import hudson.model.Cause;
import hudson.model.Run;

public class MasterBuildCause extends Cause.UpstreamCause {

  public MasterBuildCause(Run<?,?> master) {
    super(master);
  }
}

