package com.linkedin.pinot.controller;

import org.apache.log4j.Logger;
import org.restlet.Application;
import org.restlet.Component;
import org.restlet.Context;
import org.restlet.data.Protocol;
import org.restlet.engine.connector.HttpServerHelper;

import com.linkedin.pinot.controller.api.ControllerRestApplication;
import com.linkedin.pinot.controller.helix.core.PinotHelixResourceManager;


/**
 * @author Dhaval Patel<dpatel@linkedin.com>
 * Sep 24, 2014
 */

public class ControllerStarter {
  private static final Logger logger = Logger.getLogger(ControllerStarter.class);
  private final ControllerConf config;

  private final Component component;
  private final Application controllerRestApp;
  private final PinotHelixResourceManager helixResourceManager;
  private HttpServerHelper h;

  public ControllerStarter(ControllerConf conf) {
    config = conf;
    component = new Component();
    controllerRestApp = new ControllerRestApplication();
    helixResourceManager =
        new PinotHelixResourceManager(config.getZkStr(), config.getHelixClusterName(), config.getControllerHost() + "_"
            + config.getControllerPort());
  }

  public void start() {
    //final org.restlet.Server s = component.getServers().add(Protocol.HTTP, Integer.parseInt(config.getControllerPort()));
    //    s.getContext().getParameters().add("", "");
    //    s.getContext().getParameters().add("", "");
    //    s.getContext().getParameters().add("", "");
    //    s.getContext().getParameters().add("", "");
    //    s.getContext().getParameters().add("", "");
    //    s.getContext().getParameters().add("", "");

    final Context applicationContext = component.getContext().createChildContext();

    logger.info("injecting conf and resource manager to the api context");
    applicationContext.getAttributes().put(ControllerConf.class.toString(), config);
    applicationContext.getAttributes().put(PinotHelixResourceManager.class.toString(), helixResourceManager);

    controllerRestApp.setContext(applicationContext);
    component.getDefaultHost().attach(controllerRestApp);

    final org.restlet.Server s1 =
        new org.restlet.Server(applicationContext, Protocol.HTTP, Integer.parseInt(config.getControllerPort()), component);

    h = new HttpServerHelper(s1);

    try {
      logger.info("starting pinot helix resource manager");
      helixResourceManager.start();

      logger.info("************************************** starting api component");
      h.start();
    } catch (final Exception e) {
      logger.error(e);
      throw new RuntimeException(e);
    }
  }

  public void stop() {
    try {
      logger.info("stopping api component");
      h.stop();

      logger.info("stopping resource manager");
      helixResourceManager.stop();

    } catch (final Exception e) {
      logger.error(e);
    }
  }

  public static void main(String[] args) throws InterruptedException {
    final ControllerConf conf = new ControllerConf();
    conf.setControllerHost("localhost");
    conf.setControllerPort("8998");
    conf.setDataDir("/tmp/PinotController");
    conf.setZkStr("localhost:2121");
    conf.setHelixClusterName("sprintDemoClusterOne");
    final ControllerStarter starter = new ControllerStarter(conf);
    starter.start();

    Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
      @Override
      public void run() {
        starter.stop();
      }
    }));
  }
}
