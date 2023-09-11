[![CI](https://github.com/WindhoverLabs/yamcs-cfs-ds/actions/workflows/ci.yml/badge.svg)](https://github.com/WindhoverLabs/yamcs-cfs-ds/actions/workflows/ci.yml)
[![Coverage Status](https://coveralls.io/repos/github/WindhoverLabs/yamcs-cfs-ds/badge.svg?branch=main)](https://coveralls.io/github/WindhoverLabs/yamcs-cfs-ds?branch=main)
# yamcs-cfs-ds
A YAMCS plugin for the Core Flight System (CFS) Data Storage (DS) application.  This plugin will automatically detect and parse DS data logs and inject the recorded
messages in the YAMCS database.  This only parses telemetry.  Command bitpattern parsing is not supported.

# Table of Contents
1. [Dependencies](#dependencies)
2. [To Build](#to_build)  
3. [To Run](#to_run)
4. [Add It to your YAMCS Install](#add_it_to_yamcs)   
5. [XTCE Patterns and Conventions](#XTCE-Patterns-and-Conventions)
5. [Build Documentation](#build_documentation)


### Dependencies <a name="dependencies"></a>
- `Java 11`
- `Maven`
- `YAMCS>=5.6.2`
- `Ubuntu 16/18/20`

### To Build <a name="to_build"></a>
```
mvn install -DskipTests
```

To package it:
```
mvn package -DskipTests
mvn dependency:copy-dependencies
```


### Configuration
```
services:
  - class: org.yamcs.archive.XtceTmRecorder
  - class: org.yamcs.archive.ParameterRecorder
  - class: org.yamcs.archive.AlarmRecorder
  - class: org.yamcs.archive.EventRecorder
  - class: org.yamcs.archive.ReplayServer
  - class: com.windhoverlabs.yamcs.stats.TmStatsRecorder
    name: "TmStatsRecorder"
    args:
      bucket: "cfdpDownCH1"
      processor: "rf_replay"
      rate: 1 # hertz. At the moment this is limited to 1hertz by YAMCS API. Look at org.yamcs.mdb.ProcessingStatistics.snapshot().
```

## Example Commands

```
import requests
r = requests.post('http://127.0.0.1:8090/api/fsw/tm_stats_recorder/:activate',
                  json={"instance": "fsw",
                        "serviceName": "TmStatsRecorder"})
```

