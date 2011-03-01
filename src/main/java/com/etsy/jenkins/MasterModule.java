package com.etsy.jenkins;

import hudson.model.AbstractBuild;
import hudson.model.Hudson;

import com.google.inject.AbstractModule;
import com.google.inject.TypeLiteral;
import com.google.inject.assistedinject.FactoryProvider;

import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Executors;

/*package*/ class MasterModule extends AbstractModule {

  protected void configure() {
    bind(Hudson.class).toInstance(Hudson.getInstance());
    bind(BuildWatcher.Factory.class)
        .toProvider(
            FactoryProvider.newFactory(
                BuildWatcher.Factory.class, BuildWatcher.class));
    bindConstant().annotatedWith(MasterProject.PingTime.class).to(7000L);
    bind(new TypeLiteral<CompletionService<AbstractBuild>>() {})
       .toInstance(
           new ExecutorCompletionService<AbstractBuild>(
               Executors.newCachedThreadPool()));

    requestStaticInjection(MasterBuild.class);
    requestStaticInjection(MasterProject.class);
    requestStaticInjection(MasterResult.class);
    requestStaticInjection(SubResult.class);
  }
}

