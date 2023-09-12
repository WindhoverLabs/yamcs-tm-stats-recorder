[![CI](https://github.com/WindhoverLabs/yamcs-tm-stats-recorder/actions/workflows/ci.yml/badge.svg)](https://github.com/WindhoverLabs/yamcs-tm-stats-recorder/actions/workflows/ci.yml)
[![Coverage Status](https://coveralls.io/repos/github/WindhoverLabs/yamcs-tm-stats-recorder/badge.svg?branch=main)](https://coveralls.io/github/WindhoverLabs/yamcs-tm-stats-recorder?branch=main)
# yamcs-tm-stats-recorder
A YAMCS plugin that records total throughput of downlink. Can be used for any processor the user specifies in the configuration; realtime or replay. 
Can be activated at runtime via http calls.


# Table of Contents
1. [Dependencies](#dependencies)
2. [To Build](#to_build)  
4. [Add It to your YAMCS Install](#add_it_to_yamcs)   
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
  - class: com.windhoverlabs.yamcs.stats.TmStatsRecorder
    name: "TmStatsRecorder"
    args:
      bucket: "cfdpDownCH1"
      processor: "rf_replay"
      rate: 1 # hertz. At the moment this is limited to 1hertz by YAMCS API. Look at org.yamcs.mdb.ProcessingStatistics.snapshot().
```

## Example Commands (Python)

```python
import requests
r = requests.post('http://127.0.0.1:8090/api/fsw/tm_stats_recorder/:activate',
                  json={"instance": "fsw",
                        "serviceName": "TmStatsRecorder"})
```

