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
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.protocols.ldap ;

import static org.opends.server.config.ConfigConstants.ATTR_LISTEN_PORT;
import org.opends.server.types.Entry;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.admin.server.AdminTestCaseUtils;
import org.opends.server.admin.std.meta.LDAPConnectionHandlerCfgDefn;
import org.opends.server.admin.std.server.LDAPConnectionHandlerCfg;
import org.opends.server.config.ConfigException;
import org.opends.server.protocols.asn1.ASN1Boolean;
import org.opends.server.protocols.asn1.ASN1Element;
import org.opends.server.protocols.asn1.ASN1Long;
import org.opends.server.protocols.asn1.ASN1Sequence;
import org.opends.server.types.Attribute;
import org.testng.annotations.Test;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.ListIterator;

/**
 * An abstract class that all types  unit test should extend.
 */

@Test(groups = { "precommit", "ldap" }, sequential = true)
public abstract class LdapTestCase extends DirectoryServerTestCase
{
	
	private static String localHost = "127.0.0.1";
	
  /**
   * Determine whether one LDAPAttribute is equal to another.
   * The values of the attribute must be identical and in the same order.
   * @param a1 The first LDAPAttribute.
   * @param a2 The second LDAPAttribute.
   * @return true if the first LDAPAttribute is equal to the second.
   */
  static boolean testEqual(LDAPAttribute a1, LDAPAttribute a2)
  {
    if (!a1.getAttributeType().equals(a2.getAttributeType()))
    {
      return false;
    }
    return a1.getValues().equals(a2.getValues());
  }

  /**
   * Determine whether one list of LDAPAttribute is equal to another.
   * @param list1 The first list of LDAPAttribute.
   * @param list2 The second list of LDAPAttribute.
   * @return true if the first list of LDAPAttribute is equal to the second.
   */
  static boolean testEqual(LinkedList<LDAPAttribute> list1,
                           LinkedList<LDAPAttribute> list2)
  {
    ListIterator<LDAPAttribute> e1 = list1.listIterator();
    ListIterator<LDAPAttribute> e2 = list2.listIterator();
    while(e1.hasNext() && e2.hasNext()) {
      LDAPAttribute o1 = e1.next();
      LDAPAttribute o2 = e2.next();
      if (!(o1==null ? o2==null : testEqual(o1, o2)))
        return false;
    }
    return !(e1.hasNext() || e2.hasNext());
  }

  /**
   * Generate an exception by writing a long into a integer element.
   * @param op The op.
   * @param type The type of sequence.
   * @param index The index into the element to write to.
   * @throws Exception If the protocol op decode can't write the sequence.
   */
  static void 
  badIntegerElement(ProtocolOp op, byte type, int index) throws Exception {
	  ASN1Element element = op.encode();
	  ArrayList<ASN1Element> elements = ((ASN1Sequence)element).elements();
	  elements.set(index, new ASN1Long(Long.MAX_VALUE));
	  ProtocolOp.decode(new ASN1Sequence(type, elements));
  }

  /**
   * Generate an exception by adding an element.
   * @param op The op.
   * @param type The type of sequence.
   * @throws Exception If the protocol op decode has too many elements.
   */
  static void 
  tooManyElements(ProtocolOp op, byte type) throws Exception
  {
	  ASN1Element element = op.encode();
	  ArrayList<ASN1Element> elements = ((ASN1Sequence)element).elements();
	  elements.add(new ASN1Boolean(true));
	  ProtocolOp.decode(new ASN1Sequence(type, elements));
  }

  /**
   * Generate an exception by removing an element.
   * @param op The op.
   * @param type The type of sequence.
   * @throws Exception If the protocol op decode has too few elements.
   */
  static void 
  tooFewElements(ProtocolOp op, byte type) throws Exception
  {
	  ASN1Element element = op.encode();
	  ArrayList<ASN1Element> elements = ((ASN1Sequence)element).elements();
	  elements.remove(0);
	  ProtocolOp.decode(new ASN1Sequence(type, elements));
  }

  /**
   * Test toString methods.
   * @param op The op.
   * @throws Exception If the toString method fails.
   */
  static void 
  toString(ProtocolOp op) throws Exception {
	  StringBuilder sb = new StringBuilder();
	  op.toString(sb);
	  op.toString(sb, 1);
  }
  
  /**
   * Generate a LDAPConnectionHandler from a entry. The listen port is
   * determined automatically, so no ATTR_LISTEN_PORT should be in the
   * entry.
   * 
   * @param handlerEntry The entry to be used to configure the handle.
   * @return Returns the new LDAP connection handler.
   * @throws Exception if the handler cannot be initialized.
   */
  static LDAPConnectionHandler 
  getLDAPHandlerInstance(Entry handlerEntry) throws Exception {
	  ServerSocket serverLdapSocket = new ServerSocket();
	  serverLdapSocket.setReuseAddress(true);
	  serverLdapSocket.bind(new InetSocketAddress(localHost, 0));
	  long serverLdapPort = serverLdapSocket.getLocalPort();
    serverLdapSocket.close();
	  Attribute a=new Attribute(ATTR_LISTEN_PORT, String.valueOf(serverLdapPort));
	  handlerEntry.addAttribute(a,null);
    LDAPConnectionHandlerCfg config =
      getConfiguration(handlerEntry);
    LDAPConnectionHandler handler = new LDAPConnectionHandler();
    handler.initializeConnectionHandler(config);
	  return handler;
  }

  /**
   * Decode an LDAP connection handler configuration entry.
   * 
   * @param handlerEntry
   *          The configuration entry.
   * @return Returns the decoded LDAP connection handler
   *         configuration.
   * @throws ConfigException
   *           If the configuration entry could not be decoded.
   */
  static LDAPConnectionHandlerCfg getConfiguration(
      Entry handlerEntry) throws ConfigException {
    return AdminTestCaseUtils.getConfiguration(
        LDAPConnectionHandlerCfgDefn
            .getInstance(), handlerEntry);
  }
  
  /**
   * @return A free port number.
   * @throws Exception
   *           if socket cannot be created or bound to.
   */
static long
  getFreePort() throws Exception {
	  ServerSocket serverLdapSocket = new ServerSocket();
	  serverLdapSocket.setReuseAddress(true);
	  serverLdapSocket.bind(new InetSocketAddress(localHost, 0));
	  return serverLdapSocket.getLocalPort();
  }
}
