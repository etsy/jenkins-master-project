package com.etsy.jenkins;

import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Environment;
import hudson.model.FileParameterValue;
import hudson.model.FileParameterValue.FileItemImpl;
import hudson.tasks.BuildWrapper;

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import com.google.common.base.Strings;

import org.apache.commons.fileupload.FileItem;

import java.io.File;
import java.io.IOException;
import javax.servlet.ServletException;

public class PropagatedFileParameterValue
extends FileParameterValue {

  private FileItem file;

  public PropagatedFileParameterValue(
      String name, File file, String originalName) {
    super(name, file, originalName);
    this.file = new FileItemImpl(file);
  }

  @Override
  public BuildWrapper createBuildWrapper(AbstractBuild<?,?> build) {
    return new BuildWrapper() {
      @Override
      public Environment setUp(
          AbstractBuild build,
          Launcher launcher,
          BuildListener listener)
          throws IOException, InterruptedException {
        if (!Strings.isNullOrEmpty(getName())) {
          listener.getLogger().println("Copying file to " + getName());
          FilePath locationFilePath = build.getWorkspace().child(getName());
          locationFilePath.getParent().mkdirs();
          locationFilePath.copyFrom(file);
          file = null;
          locationFilePath.copyTo(new FilePath(getLocationUnderBuild(build)));
        } 
        return new Environment(){};
      }
    };
  }

  @Override
  public void doDynamic(StaplerRequest req, StaplerResponse res)
      throws ServletException, IOException {
    if (("/" + getOriginalFileName()).equals(req.getRestOfPath())) {
      AbstractBuild build = (AbstractBuild) req
          .findAncestor(AbstractBuild.class)
          .getObject();
      File fileParameter = getLocationUnderBuild(build);
      if (fileParameter.isFile()) {
        res.serveFile(req, fileParameter.toURI().toURL());
      }
    }
  }

  protected File getLocationUnderBuild(AbstractBuild build) {
    return new File(build.getRootDir(), "fileParameters/" + getName());
  }
}

