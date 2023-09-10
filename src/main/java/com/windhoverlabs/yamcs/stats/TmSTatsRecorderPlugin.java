package com.windhoverlabs.yamcs.stats;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.yamcs.Plugin;
import org.yamcs.PluginException;
import org.yamcs.Spec;
import org.yamcs.Spec.OptionType;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.events.EventProducer;
import org.yamcs.events.EventProducerFactory;
import org.yamcs.http.HttpServer;
import org.yamcs.logging.Log;

public class TmSTatsRecorderPlugin implements Plugin {
  private static final Log log = new Log(TmSTatsRecorderPlugin.class);
  private String registry;

  @Override
  public Spec getSpec() {
    Spec spec = new Spec();
    spec.addOption("registry", OptionType.STRING);
    return spec;
  }

  @Override
  public void onLoad(YConfiguration config) throws PluginException {
    YamcsServer yamcs = YamcsServer.getServer();

    EventProducer eventProducer =
        EventProducerFactory.getEventProducer(null, this.getClass().getSimpleName(), 10000);

    eventProducer.sendInfo("TmSTatsRecorderPlugin loaded");
    List<HttpServer> httpServers = yamcs.getGlobalServices(HttpServer.class);
    if (httpServers.isEmpty()) {
      log.warn(
          "Yamcs does not appear to be running an HTTP Server. The CfsApi was not initialized.");
      return;
    }

    HttpServer httpServer = httpServers.get(0);
    // Add API to server
    try (InputStream in = getClass().getResourceAsStream("/yamcs-tm-stats-recorder.protobin")) {
      httpServer.getProtobufRegistry().importDefinitions(in);
    } catch (IOException e) {
      throw new PluginException(e);
    }

    httpServer.addApi(new TmStatsRecorderApi());
  }

  public String getRegistry() {
    return registry;
  }

  /**
   * The workspace directory is the directory that holds all of our yamcs configuration.
   *
   * @return
   */
  public String getWorkspaceDir() {
    return System.getProperty("user.dir");
  }
}
