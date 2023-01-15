/*
 * Copyright (C) 2020, 2023, Yasumasa Suenaga
 *
 * This file is part of gcjfrlog.
 *
 * gcjfrlog is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * gcjfrlog is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with UL Viewer.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.yasuenag.gcjfrlog;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.*;

import jdk.jfr.consumer.*;

import com.google.gson.stream.*;


public class LogShipper implements Runnable{

  private final HttpClient client;

  private final HttpRequest.Builder requestBuilderBase;

  private final Options options;

  public LogShipper(Options options){
    this.options = options;
    client = HttpClient.newBuilder()
                       .connectTimeout(options.getConnectTimeout())
                       .executor(Executors.newWorkStealingPool())
                       .followRedirects(HttpClient.Redirect.NORMAL)
                       .build();
    requestBuilderBase = HttpRequest.newBuilder()
                                    .header("Content-Type", "application/json")
                                    .timeout(options.getRequestTimeout());
  }

  private void publish(String json) throws URISyntaxException{
    var request = requestBuilderBase.copy()
                                    .uri(options.getURI())
                                    .POST(HttpRequest.BodyPublishers.ofString(json))
                                    .build();
    client.sendAsync(request, HttpResponse.BodyHandlers.discarding())
          .thenAccept(r -> {
             if(r.statusCode() >= 400){
               System.err.println("gcjfrlog: response " + Integer.toString(r.statusCode()) + ": " + r.uri());
               System.err.println(r.body());
             }
           })
          .exceptionally(t -> {
             System.err.println("gcjfrlog: " + t.getClass().getName() + ": " + t.getMessage() + ": " + request.uri());
             return null;
           });
  }

  private void writeStackTrace(JsonWriter json, RecordedEvent event) throws IOException{
    if(event.getStackTrace() != null){
      json.name("stackTrace");
      json.beginArray();
        for(var f : event.getStackTrace().getFrames()){
          json.value(f.toString());
        }
      json.endArray();
    }
  }

  private void writeCopyFailed(String name, RecordedEvent event, JsonWriter json) throws IOException{
    json.name(name);
    json.beginObject();
      json.name("objectCount").value(event.getLong(name + ".objectCount"));
      json.name("firstSize").value(event.getLong(name + ".firstSize"));
      json.name("smallestSize").value(event.getLong(name + ".smallestSize"));
      json.name("totalSize").value(event.getLong(name + ".totalSize"));
    json.endObject();
  }

  private void writeMetaspaceSizes(String name, RecordedEvent event, JsonWriter json) throws IOException{
    json.name(name);
    json.beginObject();
      json.name("committed").value(event.getLong(name + ".committed"));
      json.name("used").value(event.getLong(name + ".used"));
      json.name("reserved").value(event.getLong(name + ".reserved"));
    json.endObject();
  }

  private void writeDurationInMillis(RecordedEvent event, JsonWriter json) throws IOException{
    var duration = event.getDuration();
    double millis = (duration.getSeconds() * 1000.0d) + (duration.getNano() / 1000_000.0d);
    json.name("duration").value(millis);
  }

  private void handleGCPhasePause(JsonWriter json, RecordedEvent event) throws IOException{
    writeDurationInMillis(event, json);
    json.name("gcId").value(event.getLong("gcId"));
    json.name("name").value(event.getString("name"));
  }

  private void handlePromotionFailed(JsonWriter json, RecordedEvent event) throws IOException{
    json.name("gcId").value(event.getLong("gcId"));
    writeCopyFailed("promotionFailed", event, json);
    RecordedThread thread = event.getThread("thread");
    if(thread != null){
      json.name("thread");
      json.beginObject();
        json.name("osName").value(thread.getOSName());
        json.name("osThreadId").value(thread.getOSThreadId());
        json.name("javaName").value(thread.getJavaName());
        json.name("javaThreadId").value(thread.getId());
      json.endObject();
    }
  }

  private void handleEvacuationFailed(JsonWriter json, RecordedEvent event) throws IOException{
    json.name("gcId").value(event.getLong("gcId"));
    writeCopyFailed("evacuationFailed", event, json);
  }

  private void handleConcurrentModeFailure(JsonWriter json, RecordedEvent event) throws IOException{
    json.name("gcId").value(event.getLong("gcId"));
  }

  private void handleMetaspaceOOM(JsonWriter json, RecordedEvent event) throws IOException{
    RecordedClassLoader classLoader = event.getValue("classLoader");
    if((classLoader != null) && (classLoader.getType() != null)){
      json.name("classLoader");
      json.beginObject();
        json.name("type").value(classLoader.getType().getName());
        json.name("name").value(classLoader.getName());
      json.endObject();
    }
    json.name("hiddenClassLoader").value(event.getBoolean("hiddenClassLoader"));
    json.name("size").value(event.getLong("size"));
    json.name("metadataType").value(event.getString("metadataType"));
    json.name("metaspaceObjectType").value(event.getString("metaspaceObjectType"));
    writeStackTrace(json, event);
  }

  private void handleGCHeapSummary(JsonWriter json, RecordedEvent event) throws IOException{
    json.name("gcId").value(event.getLong("gcId"));
    json.name("when").value(event.getString("when"));
    json.name("heapSpace");
    json.beginObject();
      json.name("committedSize").value(event.getLong("heapSpace.committedSize"));
      json.name("reservedSize").value(event.getLong("heapSpace.reservedSize"));
    json.endObject();
    json.name("heapUsed").value(event.getLong("heapUsed"));
  }

  private void handleMetaspaceSummary(JsonWriter json, RecordedEvent event) throws IOException{
    json.name("gcId").value(event.getLong("gcId"));
    json.name("when").value(event.getString("when"));
    json.name("gcThreshold").value(event.getLong("gcThreshold"));
    writeMetaspaceSizes("metaspace", event, json);
    writeMetaspaceSizes("dataSpace", event, json);
    writeMetaspaceSizes("classSpace", event, json);
  }

  private void handleGarbageCollection(JsonWriter json, RecordedEvent event) throws IOException{
    writeDurationInMillis(event, json);
    json.name("gcId").value(event.getLong("gcId"));
    json.name("name").value(event.getString("name"));
    json.name("cause").value(event.getString("cause"));
    json.name("sumOfPauses").value(event.getLong("sumOfPauses"));
    json.name("longestPause").value(event.getLong("longestPause"));
  }

  private void handleEvents(RecordedEvent event){
    String eventName = event.getEventType().getName();
    var strWriter = new StringWriter();

    try(var json = new JsonWriter(strWriter)){
      json.beginObject();
        json.name("startTime").value(event.getStartTime().toString());
        json.name("host").value(options.getHostName());
        json.name("eventName").value(eventName);
        if(options.getLabel().isPresent()){
          json.name("label").value(options.getLabel().get());
        }

        switch(eventName){
          case "jdk.GCPhasePause":
            handleGCPhasePause(json, event);
            break;

          case "jdk.PromotionFailed":
            handlePromotionFailed(json, event);
            break;

          case "jdk.EvacuationFailed":
            handleEvacuationFailed(json, event);
            break;

          case "jdk.ConcurrentModeFailure":
            handleConcurrentModeFailure(json, event);
            break;

          case "jdk.MetaspaceOOM":
            handleMetaspaceOOM(json, event);
            break;

          case "jdk.GCHeapSummary":
            handleGCHeapSummary(json, event);
            break;

          case "jdk.MetaspaceSummary":
            handleMetaspaceSummary(json, event);
            break;

          case "jdk.GarbageCollection":
            handleGarbageCollection(json, event);
            break;

          default:
            throw new IllegalStateException("Unexpected: " + eventName);
        }

      json.endObject();
    }
    catch(IOException e){
      throw new UncheckedIOException(e);
    }

    try{
      publish(strWriter.toString());
    }
    catch(URISyntaxException e){
      throw new IllegalStateException(e);
    }

  }

  private static final List<String> HANDLED_EVENTS = List.of(
    "jdk.GCPhasePause",
    "jdk.PromotionFailed",
    "jdk.EvacuationFailed",
    "jdk.ConcurrentModeFailure",
    "jdk.MetaspaceOOM",
    "jdk.GCHeapSummary",
    "jdk.MetaspaceSummary",
    "jdk.GarbageCollection"
  );

  @Override
  public void run(){
    try(var rs = new RecordingStream()){
      HANDLED_EVENTS.forEach(e -> {
        rs.enable(e);
        rs.onEvent(e, this::handleEvents);
      });

      rs.start();
    }
    catch(Exception e){
      System.err.println("gcjfrlog: " + e.getClass().toString() + ": " + e.getMessage());
    }

    System.out.println("gcjfrlog: terminated: " + Thread.currentThread().getName());
  }

}
