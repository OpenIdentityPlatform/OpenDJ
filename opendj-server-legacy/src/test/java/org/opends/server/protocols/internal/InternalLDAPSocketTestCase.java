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
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.protocols.internal;

import java.util.Hashtable;
import java.util.LinkedHashSet;
import java.util.List;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.BasicAttributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.ModificationItem;
import javax.naming.directory.SearchControls;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DereferenceAliasesPolicy;
import org.forgerock.opendj.ldap.SearchScope;
import org.opends.server.TestCaseUtils;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.ldap.AddRequestProtocolOp;
import org.opends.server.protocols.ldap.BindRequestProtocolOp;
import org.opends.server.protocols.ldap.CompareRequestProtocolOp;
import org.opends.server.protocols.ldap.DeleteRequestProtocolOp;
import org.opends.server.protocols.ldap.ExtendedRequestProtocolOp;
import org.opends.server.protocols.ldap.ExtendedResponseProtocolOp;
import org.opends.server.protocols.ldap.LDAPFilter;
import org.opends.server.protocols.ldap.LDAPMessage;
import org.opends.server.protocols.ldap.LDAPResultCode;
import org.opends.server.protocols.ldap.ModifyDNRequestProtocolOp;
import org.opends.server.protocols.ldap.ModifyRequestProtocolOp;
import org.opends.server.protocols.ldap.SearchRequestProtocolOp;
import org.opends.server.tools.LDAPReader;
import org.opends.server.tools.LDAPWriter;
import org.forgerock.opendj.ldap.DN;
import org.opends.server.types.RawAttribute;
import org.opends.server.types.RawModification;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.forgerock.opendj.ldap.ModificationType.*;
import static org.opends.server.util.CollectionUtils.*;
import static org.opends.server.util.ServerConstants.*;
import static org.testng.Assert.*;

/** This class provides a number of tests to cover the internal LDAP socket implementation. */
public class InternalLDAPSocketTestCase extends InternalTestCase
{
  /**
   * Ensures that the Directory Server is running.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @BeforeClass
  public void startServer() throws Exception
  {
    TestCaseUtils.startServer();
  }



  /**
   * Tests the ability to perform an add operation over the internal LDAP
   * socket.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAddOperation() throws Exception
  {
    TestCaseUtils.initializeTestBackend(false);
    assertFalse(DirectoryServer.entryExists(DN.valueOf("o=test")));

    try (InternalLDAPSocket socket = new InternalLDAPSocket();
        LDAPReader reader = new LDAPReader(socket);
        LDAPWriter writer = new LDAPWriter(socket))
    {
      writer.writeMessage(bindRequestMessage());

      LDAPMessage message = reader.readMessage();
      assertNotNull(message);
      assertEquals(message.getBindResponseProtocolOp().getResultCode(), 0);

      List<RawAttribute> attrList = newArrayList(
          RawAttribute.create("objectClass", "organization"),
          RawAttribute.create("o", "test"));

      AddRequestProtocolOp addRequest = new AddRequestProtocolOp(ByteString.valueOfUtf8("o=test"), attrList);
      writer.writeMessage(new LDAPMessage(2, addRequest));

      message = reader.readMessage();
      assertNotNull(message);
      assertEquals(message.getAddResponseProtocolOp().getResultCode(), LDAPResultCode.SUCCESS);
      assertTrue(DirectoryServer.entryExists(DN.valueOf("o=test")));
    }
  }



  /**
   * Tests the ability to perform an add operation over the internal LDAP
   * socket via JNDI.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testAddOperationThroughJNDI() throws Exception
  {
    TestCaseUtils.initializeTestBackend(false);
    assertFalse(DirectoryServer.entryExists(DN.valueOf("o=test")));


    Hashtable<String,String> env = new Hashtable<>();
    env.put(Context.INITIAL_CONTEXT_FACTORY,
            "com.sun.jndi.ldap.LdapCtxFactory");
    env.put("java.naming.ldap.factory.socket",
            InternalLDAPSocketFactory.class.getName());
    env.put(Context.PROVIDER_URL, "ldap://doesntmatter:389/");
    env.put(Context.SECURITY_AUTHENTICATION, "simple");
    env.put(Context.SECURITY_PRINCIPAL, "cn=Directory Manager");
    env.put(Context.SECURITY_CREDENTIALS, "password");
    env.put("com.sun.jndi.ldap.connect.pool.debug", "fine");

    DirContext context = new InitialDirContext(env);

    Attributes attributes = new BasicAttributes(true);

    Attribute objectClass = new BasicAttribute("objectClass");
    objectClass.add("top");
    objectClass.add("organization");
    attributes.put(objectClass);

    Attribute o = new BasicAttribute("o");
    o.add("test");
    attributes.put(o);

    context.createSubcontext("o=test", attributes);
    assertTrue(DirectoryServer.entryExists(DN.valueOf("o=test")));

    context.close();
  }



  /**
   * Tests the ability to perform a compare operation over the internal LDAP
   * socket.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testCompareOperation() throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    assertTrue(DirectoryServer.entryExists(DN.valueOf("o=test")));

    try (InternalLDAPSocket socket = new InternalLDAPSocket();
        LDAPReader reader = new LDAPReader(socket);
        LDAPWriter writer = new LDAPWriter(socket))
    {
      writer.writeMessage(bindRequestMessage());

      LDAPMessage message = reader.readMessage();
      assertNotNull(message);
      assertEquals(message.getBindResponseProtocolOp().getResultCode(), 0);

      CompareRequestProtocolOp compareRequest =
          new CompareRequestProtocolOp(ByteString.valueOfUtf8("o=test"), "o", ByteString.valueOfUtf8("test"));
      writer.writeMessage(new LDAPMessage(2, compareRequest));

      message = reader.readMessage();
      assertNotNull(message);
      assertEquals(message.getCompareResponseProtocolOp().getResultCode(), LDAPResultCode.COMPARE_TRUE);
    }
  }



  /**
   * Tests the ability to perform a compare operation over the internal LDAP
   * socket via JNDI.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testCompareOperationThroughJNDI() throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    assertTrue(DirectoryServer.entryExists(DN.valueOf("o=test")));


    Hashtable<String,String> env = new Hashtable<>();
    env.put(Context.INITIAL_CONTEXT_FACTORY,
            "com.sun.jndi.ldap.LdapCtxFactory");
    env.put("java.naming.ldap.factory.socket",
            InternalLDAPSocketFactory.class.getName());
    env.put(Context.PROVIDER_URL, "ldap://doesntmatter:389/");
    env.put(Context.SECURITY_AUTHENTICATION, "simple");
    env.put(Context.SECURITY_PRINCIPAL, "cn=Directory Manager");
    env.put(Context.SECURITY_CREDENTIALS, "password");

    DirContext context = new InitialDirContext(env);

    SearchControls poorlyNamedSearchControls = new SearchControls();
    poorlyNamedSearchControls.setSearchScope(SearchControls.OBJECT_SCOPE);
    poorlyNamedSearchControls.setReturningAttributes(new String[0]);

    NamingEnumeration results = context.search("o=test", "(o=test)",
                                               poorlyNamedSearchControls);
    assertTrue(results.hasMoreElements());
    assertNotNull(results.nextElement());
    assertFalse(results.hasMoreElements());

    context.close();
  }



  /**
   * Tests the ability to perform a delete operation over the internal LDAP
   * socket.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testDeleteOperation() throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    assertTrue(DirectoryServer.entryExists(DN.valueOf("o=test")));

    try (InternalLDAPSocket socket = new InternalLDAPSocket();
        LDAPReader reader = new LDAPReader(socket);
        LDAPWriter writer = new LDAPWriter(socket))
    {
      writer.writeMessage(bindRequestMessage());

      LDAPMessage message = reader.readMessage();
      assertNotNull(message);
      assertEquals(message.getBindResponseProtocolOp().getResultCode(), 0);

      DeleteRequestProtocolOp deleteRequest = new DeleteRequestProtocolOp(ByteString.valueOfUtf8("o=test"));
      writer.writeMessage(new LDAPMessage(2, deleteRequest));

      message = reader.readMessage();
      assertNotNull(message);
      assertEquals(message.getDeleteResponseProtocolOp().getResultCode(), LDAPResultCode.SUCCESS);
      assertFalse(DirectoryServer.entryExists(DN.valueOf("o=test")));
    }
  }



  /**
   * Tests the ability to perform a delete operation over the internal LDAP
   * socket via JNDI.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testDeleteOperationThroughJNDI() throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    assertTrue(DirectoryServer.entryExists(DN.valueOf("o=test")));


    Hashtable<String,String> env = new Hashtable<>();
    env.put(Context.INITIAL_CONTEXT_FACTORY,
            "com.sun.jndi.ldap.LdapCtxFactory");
    env.put("java.naming.ldap.factory.socket",
            InternalLDAPSocketFactory.class.getName());
    env.put(Context.PROVIDER_URL, "ldap://doesntmatter:389/");
    env.put(Context.SECURITY_AUTHENTICATION, "simple");
    env.put(Context.SECURITY_PRINCIPAL, "cn=Directory Manager");
    env.put(Context.SECURITY_CREDENTIALS, "password");

    DirContext context = new InitialDirContext(env);

    context.destroySubcontext("o=test");
    assertFalse(DirectoryServer.entryExists(DN.valueOf("o=test")));

    context.close();
  }



  /**
   * Tests the ability to perform an extended operation over the internal LDAP
   * socket using the "Who Am I?" request.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testExtendedOperation() throws Exception
  {
    try (InternalLDAPSocket socket = new InternalLDAPSocket();
        LDAPReader reader = new LDAPReader(socket);
        LDAPWriter writer = new LDAPWriter(socket))
    {
      writer.writeMessage(bindRequestMessage());

      LDAPMessage message = reader.readMessage();
      assertNotNull(message);
      assertEquals(message.getBindResponseProtocolOp().getResultCode(), 0);

      ExtendedRequestProtocolOp extendedRequest = new ExtendedRequestProtocolOp(OID_WHO_AM_I_REQUEST);
      writer.writeMessage(new LDAPMessage(2, extendedRequest));

      message = reader.readMessage();
      assertNotNull(message);

      ExtendedResponseProtocolOp extendedResponse = message.getExtendedResponseProtocolOp();
      assertEquals(extendedResponse.getResultCode(), LDAPResultCode.SUCCESS);
      assertTrue(extendedResponse.getValue().toString().equalsIgnoreCase(
          "dn:cn=Directory Manager,cn=Root DNs,cn=config"));
    }
  }



  /**
   * Tests the ability to perform a modify operation over the internal LDAP
   * socket.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testModifyOperation() throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    assertTrue(DirectoryServer.entryExists(DN.valueOf("o=test")));

    try (InternalLDAPSocket socket = new InternalLDAPSocket();
        LDAPReader reader = new LDAPReader(socket);
        LDAPWriter writer = new LDAPWriter(socket))
    {
      writer.writeMessage(bindRequestMessage());

      LDAPMessage message = reader.readMessage();
      assertNotNull(message);
      assertEquals(message.getBindResponseProtocolOp().getResultCode(), 0);

      List<RawModification> mods = newArrayList(
          RawModification.create(REPLACE, "description", "foo"));

      ModifyRequestProtocolOp modifyRequest = new ModifyRequestProtocolOp(ByteString.valueOfUtf8("o=test"), mods);
      writer.writeMessage(new LDAPMessage(2, modifyRequest));

      message = reader.readMessage();
      assertNotNull(message);
      assertEquals(message.getModifyResponseProtocolOp().getResultCode(), LDAPResultCode.SUCCESS);
    }
  }



  /**
   * @return
   */
  private LDAPMessage bindRequestMessage()
  {
    BindRequestProtocolOp bindRequest = bindRequest();
    LDAPMessage message = new LDAPMessage(1, bindRequest);
    return message;
  }



  /**
   * Tests the ability to perform a modify operation over the internal LDAP
   * socket via JNDI.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testModifyOperationThroughJNDI() throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    assertTrue(DirectoryServer.entryExists(DN.valueOf("o=test")));


    Hashtable<String, String> env = new Hashtable<>();
    env.put(Context.INITIAL_CONTEXT_FACTORY,
            "com.sun.jndi.ldap.LdapCtxFactory");
    env.put("java.naming.ldap.factory.socket",
            InternalLDAPSocketFactory.class.getName());
    env.put(Context.PROVIDER_URL, "ldap://doesntmatter:389/");
    env.put(Context.SECURITY_AUTHENTICATION, "simple");
    env.put(Context.SECURITY_PRINCIPAL, "cn=Directory Manager");
    env.put(Context.SECURITY_CREDENTIALS, "password");

    DirContext context = new InitialDirContext(env);

    ModificationItem[] mods =
    {
      new ModificationItem(DirContext.REPLACE_ATTRIBUTE, new BasicAttribute("description", "foo"))
    };

    context.modifyAttributes("o=test", mods);
    context.close();
  }



  /**
   * Tests the ability to perform a modify DN operation over the internal LDAP
   * socket.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testModifyDNOperation() throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.addEntry(
      "dn: ou=People,o=test",
      "objectClass: top",
      "objectClass: organizationalUnit",
      "ou: People");

    assertTrue(DirectoryServer.entryExists(DN.valueOf("ou=People,o=test")));
    assertFalse(DirectoryServer.entryExists(DN.valueOf("ou=Users,o=test")));

    try (InternalLDAPSocket socket = new InternalLDAPSocket();
        LDAPReader reader = new LDAPReader(socket);
        LDAPWriter writer = new LDAPWriter(socket))
    {
      LDAPMessage message = bindRequestMessage();
      writer.writeMessage(message);

      message = reader.readMessage();
      assertNotNull(message);
      assertEquals(message.getBindResponseProtocolOp().getResultCode(), 0);

      ModifyDNRequestProtocolOp modifyDNRequest =
          new ModifyDNRequestProtocolOp(ByteString.valueOfUtf8("ou=People,o=test"), ByteString.valueOfUtf8("ou=Users"),
              true);
      writer.writeMessage(new LDAPMessage(2, modifyDNRequest));

      message = reader.readMessage();
      assertNotNull(message);
      assertEquals(message.getModifyDNResponseProtocolOp().getResultCode(), LDAPResultCode.SUCCESS);

      assertFalse(DirectoryServer.entryExists(DN.valueOf("ou=People,o=test")));
      assertTrue(DirectoryServer.entryExists(DN.valueOf("ou=Users,o=test")));
    }
  }



  /**
   * @return
   */
  private BindRequestProtocolOp bindRequest()
  {
    BindRequestProtocolOp bindRequest =
        new BindRequestProtocolOp(ByteString.valueOfUtf8("cn=Directory Manager"), 3, ByteString
            .valueOfUtf8("password"));
    return bindRequest;
  }



  /**
   * Tests the ability to perform a modify DN operation over the internal LDAP
   * socket via JNDI.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testModifyDNOperationThroughJNDI() throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.addEntry(
      "dn: ou=People,o=test",
      "objectClass: top",
      "objectClass: organizationalUnit",
      "ou: People");

    assertTrue(DirectoryServer.entryExists(DN.valueOf("ou=People,o=test")));
    assertFalse(DirectoryServer.entryExists(DN.valueOf("ou=Users,o=test")));


    Hashtable<String,String> env = new Hashtable<>();
    env.put(Context.INITIAL_CONTEXT_FACTORY,
            "com.sun.jndi.ldap.LdapCtxFactory");
    env.put("java.naming.ldap.factory.socket",
            InternalLDAPSocketFactory.class.getName());
    env.put(Context.PROVIDER_URL, "ldap://doesntmatter:389/");
    env.put(Context.SECURITY_AUTHENTICATION, "simple");
    env.put(Context.SECURITY_PRINCIPAL, "cn=Directory Manager");
    env.put(Context.SECURITY_CREDENTIALS, "password");

    DirContext context = new InitialDirContext(env);

    context.rename("ou=People,o=test", "ou=Users,o=test");

    assertFalse(DirectoryServer.entryExists(DN.valueOf("ou=People,o=test")));
    assertTrue(DirectoryServer.entryExists(DN.valueOf("ou=Users,o=test")));

    context.close();
  }



  /**
   * Tests the ability to perform a search operation over the internal LDAP
   * socket.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testSearchOperation() throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    assertTrue(DirectoryServer.entryExists(DN.valueOf("o=test")));

    try (InternalLDAPSocket socket = new InternalLDAPSocket();
        LDAPReader reader = new LDAPReader(socket);
        LDAPWriter writer = new LDAPWriter(socket))
    {
      writer.writeMessage(bindRequestMessage());

      LDAPMessage message = reader.readMessage();
      assertNotNull(message);
      assertEquals(message.getBindResponseProtocolOp().getResultCode(), 0);

      SearchRequestProtocolOp searchRequest =
          new SearchRequestProtocolOp(ByteString.valueOfUtf8("o=test"), SearchScope.BASE_OBJECT,
              DereferenceAliasesPolicy.NEVER, 0, 0, false, LDAPFilter.objectClassPresent(), new LinkedHashSet<String>());
      writer.writeMessage(new LDAPMessage(2, searchRequest));

      message = reader.readMessage();
      assertNotNull(message);
      assertEquals(message.getSearchResultEntryProtocolOp().getDN(), DN.valueOf("o=test"));

      message = reader.readMessage();
      assertNotNull(message);
      assertEquals(message.getSearchResultDoneProtocolOp().getResultCode(), LDAPResultCode.SUCCESS);
    }
  }



  /**
   * Tests the ability to perform a searcj operation over the internal LDAP
   * socket via JNDI.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testSearchOperationThroughJNDI() throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    assertTrue(DirectoryServer.entryExists(DN.valueOf("o=test")));


    Hashtable<String,String> env = new Hashtable<>();
    env.put(Context.INITIAL_CONTEXT_FACTORY,
            "com.sun.jndi.ldap.LdapCtxFactory");
    env.put("java.naming.ldap.factory.socket",
            InternalLDAPSocketFactory.class.getName());
    env.put(Context.PROVIDER_URL, "ldap://doesntmatter:389/");
    env.put(Context.SECURITY_AUTHENTICATION, "simple");
    env.put(Context.SECURITY_PRINCIPAL, "cn=Directory Manager");
    env.put(Context.SECURITY_CREDENTIALS, "password");

    DirContext context = new InitialDirContext(env);

    SearchControls poorlyNamedSearchControls = new SearchControls();
    poorlyNamedSearchControls.setSearchScope(SearchControls.OBJECT_SCOPE);

    NamingEnumeration results = context.search("o=test", "(objectClass=*)",
                                               poorlyNamedSearchControls);
    assertTrue(results.hasMoreElements());
    assertNotNull(results.nextElement());
    assertFalse(results.hasMoreElements());

    context.close();
  }
}
