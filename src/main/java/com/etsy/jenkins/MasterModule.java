package com.etsy.jenkins;

import com.etsy.jenkins.cli.BuildMasterCommand;
import com.etsy.jenkins.cli.handlers.MasterProjectOptionHandler;

import hudson.model.AbstractBuild;
import hudson.model.Hudson;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.TypeLiteral;
import com.google.inject.assistedinject.FactoryProvider;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

/*package*/ class MasterModule extends AbstractModule {

  protected void configure() {
    bind(Hudson.class).toInstance(Hudson.getInstance());
    bind(BuildWatcher.Factory.class)
        .toProvider(
            FactoryProvider.newFactory(
                BuildWatcher.Factory.class, BuildWatcher.class));
    bind(RebuildWatcher.Factory.class)
        .toProvider(
            FactoryProvider.newFactory(
                RebuildWatcher.Factory.class, RebuildWatcher.class));
    bindConstant().annotatedWith(MasterProject.PingTime.class).to(7000L);

    ExecutorService executor = Executors.newFixedThreadPool(25);
    bind(ExecutorService.class)
        .toInstance(executor);
    bind(Executor.class)
        .toInstance(executor);

    requestStaticInjection(MasterBuild.class);
    requestStaticInjection(MasterProject.class);
    requestStaticInjection(MasterResult.class);
    requestStaticInjection(SubResult.class);
    requestStaticInjection(SubProjectsAction.class);
    requestStaticInjection(SubProjectsJobProperty.class);
    requestStaticInjection(BuildMasterCommand.class);
    requestStaticInjection(MasterProjectOptionHandler.class);
  }
}

