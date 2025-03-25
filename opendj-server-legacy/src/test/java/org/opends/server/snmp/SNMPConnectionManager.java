/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2008 Sun Microsystems, Inc.
 * Portions copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.snmp;



import com.sun.management.snmp.SnmpEngine;
import com.sun.management.snmp.SnmpOid;
import com.sun.management.snmp.SnmpOidTableSupport;
import com.sun.management.snmp.manager.SnmpPeer;
import com.sun.management.snmp.manager.SnmpSession;
import com.sun.management.snmp.manager.usm.SnmpUsmPeer;
import java.io.File;
import java.net.InetAddress;
import static org.testng.Assert.*;

import java.util.ArrayList;
import java.util.List;

import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.opends.server.api.ConnectionHandler;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyOperationBasis;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.types.Attributes;
import org.opends.server.types.Control;
import org.forgerock.opendj.ldap.DN;
import org.opends.server.types.Modification;
import org.forgerock.opendj.ldap.ModificationType;
import org.testng.annotations.Test;



/**
 * An abstract class that all SNMP unit test should extend.
 */
@Test(enabled = true, groups =
{
    "precommit", "snmp"
}, singleThreaded = true)
public abstract class SNMPConnectionManager extends DirectoryServerTestCase
{

  /**
   * Snmp Port
   */
  private int snmpPort;

  /**
   * Snmp Trap Port
   */
  private int trapSnmpPort;



  /**
   * Set Up the Directory Server
   *
   * @throws java.lang.Exception
   */
  protected void setUp() throws Exception
  {

    // Make sure that the server is up and running.
    TestCaseUtils.restartServer();
    synchronized (this)
    {
      this.wait(500);
    }
    SNMPConnectionHandler snmpHandler = getSNMPConnectionHandler();
    if (snmpHandler == null)
    {
      throw new Exception("Unable to get a SNMP connector");
    }
  }



  /**
   * Set Down the Directory Server.
   *
   * @throws java.lang.Exception
   */
  protected void setDown() throws Exception
  {
    TestCaseUtils.restartServer();
  }



  /**
   * Gets the Snmp port on which the Connection Handler is listening
   *
   * @return
   */
  protected int getSnmpPort()
  {
    return this.snmpPort;
  }



  /**
   * Gets the Snmp trap port on which the Connection Handler is
   * listening
   *
   * @return
   */
  protected int getTrapSnmpPort()
  {
    return this.trapSnmpPort;
  }



  /**
   * Gets an SNMP V3 peer agent (proxy)
   *
   * @param port
   * @return
   */
  protected SnmpUsmPeer getSnmpV3Peer(int port)
  {

    try
    {
      String host = InetAddress.getLocalHost().getCanonicalHostName();
      SnmpOidTableSupport oidTable = new DIRECTORY_SERVER_MIBOidTable();
      SnmpOid.setSnmpOidTable(oidTable);

      SnmpSession session = new SnmpSession("SyncManagerV3 session");
      SnmpEngine engine = session.getEngine();
      return new SnmpUsmPeer(engine, host, port);
    }
    catch (Exception ex)
    {
      return null;
    }
  }



  /**
   * Gets an SNMP V2 agent peer (proxy)
   *
   * @param port
   * @return
   */
  protected SnmpPeer getSnmpV2Peer(int port)
  {

    try
    {
      String host = InetAddress.getLocalHost().getCanonicalHostName();
      SnmpOidTableSupport oidTable = new DIRECTORY_SERVER_MIBOidTable();
      SnmpOid.setSnmpOidTable(oidTable);
      return new SnmpPeer(host, port);
    }
    catch (Exception ex)
    {
      return null;
    }
  }



  /**
   * Get a reference to the SNMP connection handler.
   *
   * @return an SNMP Connection handler
   * @throws an
   *           Exception is something went wrong.
   */
  protected SNMPConnectionHandler getSNMPConnectionHandler() throws Exception
  {
    List<ConnectionHandler<?>> handlers = DirectoryServer.getConnectionHandlers();
    assertNotNull(handlers);
    SNMPConnectionHandler snmpConnectionHandler = null;
    for (ConnectionHandler handler : handlers)
    {
      if (handler instanceof SNMPConnectionHandler)
      {
        snmpConnectionHandler = (SNMPConnectionHandler) handler;
        break;
      }
    }

    if (snmpConnectionHandler == null)
    {
      enableSnmp();
      synchronized (this)
      {
        this.wait(500);
      }
      for (ConnectionHandler handler : handlers)
      {
        if (handler instanceof SNMPConnectionHandler)
        {
          snmpConnectionHandler = (SNMPConnectionHandler) handler;
          break;
        }
      }
    }
    assertNotNull(snmpConnectionHandler);
    return snmpConnectionHandler;
  }



  /**
   * Enable SNMP with the port chosen in TestCaseUtils.
   *
   * @throws Exception
   *           if the handler cannot be enabled.
   */
  protected void enableSnmp() throws Exception
  {
    this.snmpPort = TestCaseUtils.findFreePort();
    this.trapSnmpPort = TestCaseUtils.findFreePort();

    ArrayList<Modification> mods = new ArrayList<Modification>();

    InternalClientConnection conn = InternalClientConnection
        .getRootConnection();

    mods.add(new Modification(ModificationType.REPLACE, Attributes.create(
        "ds-cfg-listen-port", String.valueOf(this.snmpPort))));

    mods.add(new Modification(ModificationType.REPLACE, Attributes.create(
        "ds-cfg-trap-port", String.valueOf(this.trapSnmpPort))));

    String hosts = InetAddress.getLocalHost().getCanonicalHostName();

    mods.add(new Modification(ModificationType.ADD, Attributes.create(
        "ds-cfg-traps-destination", hosts)));

    String jarFileLocation = System
        .getProperty("org.opends.server.snmp.opendmk");

    mods.add(new Modification(ModificationType.ADD, Attributes.create(
        "ds-cfg-opendmk-jarfile", jarFileLocation + File.separator
            + "jdmkrt.jar")));

    ModifyOperationBasis op = new ModifyOperationBasis(conn, conn
        .nextOperationID(), conn.nextMessageID(), new ArrayList<Control>(), DN
        .valueOf("cn=SNMP Connection Handler,cn=Connection Handlers,cn=config"),
        mods);
    op.run();

    mods.clear();

    mods.add(new Modification(ModificationType.REPLACE, Attributes
        .create("ds-cfg-enabled", "true")));

    op = new ModifyOperationBasis(conn, conn.nextOperationID(), conn
        .nextMessageID(), new ArrayList<Control>(), DN
        .valueOf("cn=SNMP Connection Handler,cn=Connection Handlers,cn=config"),
        mods);

    op.run();
  }



  /**
   * Enable JMX with the port chosen in TestCaseUtils.
   *
   * @throws Exception
   *           if the handler cannot be enabled.
   */
  protected void enableJmx() throws Exception
  {
    ArrayList<Modification> mods = new ArrayList<Modification>();

    InternalClientConnection conn = InternalClientConnection
        .getRootConnection();
    mods.add(new Modification(ModificationType.REPLACE, Attributes.create(
        "ds-cfg-enabled", "true")));

    ModifyOperationBasis op = new ModifyOperationBasis(conn, conn
        .nextOperationID(), conn.nextMessageID(), new ArrayList<Control>(), DN
        .valueOf("cn=JMX Connection Handler,cn=Connection Handlers,cn=config"),
        mods);
    op.run();
  }
}
