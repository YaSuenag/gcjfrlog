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

import java.lang.instrument.*;
import java.net.*;


public class Bootstrap{

  public static void premain(String agentArgs, Instrumentation inst){
    Thread shipper;

    try{
      var opts = new Options(agentArgs);
      shipper = new Thread(new LogShipper(opts), "Log shipper thread - " + opts.getURI().getHost());
    }
    catch(UnknownHostException | URISyntaxException e){
      throw new RuntimeException(e);
    }

    shipper.setDaemon(true);
    shipper.start();
  }

  public static void agentmain(String agentArgs, Instrumentation inst){
    premain(agentArgs, inst);
  }

}
