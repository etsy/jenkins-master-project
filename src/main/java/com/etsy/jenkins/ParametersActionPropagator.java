package com.etsy.jenkins;

import hudson.model.AbstractProject;
import hudson.model.FileParameterValue;
import hudson.model.JobProperty;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;

import com.google.common.collect.Lists;

import java.io.File;
import java.util.List;

public class ParametersActionPropagator {

  public ParametersAction[] getPropagatedActions(
      MasterBuild masterBuild,
      AbstractProject subProject) {
    List<ParametersAction> parametersActionList = 
        masterBuild.getActions(ParametersAction.class);
    List<ParametersAction> propagatedParametersActionList =
        Lists.<ParametersAction>newArrayList();
    for (ParametersAction action : parametersActionList) {
      ParametersAction propagated = 
          getPropagatedAction(masterBuild, subProject, action);
      propagatedParametersActionList.add(propagated);
    }

    return propagatedParametersActionList.toArray(
        new ParametersAction[propagatedParametersActionList.size()]);
  }

  public ParametersAction getPropagatedAction(
      MasterBuild masterBuild,
      AbstractProject subProject, 
      ParametersAction action) {
    ParametersDefinitionProperty pdp = (ParametersDefinitionProperty)
        subProject.getProperty(ParametersDefinitionProperty.class);
    if (pdp == null) {
        return null; // This project does not have parameters
    }

    List<ParameterValue> values = Lists.<ParameterValue>newArrayList();
    for (ParameterValue value : action.getParameters()) {
      String name = value.getName();
      ParameterDefinition pd = pdp.getParameterDefinition(name);
      if (pd == null) {
        continue; // This project does not have this parameter
      }

      // File parameters are special
      if (value instanceof FileParameterValue) {
        final String location = pd.getName();
        File file = new File(
            masterBuild.getRootDir(), 
            "fileParameters/" + location);
        String originalFileName = 
            ((FileParameterValue) value).getOriginalFileName();
        value = new PropagatedFileParameterValue(
            location, file, originalFileName);
      }
      values.add(value);
    }

    return new ParametersAction(values); 
  }
}

