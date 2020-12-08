gcjfrlog is Java agent to gather GC information via JFR Event Sreaming and to post them as JSON document via HTTP.

# Requirements

JDK 14 or later

# How to build

```
$ mvn package
```

# How to use

## Examples

Post events to Elasticsearch

```
$ java -javaagent:/path/to/gcjfrlog.jar=uri=http://localhost:9200/gcjfrlog-%y-%m/%h,label=app ...
```

## Start agent

### Start log shipping since process start

Set path to `gcjfrlog-<version>.jar` to `-javaagent`

```
$ java -javaagent:/path/to/gcjfrlog.jar=<option> ...
```

### Attach gcjfrlog to existing process

You need to escape double-quote.

```
$ jcmd <PID> JVMTI.agent_load \"/path/tp/gcjfrlog.jar=<option>\"
```

## Option

* `uri` **mandatory option**
    * URI to push
* `label`
    * label field would be set if this value is set
* `connect_timeout`
    * Connection timeout in millis
* `request_timeout`
    * HTTP request timeout in millis

## Format strings

You can use format strings in `uri`.

* `%h`
    * hostname
* `%l`
    * label
* `%y`
    * year (yyyy)
* `%m`
    * month (mm)
* `%d`
    * day (dd)

# JFR events which are handled

* `jdk.GCPhasePause`
* `jdk.PromotionFailed`
* `jdk.EvacuationFailed`
* `jdk.ConcurrentModeFailure`
* `jdk.MetaspaceOOM`
* `jdk.GCHeapSummary`
* `jdk.MetaspaceSummary`
* `jdk.GarbageCollection`

# License

The GNU Lesser General Public License, version 3.0
