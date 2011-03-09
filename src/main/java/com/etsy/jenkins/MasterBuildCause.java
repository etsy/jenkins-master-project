package com.etsy.jenkins;

import hudson.model.AbstractBuild;
import hudson.model.Cause;
import hudson.model.Run;

public class MasterBuildCause extends Cause.UpstreamCause {

  private final int rebuildNumber;

  public MasterBuildCause(Run<?,?> master) {
    this(master, 0);
  }

  public MasterBuildCause(Run<?,?> master, int rebuildNumber) {
    super(master);
    this.rebuildNumber = rebuildNumber;
  }

  public int getRebuildNumber() {
    return rebuildNumber;
  }
}

