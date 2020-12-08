/*
 * Copyright (C) 2020 Yasumasa Suenaga
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

import java.net.*;
import java.time.*;
import java.time.temporal.*;
import java.util.*;


public class Options{

  private String uriBase;

  private Optional<String> label;

  private Duration connectTimeout;

  private Duration requestTimeout;

  private URI cachedURI;

  private LocalDateTime cachedDateTime;

  private final String hostName;

  public Options(String args) throws UnknownHostException{
    if(args == null){
      throw new IllegalArgumentException("uri is not specified");
    }

    uriBase = null;
    cachedURI = null;
    cachedDateTime = null;
    label = Optional.empty();
    connectTimeout = Duration.of(1000L, ChronoUnit.MILLIS);
    requestTimeout = Duration.of(1000L, ChronoUnit.MILLIS);
    hostName = InetAddress.getLocalHost().getHostName();

    for(var arg : args.split(",")){
      var kv = arg.split("=");
      switch(kv[0]){
        case "uri":
          uriBase = kv[1];
          break;

        case "label":
          label = Optional.of(kv[1]);
          break;

        case "connect_timeout":
          connectTimeout = Duration.of(Long.parseLong(kv[1]), ChronoUnit.MILLIS);
          break;

        case "request_timeout":
          requestTimeout = Duration.of(Long.parseLong(kv[1]), ChronoUnit.MILLIS);
          break;

        default:
          throw new IllegalArgumentException("Unknown option: " + kv[0]);
      }
    }

    if(uriBase == null){
      throw new IllegalArgumentException("uri is not specified");
    }

    // "%h": hostname
    // "%l": label (if exists)
    uriBase = uriBase.replaceAll("%h", hostName)
                     .replaceAll("%l", label.orElse(""));
  }

  public synchronized URI getURI() throws URISyntaxException{
    var now = LocalDateTime.now().truncatedTo(ChronoUnit.DAYS);

    if((cachedDateTime == null) || !cachedDateTime.equals(now)){
      // "%y": year
      // "%m": month
      // "%d": day
      var uri = uriBase.replaceAll("%y", Integer.toString(now.getYear()))
                       .replaceAll("%m", String.format("%02d", now.getMonthValue()))
                       .replaceAll("%d", String.format("%02d", now.getDayOfMonth()));
      cachedDateTime = now;
      cachedURI = new URI(uri);
    }

    return cachedURI;
  }

  public Optional<String> getLabel(){
    return label;
  }

  public Duration getConnectTimeout(){
    return connectTimeout;
  }

  public Duration getRequestTimeout(){
    return requestTimeout;
  }

  public String getHostName(){
    return hostName;
  }

}
