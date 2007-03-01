/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE
 * or https://OpenDS.dev.java.net/OpenDS.LICENSE.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
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
 * functionnalities but maintain inner variables which are used during the
 * connection phase.
 * <p>
 * Note that the javadoc has been copied from the
 * javax.management.remote.JMXConnector interface.
 */
public class OpendsJmxConnector implements JMXConnector

{

  /**
   * the wrapped JMX RMI connector.
   */
  private JMXConnector jmxc = null;

  /**
   * the connection environment set at creation.
   */
  private Map<String,Object> environment = null;

  /**
   * the JMX Service URL.
   */
  private JMXServiceURL serviceURL = null;

  /**
   * the host to connect to.
   */
  private String serverHostname = null;



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
   *
   */
  public OpendsJmxConnector(String serverHostname, int serverPort,
      Map<String,Object> environment) throws IOException
  {
    serviceURL = new JMXServiceURL(
        "service:jmx:rmi:///jndi/rmi://" + serverHostname + ":" + serverPort
            + "/org.opends.server.protocols.jmx.client-unknown");

    this.jmxc = JMXConnectorFactory.newJMXConnector(serviceURL, environment);
    this.serverHostname = serverHostname;
    this.environment = environment ;
  }
//  /**
//   * Sets this connector's connection environment.
//   *
//   * @param environment the new connection env
//   */
//  public void setConnectionEnv(Map connectionEnv)
//  {
//    this.environment = environment;
//  }

  /**
   * Returns the connection environment.
   *
   * @return Map the connection environment used by new connections
   */
  public Map getConnectionEnv()
  {
    return environment;
  }

  /**
   * Establishes the connection to the connector server. This method is
   * equivalent to connect(null).
   *
   * @throws IOException
   *         if the connection could not be made because of a communication
   *         problem.
   * @throws SecurityException
   *         if the connection could not be made for security reasons.
   */
  public void connect() throws IOException, SecurityException
  {
    this.connect(null);
  }

 /**
   * Establishes the connection to the connector server. If connect has
   * already been called successfully on this object, calling it again has
   * no effect. If, however, close() was called after connect, the new
   * connect will throw an IOException. Otherwise, either connect has never
   * been called on this object, or it has been called but produced an
   * exception. Then calling connect will attempt to establish a connection
   * to the connector server.
   *
   * @param env
   *        the properties of the connection. Properties in this map
   *        override properties in the map specified when the JMXConnector
   *        was created, if any. This parameter can be null, which is
   *        equivalent to an empty map.
   * @throws IOException
   *         if the connection could not be made because of a communication
   *         problem.
   * @throws SecurityException -
   *         if the connection could not be made for security reasons.
   */
  public void connect(Map<String,?> env) throws IOException, SecurityException
  {
    // set the real target hostname
    DirectoryRMIClientSocketFactory.setServerHostname(serverHostname);

    // configure the thread-local connection environment
    if (env != null)
    {
      // encode credentials if necessary
      updateCredentials(env);
    }
    DirectoryRMIClientSocketFactory.setConnectionEnv(environment);


    jmxc.connect(env);
  }

  /**
   * Returns an MBeanServerConnection object representing a remote MBean
   * server. For a given JMXConnector, two successful calls to this method
   * will usually return the same MBeanServerConnection object, though this
   * is not required. For each method in the returned
   * MBeanServerConnection, calling the method causes the corresponding
   * method to be called in the remote MBean server. The value returned by
   * the MBean server method is the value returned to the client. If the
   * MBean server method produces an Exception, the same Exception is seen
   * by the client. If the MBean server method, or the attempt to call it,
   * produces an Error, the Error is wrapped in a JMXServerErrorException,
   * which is seen by the client. Calling this method is equivalent to
   * calling getMBeanServerConnection(null) meaning that no delegation
   * subject is specified and that all the operations called on the
   * MBeanServerConnection must use the authenticated subject, if any.
   *
   * @return an object that implements the MBeanServerConnection interface
   *         by forwarding its methods to the remote MBean server.
   * @throws IOException -
   *         if a valid MBeanServerConnection cannot be created, for
   *         instance because the connection to the remote MBean server has
   *         not yet been established (with the connect method), or it has
   *         been closed, or it has broken.
   */
  public MBeanServerConnection getMBeanServerConnection() throws IOException
  {
    return jmxc.getMBeanServerConnection();
  }

  /**
   * Returns an MBeanServerConnection object representing a remote MBean
   * server on which operations are performed on behalf of the supplied
   * delegation subject. For a given JMXConnector and Subject, two
   * successful calls to this method will usually return the same
   * MBeanServerConnection object, though this is not required. For each
   * method in the returned MBeanServerConnection, calling the method
   * causes the corresponding method to be called in the remote MBean
   * server on behalf of the given delegation subject instead of the
   * authenticated subject. The value returned by the MBean server method
   * is the value returned to the client. If the MBean server method
   * produces an Exception, the same Exception is seen by the client. If
   * the MBean server method, or the attempt to call it, produces an Error,
   * the Error is wrapped in a JMXServerErrorException, which is seen by
   * the client.
   *
   * @param delegationSubject
   *        the Subject on behalf of which requests will be performed. Can
   *        be null, in which case requests will be performed on behalf of
   *        the authenticated Subject, if any.
   * @return an object that implements the MBeanServerConnection interface
   *         by forwarding its methods to the remote MBean server on behalf
   *         of a given delegation subject.
   * @throws IOException
   *         if a valid MBeanServerConnection cannot be created, for
   *         instance because the connection to the remote MBean server has
   *         not yet been established (with the connect method), or it has
   *         been closed, or it has broken.
   */
  public MBeanServerConnection getMBeanServerConnection(
      Subject delegationSubject) throws IOException
  {
    return jmxc.getMBeanServerConnection(delegationSubject);
  }

  /**
   * Closes the client connection to its server. Any ongoing or new request
   * using the MBeanServerConnection returned by getMBeanServerConnection()
   * will get an IOException. If close has already been called successfully
   * on this object, calling it again has no effect. If close has never
   * been called, or if it was called but produced an exception, an attempt
   * will be made to close the connection. This attempt can succeed, in
   * which case close will return normally, or it can generate an
   * exception. Closing a connection is a potentially slow operation. For
   * example, if the server has crashed, the close operation might have to
   * wait for a network protocol timeout. Callers that do not want to block
   * in a close operation should do it in a separate thread.
   *
   * @throws IOException
   *         if the connection cannot be closed cleanly. If this exception
   *         is thrown, it is not known whether the server end of the
   *         connection has been cleanly closed.
   */
  public void close() throws IOException
  {
    jmxc.close();
  }

  /**
   * Adds a listener to be informed of changes in connection status. The
   * listener will receive notifications of type JMXConnectionNotification.
   * An implementation can send other types of notifications too. Any
   * number of listeners can be added with this method. The same listener
   * can be added more than once with the same or different values for the
   * filter and handback. There is no special treatment of a duplicate
   * entry. For example, if a listener is registered twice with no filter,
   * then its handleNotification method will be called twice for each
   * notification.
   *
   * @param listener
   *        a listener to receive connection status notifications.
   * @param filter
   *        a filter to select which notifications are to be delivered to
   *        the listener, or null if all notifications are to be delivered.
   * @param handback
   *        an object to be given to the listener along with each
   *        notification. Can be null.
   * @throws NullPointerException
   *         if listener is null.
   */
  public void addConnectionNotificationListener(
      NotificationListener listener, NotificationFilter filter,
      Object handback) throws NullPointerException
  {
    jmxc.addConnectionNotificationListener(listener, filter, handback);
  }

  /**
   * Removes a listener from the list to be informed of changes in status.
   * The listener must previously have been added. If there is more than
   * one matching listener, all are removed.
   *
   * @param listener -
   *        a listener to receive connection status notifications.
   * @throws NullPointerException
   *         if listener is null.
   * @throws ListenerNotFoundException
   *         if the listener is not registered with this JMXConnector.
   */
  public void removeConnectionNotificationListener(
      NotificationListener listener) throws ListenerNotFoundException,
      NullPointerException
  {
    jmxc.removeConnectionNotificationListener(listener);
  }

  /**
   * Removes a listener from the list to be informed of changes in status.
   * The listener must previously have been added with the same three
   * parameters. If there is more than one matching listener, only one is
   * removed.
   *
   * @param l
   *        a listener to receive connection status notifications.
   * @param f
   *        a filter to select which notifications are to be delivered to
   *        the listener. Can be null. handback - an object to be given to
   *        the listener along with each notification. Can be null.
   * @param handback
   *        an object to be given to the listener along with each
   *        notification. Can be null.
   * @throws ListenerNotFoundException
   *         if the listener is not registered with this JMXConnector, or
   *         is not registered with the given filter and handback.
   */
  public void removeConnectionNotificationListener(
      NotificationListener l, NotificationFilter f, Object handback)
      throws ListenerNotFoundException
  {
    jmxc.removeConnectionNotificationListener(l, f, handback);
  }

  /**
   * Gets this connection's ID from the connector server. For a given
   * connector server, every connection will have a unique id which does
   * not change during the lifetime of the connection.
   *
   * @return the unique ID of this connection. This is the same as the ID
   *         that the connector server includes in its
   *         JMXConnectionNotifications. The package description describes
   *         the conventions for connection IDs.
   * @throws IOException
   *         if the connection ID cannot be obtained, for instance because
   *         the connection is closed or broken.
   */
  public String getConnectionId() throws IOException
  {
    return jmxc.getConnectionId();
  }

  /**
   * Update if necessary the credentials of the given map using
   * information coming from the map given when the connector was created.
   * This method is called from the connect method when it has received
   * a non null map holding potentially new credentials. It calls this
   * method BEFORE actually trying to connect to the server.
   *
   * @param Map given to the connect method
   */
  private void updateCredentials(Map env) throws IOException
  {
    // credential to update ??
    if (!env.containsKey(JMXConnector.CREDENTIALS))
    {
      // NO : nothing to update
      return;
    }
    else
    {
      Object cred  =  env.get(JMXConnector.CREDENTIALS);
      environment.put(JMXConnector.CREDENTIALS, cred);
    }
  }
}
