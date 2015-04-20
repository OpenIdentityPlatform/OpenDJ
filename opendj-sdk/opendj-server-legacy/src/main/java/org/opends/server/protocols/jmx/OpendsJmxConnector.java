/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions Copyright 2015 ForgeRock AS
 */
package org.opends.server.protocols.jmx;

import java.io.IOException;
import java.util.Map;

import javax.management.ListenerNotFoundException;
import javax.management.MBeanServerConnection;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import javax.security.auth.Subject;

/**
 * Wrapper class for the JMX's RMI connector. This class has the exact same
 * functionalities but maintain inner variables which are used during the
 * connection phase.
 * <p>
 * Note that the javadoc has been copied from the
 * javax.management.remote.JMXConnector interface.
 */
public class OpendsJmxConnector implements JMXConnector
{

  /** The wrapped JMX RMI connector. */
  private JMXConnector jmxc;

  /** The connection environment set at creation. */
  private Map<String,Object> environment;

  /** The JMX Service URL. */
  private JMXServiceURL serviceURL;

  /**
   * Creates a connector client for the connector server at the
   * given host and port.  The resultant client is not connected until its
   *  connect method is called.
   *
   * @param serverHostname the target server hostname
   *
   * @param serverPort the target server port
   *
   * @param environment a set of attributes to determine how the
   * connection is made.  This parameter can be null.  Keys in this
   * map must be Strings.  The appropriate type of each associated
   * value depends on the attribute.  The contents of
   * <code>environment</code> are not changed by this call.
   *
   * @exception IOException if the connector client cannot be made
   * because of a communication problem.
   */
  public OpendsJmxConnector(String serverHostname, int serverPort,
      Map<String,Object> environment) throws IOException
  {
    serviceURL = new JMXServiceURL(
        "service:jmx:rmi:///jndi/rmi://" + serverHostname + ":" + serverPort
            + "/org.opends.server.protocols.jmx.client-unknown");

    this.jmxc = JMXConnectorFactory.newJMXConnector(serviceURL, environment);
    this.environment = environment ;
  }

  /**
   * Returns the connection environment.
   *
   * @return Map the connection environment used by new connections
   */
  public Map<String, Object> getConnectionEnv()
  {
    return environment;
  }

  /** {@inheritDoc} */
  @Override
  public void connect() throws IOException, SecurityException
  {
    connect(null);
  }

  /** {@inheritDoc} */
  @Override
  public void connect(Map<String,?> env) throws IOException, SecurityException
  {
    jmxc.connect(env);
  }

  /** {@inheritDoc} */
  @Override
  public MBeanServerConnection getMBeanServerConnection() throws IOException
  {
    return jmxc.getMBeanServerConnection();
  }

  /** {@inheritDoc} */
  @Override
  public MBeanServerConnection getMBeanServerConnection(
      Subject delegationSubject) throws IOException
  {
    return jmxc.getMBeanServerConnection(delegationSubject);
  }

  /** {@inheritDoc} */
  @Override
  public void close() throws IOException
  {
    jmxc.close();
  }

  /** {@inheritDoc} */
  @Override
  public void addConnectionNotificationListener(
      NotificationListener listener, NotificationFilter filter,
      Object handback) throws NullPointerException
  {
    jmxc.addConnectionNotificationListener(listener, filter, handback);
  }

  /** {@inheritDoc} */
  @Override
  public void removeConnectionNotificationListener(
      NotificationListener listener) throws ListenerNotFoundException,
      NullPointerException
  {
    jmxc.removeConnectionNotificationListener(listener);
  }

  /** {@inheritDoc} */
  @Override
  public void removeConnectionNotificationListener(
      NotificationListener l, NotificationFilter f, Object handback)
      throws ListenerNotFoundException
  {
    jmxc.removeConnectionNotificationListener(l, f, handback);
  }

  /** {@inheritDoc} */
  @Override
  public String getConnectionId() throws IOException
  {
    return jmxc.getConnectionId();
  }
}
