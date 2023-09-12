/****************************************************************************
 *
 *   Copyright (c) 2022 Windhover Labs, L.L.C. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 * 3. Neither the name Windhover Labs nor the names of its
 *    contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS
 * OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
 * ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 *
 *****************************************************************************/

package com.windhoverlabs.yamcs.stats;

import com.csvreader.CsvWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.yamcs.AbstractYamcsService;
import org.yamcs.InitException;
import org.yamcs.Spec;
import org.yamcs.Spec.OptionType;
import org.yamcs.ValidationException;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.client.Helpers;
import org.yamcs.events.EventProducer;
import org.yamcs.events.EventProducerFactory;
import org.yamcs.mdb.ProcessingStatistics;
import org.yamcs.protobuf.TmStatistics;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.yarch.FileSystemBucket;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;

public class TmStatsRecorder extends AbstractYamcsService implements Runnable {

  /* Internal member attributes. */
  protected FileSystemBucket bucket;
  protected Thread thread;
  private ScheduledThreadPoolExecutor timer;

  // TODO:Add max to bound flushIntervalSeconds so we don't fill RAM forever with statsData.
  private int flushIntervalSeconds = 15; // Amount of time to wait to write to file system.

  private Instant lastFlush; // Timestamp of last fush.
  HashMap<Instant, Long> statsData = new HashMap<Instant, Long>();

  private String processor;

  //  TODO:Make these configurable
  private boolean headerWritten = false;

  private boolean singleFileMode = true; // Not sure if this will be useful at all...

  private boolean active = false;

  private float statsRate; // Times per second

  //  Relative to when recorder is first activated.
  //  If deactivated, this will be reset
  // Useful for relative time in CSVs
  private Instant firstInstant;

  boolean isActive() {
    return active;
  }

  void setActive(boolean active) {
    eventProducer.sendInfo(
        this.getClass().getSimpleName()
            + (active ? " Activated" : " Deacitvated. Processor:" + processor));

    if (active) {
      collectStats();
    } else {
      if (statsTask != null) {
        firstInstant = null;
        boolean success = statsTask.cancel(false);
        if (success) {
          eventProducer.sendInfo("Stats Task Successfully deactivated");
        } else {
          eventProducer.sendWarning("Failed to deactivate stats task. ");
        }
      } else {
        eventProducer.sendWarning(
            "Failed to deactivate stats task. stats Task does not exist; was there a task acitvated? ");
      }
    }

    this.active = active;
  }

  private static final String CSV_NAME_POST_FIX = "TM_Stats.csv";

  private Path filePath;
  private Path bucketPath;
  private EventProducer eventProducer;
  private ScheduledFuture<?> statsTask;

  public Spec getSpec() {
    Spec spec = new Spec();

    /* Define our configuration parameters. */
    //    spec.addOption("name", OptionType.STRING).withRequired(true);
    spec.addOption("processor", OptionType.STRING).withRequired(true);
    spec.addOption("bucket", OptionType.STRING).withRequired(true);
    spec.addOption("rate", OptionType.INTEGER).withRequired(true);

    return spec;
  }

  @Override
  public void init(String yamcsInstance, String serviceName, YConfiguration config) {
    try {
      super.init(yamcsInstance, serviceName, config);
    } catch (InitException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    eventProducer =
        EventProducerFactory.getEventProducer(
            yamcsInstance, this.getClass().getSimpleName(), 10000);

    eventProducer.sendInfo(
        this.getClass().getSimpleName() + " loaded with config:" + config.toString());
    String bucketName;

    /* Calidate the configuration that the user passed us. */
    try {
      config = getSpec().validate(config);
    } catch (ValidationException e) {
      log.error("Failed configuration validation.", e);
    }

    /* Instantiate our member objects. */
    this.processor = config.getString("processor", "realtime");
    this.statsRate = config.getInt("rate", 50);
    /* Read in our configuration parameters. */
    bucketName = config.getString("bucket");

    /* Iterate through the bucket names passed to us by the configuration file.  We're going to add the buckets
     * to our internal list so we can process them later. */
    YarchDatabaseInstance yarch = YarchDatabase.getInstance(YamcsServer.GLOBAL_INSTANCE);

    try {
      bucket = (FileSystemBucket) yarch.getBucket(bucketName);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  @Override
  protected void doStart() {
    thread = new Thread(this);
    thread.start();
    notifyStarted();
  }

  @Override
  protected void doStop() {
    if (thread != null) {
      thread.interrupt();
    }

    notifyStopped();
  }

  @Override
  public void run() {
    if (active) {
      collectStats();
    }

    /* Enter our main loop */
    while (isRunningAndEnabled()) {}
  }

  private void collectStats() {
    lastFlush = Instant.now();

    filePath = getNewFilePath();

    this.subscribeTMStats(
        stat -> {
          long totalBitsPerSecond = 0;
          for (TmStatistics s : stat.snapshot()) {
            totalBitsPerSecond += s.getDataRate();
          }
          record(totalBitsPerSecond);
        },
        this.processor);
  }

  private void record(long totalBitsPerSecond) {
    Instant now = Instant.now();
    Duration timeDelta = Duration.between(lastFlush, now);
    Instant processorTime =
        Helpers.toInstant(
            TimeEncoding.toProtobufTimestamp(
                YamcsServer.getServer()
                    .getInstance(this.getYamcsInstance())
                    .getProcessor(processor)
                    .getCurrentTime()));
    if (firstInstant == null) {
      firstInstant = processorTime;
    }
    statsData.put(processorTime, totalBitsPerSecond);
    if (timeDelta.toSeconds() > flushIntervalSeconds) {
      flushData();
      statsData.clear();
      lastFlush = now;
    }
  }

  private void flushData() {
    if (!singleFileMode) {
      filePath = getNewFilePath();
    }
    writeToCSV(filePath.toAbsolutePath().toString(), this.statsData);
  }

  private Path getNewFilePath() {
    bucketPath = Paths.get(bucket.getBucketRoot().toString()).toAbsolutePath();
    Path filePath = bucketPath.resolve(Instant.now().toString() + "_" + CSV_NAME_POST_FIX);

    return filePath;
  }

  public void subscribeTMStats(Consumer<ProcessingStatistics> consumer, String processor) {
    //    TODO:Don't use the YAMCS thread pool. Use the Java one.
    timer = YamcsServer.getServer().getThreadPoolExecutor();
    //    TODO
    //    float calculatedRate = ((1 / this.statsRate)) * (1000);

    //    Rate is 1HZ for now.
    eventProducer.sendInfo("Subscribe to stats at " + 1 + " HZ");

    statsTask =
        timer.scheduleAtFixedRate(
            () -> {
              ProcessingStatistics ps =
                  YamcsServer.getServer()
                      .getInstance(this.getYamcsInstance())
                      .getProcessor(processor)
                      .getTmProcessor()
                      .getStatistics();
              consumer.accept(ps);
            },
            0,
            1,
            TimeUnit.SECONDS);
  }

  private void writeToCSV(String path, HashMap<Instant, Long> statsData) {
    CsvWriter csvWriter = null;
    ArrayList<String> columnHeaders = new ArrayList<String>();
    columnHeaders.add("Reception_Time");
    columnHeaders.add("Delta_Time_Milli");
    columnHeaders.add("Bits_Per_second");
    try {
      csvWriter = new CsvWriter(new FileWriter(path, true), ',');
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    try {
      if (!headerWritten) {
        csvWriter.writeRecord(columnHeaders.toArray(new String[0]));
        headerWritten = true;
      }
    } catch (IOException e) {
      e.printStackTrace();
    }

    ArrayList<String> record = new ArrayList<String>();

    ArrayList<Instant> sortedTimeStamps = new ArrayList<Instant>(statsData.keySet());
    Collections.sort(sortedTimeStamps);
    for (Instant t : sortedTimeStamps) {
      Duration timeDelta = Duration.between(firstInstant, t);
      record.add(t.toString());
      record.add(Long.toString(timeDelta.toMillis()));
      record.add(Long.toString(statsData.get(t)));
      try {
        csvWriter.writeRecord(record.toArray(new String[0]));
      } catch (IOException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
      record.clear();
    }

    csvWriter.close();
    columnHeaders = null;
  }

  public boolean isRunningAndEnabled() {
    State state = state();
    return (state == State.RUNNING || state == State.STARTING);
  }
}
