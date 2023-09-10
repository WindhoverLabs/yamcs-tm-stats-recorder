package com.windhoverlabs.yamcs.stats;

// This has to be inside of org.yamcs.http.api in order to access MdbApi.verifyParameterId
import com.windhoverlabs.yamcs.stats.api.AbstractTmStatsRecorderApi;
import com.windhoverlabs.yamcs.stats.api.TMStatsRecorderActivateRequest;
import com.windhoverlabs.yamcs.stats.api.TMStatsRecorderConfig;
import com.windhoverlabs.yamcs.stats.api.TMStatsRecorderDeactivateRequest;
import org.yamcs.YamcsServer;
import org.yamcs.YamcsServerInstance;
import org.yamcs.api.Observer;
import org.yamcs.http.Context;

public class TmStatsRecorderApi extends AbstractTmStatsRecorderApi<Context> {

  @Override
  public void activateTMStatsRecorder(
      Context ctx,
      TMStatsRecorderActivateRequest request,
      Observer<TMStatsRecorderConfig> observer) {
    YamcsServerInstance instance = YamcsServer.getServer().getInstance(request.getInstance());
    TmStatsRecorder recorder = (TmStatsRecorder) instance.getService(request.getServiceName());
    recorder.setActive(true);
    observer.complete(TMStatsRecorderConfig.newBuilder().build());
  }

  @Override
  public void deactivateTMStatsRecorder(
      Context ctx,
      TMStatsRecorderDeactivateRequest request,
      Observer<TMStatsRecorderConfig> observer) {
    YamcsServerInstance instance = YamcsServer.getServer().getInstance(request.getInstance());
    TmStatsRecorder recorder = (TmStatsRecorder) instance.getService(request.getServiceName());
    recorder.setActive(false);
    observer.complete(TMStatsRecorderConfig.newBuilder().build());
  }
}
