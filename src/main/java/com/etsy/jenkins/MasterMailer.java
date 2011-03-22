package com.etsy.jenkins;

import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Hudson;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Mailer;
import hudson.tasks.MailMessageIdAction;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;

import org.kohsuke.stapler.StaplerRequest;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import org.apache.commons.jelly.JellyContext;
import org.apache.commons.jelly.XMLOutput;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import javax.mail.Address;
import javax.mail.Message;
import javax.mail.Transport;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

public class MasterMailer extends Notifier {

  public String recipients;
  public String preamble;
  public Map<String, String> links;

  public MasterMailer(
      String recipients,
      String preamble,
      Map<String, String> links) {
    this.recipients = recipients;
    this.preamble = preamble;
    this.links = links;
  }

  @Override
  public boolean prebuild(AbstractBuild build, BuildListener listener) {
    MasterBuild master = (MasterBuild) build;
    EnvVars env = getEnvironment(master, listener);
    if (env == null) {
      return false;
    }

    JellyContext context = new JellyContext();
    context.setVariable("master", master);
    String subject = compileJelly(context, 
        "prebuild_email_subject.jelly");

    context = new JellyContext();
    context.setVariable("subject", subject);
    context.setVariable("preamble", env.expand(preamble));
    String body = compileJelly(context, 
        "prebuild_email_body.jelly");
  
    try { 
      MimeMessage message = sendMail(master, subject, body, listener);
      build.addAction(new MailMessageIdAction(message.getMessageID()));
    } catch (Exception e) {
      e.printStackTrace(listener.error(e.getMessage()));
      return false;
    }
    return true;
  }

  @Override
  public boolean perform(
      AbstractBuild build,
      Launcher launcher,
      BuildListener listener) throws InterruptedException, IOException {
    return sendResultEmail((MasterBuild) build, listener);
  }

  public void sendResultEmail(MasterBuild build) {
    sendResultEmail(build, TaskListener.NULL);
  }

  private boolean sendResultEmail(MasterBuild build, TaskListener listener) {
    EnvVars env = getEnvironment(build, listener);
    if (env == null) {
      return false;
    }
    
    MasterBuild master = (MasterBuild) build;
    JellyContext context = new JellyContext();
    context.setVariable("master", master);
    String subject = compileJelly(context, 
        "result_email_subject.jelly");

    context = new JellyContext();
    context.setVariable("subject", subject);
    context.setVariable("preamble", env.expand(preamble));
    context.setVariable("master", master);
    context.setVariable("links", links);
    context.setVariable("rootURL", Hudson.getInstance().getRootUrl());
    String body = compileJelly(context, 
        "result_email_body.jelly");

    try {
      sendMail(build, subject, body, listener);
    } catch (Exception e) {
      e.printStackTrace(listener.error(e.getMessage()));
      return false;
    }

    return true;
  }

  private EnvVars getEnvironment(
      MasterBuild build, TaskListener listener) {
    try {
      return build.getEnvironment(listener);
    } catch (Exception e) {
      e.printStackTrace(listener.error(e.getMessage()));
    }
    return null;
  }

  private MimeMessage sendMail(
      MasterBuild build, String subject, String body, TaskListener listener) 
      throws Exception {
    MimeMessage message = new MimeMessage(Mailer.descriptor().createSession());
    Address from = 
        createAddress(Mailer.descriptor().getAdminAddress(), listener);
    message.setFrom(from);
    message.setReplyTo(new Address[] { from });
    message.setRecipients(
        Message.RecipientType.TO, getRecipients(build, listener));
    message.setSubject(subject);
    message.setText(body, "iso-8859-1", "html");
    
    MailMessageIdAction action = build.getAction(MailMessageIdAction.class);
    if (action != null) {
      String messageId = action.messageId;
      message.setHeader("In-Reply-To", messageId);
      message.setHeader("References", messageId);
    }
    Transport.send(message);
    return message;
  }

  private Address[] getRecipients(MasterBuild build, TaskListener listener) {
    EnvVars env = getEnvironment(build, listener);
    List<Address> addresses = Lists.<Address>newArrayList();
    StringTokenizer tokenizer = new StringTokenizer(recipients);
    while (tokenizer.hasMoreTokens()) {
      String address = env.expand(tokenizer.nextToken());
      Address add = createAddress(address, listener);
      if (add != null) {
        addresses.add(add);
      }
    }
    return addresses.toArray(new Address[addresses.size()]);
  }

  private Address createAddress(String address, TaskListener listener) {
    try {
      return new InternetAddress(address);
    } catch (AddressException e) {
      e.printStackTrace(listener.error(e.getMessage()));
    }
    return null;
  }

  private String compileJelly(JellyContext context, String template) {
    StringWriter writer = new StringWriter();
    try {
      XMLOutput xmlOutput = XMLOutput.createXMLOutput(writer);
      String url = this.getClass()
          .getResource("MasterMailer.class").toString();
      url = url.substring(0, url.lastIndexOf(".class"));
      context.runScript(url + "/" + template, xmlOutput);
      xmlOutput.flush();
    } catch (Exception e) {
      e.printStackTrace(new PrintWriter(writer));
    }

    return writer.toString();
  }

  @Override
  public BuildStepMonitor getRequiredMonitorService() {
    return BuildStepMonitor.NONE;
  }

  @Override
  public BuildStepDescriptor<Publisher> getDescriptor() {
    return DESCRIPTOR;
  }

  @Extension
  public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

  public static class DescriptorImpl extends BuildStepDescriptor<Publisher> {

    @Override
    public Publisher newInstance(StaplerRequest req, JSONObject formData) {
      String recipients = formData.getString("recipients");
      String preamble = formData.getString("preamble");
      JSONArray linksData = formData.optJSONArray("links");
      Map<String, String> links = Maps.<String, String>newHashMap();
      if (linksData != null) {
        for (int i=0; i < linksData.size(); i++) {
          JSONObject linkData = linksData.getJSONObject(i);
          if (linkData != null) {
            links.put(linkData.getString("path"), linkData.getString("text"));
          }
        }
      } else {
        JSONObject linkData = formData.optJSONObject("links");
        if (linkData != null) {
          links.put(linkData.getString("path"), linkData.getString("text"));
        }
      }
      return new MasterMailer(recipients, preamble, links);
    }

    @Override
    public String getDisplayName() {
      return "Master Mailer";
    }

    @Override
    public boolean isApplicable(Class<? extends AbstractProject> jobType) {
      return jobType.equals(MasterProject.class);
    }
  }
}

