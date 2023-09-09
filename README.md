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

The `package` command will output a jar file at `yamcs-cfs-ds/target`.
Note the `dependency:copy-dependencies` command; this will copy all of the jars to the `yamcs-cfs-ds/target/dependency` directory. Very useful for integrating third-party dependencies.

### To Integrate with YAMCS
This plugin functions as a YAMCS Telemetry Provider and will appear as a Datalink.  To integrate this plugin, add the
"com.windhoverlabs.yamcs.cfs.ds.CfsDsPlugin" plugin to the "dataLinks" section of the YAMCS instance configuration. 
For example:
```
dataLinks:   
  - name: ds-logs
    class: com.windhoverlabs.yamcs.cfs.ds.CfsDsPlugin
    stream: tm_realtime
    buckets: ["cfdpDown"]
    DS_FILE_HDR_SUBTYPE: 12345
    DS_TOTAL_FNAME_BUFSIZE: 64
    packetPreprocessorClassName: org.yamcs.tctm.cfs.CfsPacketPreprocessor
    packetPreprocessorArgs:
      byteOrder: LITTLE_ENDIAN
      timestampFormat: CFE_SB_TIME_32_16_SUBS
      timeEncoding:
        useLocalGenerationTime: false
        epoch: CUSTOM
        epochUTC: "1970-01-01T00:00:00Z"
        timeIncludesLeapSeconds: false
```

### Configuration
This plugin functions as a YAMCS Telemetry Provider and will appear as a Datalink.  To integrate this plugin, add the
"com.windhoverlabs.yamcs.cfs.ds.CfsDsPlugin" plugin to the "dataLinks" section of the YAMCS instance configuration. 
For example:

name
:  REQUIRED.  The name as it will appear in the Datalinks list

class
:  REQUIRED.  This must be "com.windhoverlabs.yamcs.cfs.ds.CfsDsPlugin"

stream
:  REQUIRED.  This is the stream to push the parsed packets to.  Typically this is 'tm_realtime', but depends on your particular YAMCS configuration.

buckets
:  REQUIRED.  This is an array of bucket names.  The buckets must be defined in the YAMCS configuration file.  These must be File System Buckets.  The plugin will monitor these buckets for new or modified files.

DS_FILE_HDR_SUBTYPE
:  REQUIRED.  This integer is the value that the DS application sets in the CFE FS Header to identify the file as a DS log.  This should match your CFS flight software configuration and will appear in source code as "DS_FILE_HDR_SUBTYPE" (CFE 6.5.0a).

DS_TOTAL_FNAME_BUFSIZE
:  REQUIRED.  This integer represents the length of the filename field in the DS file header.  This should match your CFS flight software configuration and will appear in source code as "DS_TOTAL_FNAME_BUFSIZE" (CFE 6.5.0a).

ignoreInitial
:  OPTIONAL.  DEFAULT=true.  When set to true, the plugin will ignore files that already exist in the buckets at startup.  When set to false, the plugin will process these files as newly received DS log files.  If the file has already been processed, the entries will appear as duplicates and will not result in a change.  

clearBucketsAtStartup
:  OPTIONAL.  DEFAULT=false.  When set to true, the plugin will delete all files and directories contained in the buckets at startup.

deleteFileAfterProcessing
:  OPTIONAL.  DEFAULT=false.  When set to true, the plugin will delete each file after successfully processing it.

pollingPeriod:
:  OPTIONAL.  DEFAULT=5.  This integer represents the number of seconds to wait between checking the buckets for changes.  

packetPreprocessorClassName
:  REQUIRED.  This is the class name to use as the PacketPreprocessor 

packetPreprocessorArgs
:  REQUIRED.  These are the arguments for the PacketPreprocessor

