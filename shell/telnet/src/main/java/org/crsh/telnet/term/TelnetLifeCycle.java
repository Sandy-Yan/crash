/*
 * Copyright (C) 2003-2009 eXo Platform SAS.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.crsh.telnet.term;

import net.wimpi.telnetd.TelnetD;
import net.wimpi.telnetd.io.terminal.TerminalManager;
import net.wimpi.telnetd.net.Connection;
import net.wimpi.telnetd.net.ConnectionManager;
import net.wimpi.telnetd.net.PortListener;
import net.wimpi.telnetd.shell.ShellManager;
import net.wimpi.telnetd.util.StringUtil;
import org.crsh.plugin.PluginContext;
import org.crsh.plugin.ResourceKind;
import org.crsh.term.TermLifeCycle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author <a href="mailto:julien.viet@exoplatform.com">Julien Viet</a>
 * @version $Revision$
 */
public class TelnetLifeCycle extends TermLifeCycle {

  /** . */
  private final Logger log = LoggerFactory.getLogger(TelnetLifeCycle.class);

  /** . */
  private Integer port;

  /** . */
  private List<PortListener> listeners;

  /** . */
  private static final ConcurrentHashMap<ConnectionManager, TelnetLifeCycle> map = new ConcurrentHashMap<ConnectionManager, TelnetLifeCycle>();

  static TelnetLifeCycle getLifeCycle(Connection conn) {
    return map.get(conn.getConnectionData().getManager());
  }

  public TelnetLifeCycle(PluginContext context) {
    super(context);
  }

  public Integer getPort() {
    return port;
  }

  public void setPort(Integer port) {
    this.port = port;
  }

  @Override
  protected synchronized void doInit() throws Exception {
    String s = getContext().loadResource("telnet.properties", ResourceKind.CONFIG).getContent();
    Properties props = new Properties();
    props.load(new ByteArrayInputStream(s.getBytes("ISO-8859-1")));

    //
    if (port != null) {
      log.debug("Explicit telnet port configuration with value " + port);
      props.put("std.port", port.toString());
    } else {
      log.debug("Use default telnet port configuration " + props.getProperty("std.port"));
    }

    //
    ShellManager.createShellManager(props);

    //
    TerminalManager.createTerminalManager(props);

    //
    ArrayList<PortListener> listeners = new ArrayList<PortListener>();
    String[] listnames = StringUtil.split(props.getProperty("listeners"), ",");
    for (String listname : listnames) {
      PortListener listener = PortListener.createPortListener(listname, props);
      listeners.add(listener);
    }

    //
    this.listeners = listeners;

    // Start listeners
    for (PortListener listener : this.listeners) {
      listener.start();
      map.put(listener.getConnectionManager(), this);
    }
  }

  @Override
  protected synchronized void doDestroy() {
    if (listeners != null) {
      List<PortListener> listeners = this.listeners;
      this.listeners = null;
      for (PortListener listener : listeners) {
        try {
          listener.stop();
        } catch (Exception ignore) {
        } finally {
          map.remove(listener.getConnectionManager());
        }
      }
    }
  }
}
