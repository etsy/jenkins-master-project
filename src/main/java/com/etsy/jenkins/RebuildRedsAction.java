package com.etsy.jenkins;

import hudson.model.Action;

public class RebuildRedsAction implements Action {

  private int maxRetries;

  public RebuildRedsAction(int maxRetries) {
    this.maxRetries = maxRetries;
  }

  public String getDisplayName() {
    return "Rebuild Reds";
  }

  public String getIconFileName() {
    return null;
  }

  public String getUrlName() {
    return "rebuildReds";
  }

  public int getMaxRetries() {
    return this.maxRetries;
  }
}

