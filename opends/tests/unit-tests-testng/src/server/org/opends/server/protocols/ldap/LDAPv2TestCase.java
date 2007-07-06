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



import java.net.Socket;
import java.util.ArrayList;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import org.opends.server.TestCaseUtils;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.asn1.ASN1Reader;
import org.opends.server.protocols.asn1.ASN1Writer;
import org.opends.server.types.DereferencePolicy;
import org.opends.server.types.ModificationType;
import org.opends.server.types.RawAttribute;
import org.opends.server.types.RawModification;
import org.opends.server.types.SearchScope;

import static org.testng.Assert.*;

import static org.opends.server.util.ServerConstants.*;



/**
 * This class provides a number of tests to ensure that the server interacts
 * with LDAPv2 clients as expected.
 */
public class LDAPv2TestCase
       extends LdapTestCase
{
  /**
   * Ensure that the Directory Server is running.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @BeforeClass()
  public void startServer()
         throws Exception
  {
    TestCaseUtils.startServer();
    TestCaseUtils.initializeTestBackend(true);
  }



  /**
   * Tests to ensure that the server will reject an LDAPv2 bind request if
   * configured to do so.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testRejectBindRequest()
         throws Exception
  {
    TestCaseUtils.applyModifications(
      "dn: cn=LDAP Connection Handler,cn=Connection Handlers,cn=config",
      "changetype: modify",
      "replace: ds-cfg-allow-ldapv2",
      "ds-cfg-allow-ldapv2: false");

    Socket     s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);

    try
    {
      BindRequestProtocolOp bindRequest =
           new BindRequestProtocolOp(
                    new ASN1OctetString("cn=Directory Manager"), 2,
                    new ASN1OctetString("password"));
      LDAPMessage message = new LDAPMessage(1, bindRequest);
      w.writeElement(message.encode());

      message = LDAPMessage.decode(r.readElement().decodeAsSequence());
      BindResponseProtocolOp bindResponse = message.getBindResponseProtocolOp();
      assertEquals(bindResponse.getResultCode(),
                   LDAPResultCode.INAPPROPRIATE_AUTHENTICATION);
    }
    finally
    {
      TestCaseUtils.applyModifications(
        "dn: cn=LDAP Connection Handler,cn=Connection Handlers,cn=config",
        "changetype: modify",
        "replace: ds-cfg-allow-ldapv2",
        "ds-cfg-allow-ldapv2: true");

      try
      {
        r.close();
      } catch (Exception e) {}
      try
      {
        w.close();
      } catch (Exception e) {}
      try
      {
        s.close();
      } catch (Exception e) {}
    }
  }



  /**
   * Tests to ensure that the server will reject an extended request from an
   * LDAPv2 client.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testRejectExtendedRequest()
         throws Exception
  {
    Socket     s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);

    try
    {
      BindRequestProtocolOp bindRequest =
           new BindRequestProtocolOp(
                    new ASN1OctetString("cn=Directory Manager"), 2,
                    new ASN1OctetString("password"));
      LDAPMessage message = new LDAPMessage(1, bindRequest);
      w.writeElement(message.encode());

      message = LDAPMessage.decode(r.readElement().decodeAsSequence());
      BindResponseProtocolOp bindResponse = message.getBindResponseProtocolOp();
      assertEquals(bindResponse.getResultCode(), 0);

      ExtendedRequestProtocolOp extendedRequest =
           new ExtendedRequestProtocolOp(OID_START_TLS_REQUEST);
      message = new LDAPMessage(2, extendedRequest);
      w.writeElement(message.encode());

      assertNull(r.readElement());
    }
    finally
    {
      try
      {
        r.close();
      } catch (Exception e) {}
      try
      {
        w.close();
      } catch (Exception e) {}
      try
      {
        s.close();
      } catch (Exception e) {}
    }
  }



  /**
   * Tests to ensure that the server will reject an LDAPv2 add request if it
   * contains any controls.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testRejectAddControls()
         throws Exception
  {
    Socket     s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);

    try
    {
      BindRequestProtocolOp bindRequest =
           new BindRequestProtocolOp(
                    new ASN1OctetString("cn=Directory Manager"), 2,
                    new ASN1OctetString("password"));
      LDAPMessage message = new LDAPMessage(1, bindRequest);
      w.writeElement(message.encode());

      message = LDAPMessage.decode(r.readElement().decodeAsSequence());
      BindResponseProtocolOp bindResponse = message.getBindResponseProtocolOp();
      assertEquals(bindResponse.getResultCode(), 0);

      ArrayList<RawAttribute> addAttrs = new ArrayList<RawAttribute>();
      addAttrs.add(RawAttribute.create("objectClass", "organizationalUnit"));
      addAttrs.add(RawAttribute.create("ou", "People"));

      AddRequestProtocolOp addRequest =
           new AddRequestProtocolOp(new ASN1OctetString("ou=People,o=test"),
                                    addAttrs);
      ArrayList<LDAPControl> controls = new ArrayList<LDAPControl>(1);
      controls.add(new LDAPControl(OID_MANAGE_DSAIT_CONTROL, true));
      message = new LDAPMessage(2, addRequest, controls);
      w.writeElement(message.encode());

      message = LDAPMessage.decode(r.readElement().decodeAsSequence());
      AddResponseProtocolOp addResponse = message.getAddResponseProtocolOp();
      assertEquals(addResponse.getResultCode(), LDAPResultCode.PROTOCOL_ERROR);
    }
    finally
    {
      try
      {
        r.close();
      } catch (Exception e) {}
      try
      {
        w.close();
      } catch (Exception e) {}
      try
      {
        s.close();
      } catch (Exception e) {}
    }
  }



  /**
   * Tests to ensure that the server will reject an LDAPv2 bind request if it
   * contains any controls and LDAPv2 binds are allowed.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testRejectBindControls()
         throws Exception
  {
    Socket     s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);

    try
    {
      BindRequestProtocolOp bindRequest =
           new BindRequestProtocolOp(
                    new ASN1OctetString("cn=Directory Manager"), 2,
                    new ASN1OctetString("password"));
      ArrayList<LDAPControl> controls = new ArrayList<LDAPControl>(1);
      controls.add(new LDAPControl(OID_MANAGE_DSAIT_CONTROL, true));
      LDAPMessage message = new LDAPMessage(1, bindRequest, controls);
      w.writeElement(message.encode());

      message = LDAPMessage.decode(r.readElement().decodeAsSequence());
      BindResponseProtocolOp bindResponse = message.getBindResponseProtocolOp();
      assertEquals(bindResponse.getResultCode(), LDAPResultCode.PROTOCOL_ERROR);
    }
    finally
    {
      try
      {
        r.close();
      } catch (Exception e) {}
      try
      {
        w.close();
      } catch (Exception e) {}
      try
      {
        s.close();
      } catch (Exception e) {}
    }
  }



  /**
   * Tests to ensure that the server will reject an LDAPv2 compare request if it
   * contains any controls.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testRejectCompareControls()
         throws Exception
  {
    Socket     s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);

    try
    {
      BindRequestProtocolOp bindRequest =
           new BindRequestProtocolOp(
                    new ASN1OctetString("cn=Directory Manager"), 2,
                    new ASN1OctetString("password"));
      LDAPMessage message = new LDAPMessage(1, bindRequest);
      w.writeElement(message.encode());

      message = LDAPMessage.decode(r.readElement().decodeAsSequence());
      BindResponseProtocolOp bindResponse = message.getBindResponseProtocolOp();
      assertEquals(bindResponse.getResultCode(), 0);

      CompareRequestProtocolOp compareRequest =
           new CompareRequestProtocolOp(new ASN1OctetString("o=test"),
                                        "o", new ASN1OctetString("test"));
      ArrayList<LDAPControl> controls = new ArrayList<LDAPControl>(1);
      controls.add(new LDAPControl(OID_MANAGE_DSAIT_CONTROL, true));
      message = new LDAPMessage(2, compareRequest, controls);
      w.writeElement(message.encode());

      message = LDAPMessage.decode(r.readElement().decodeAsSequence());
      CompareResponseProtocolOp compareResponse =
           message.getCompareResponseProtocolOp();
      assertEquals(compareResponse.getResultCode(),
                   LDAPResultCode.PROTOCOL_ERROR);
    }
    finally
    {
      try
      {
        r.close();
      } catch (Exception e) {}
      try
      {
        w.close();
      } catch (Exception e) {}
      try
      {
        s.close();
      } catch (Exception e) {}
    }
  }



  /**
   * Tests to ensure that the server will reject an LDAPv2 delete request if it
   * contains any controls.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testRejectDeleteControls()
         throws Exception
  {
    Socket     s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);

    try
    {
      BindRequestProtocolOp bindRequest =
           new BindRequestProtocolOp(
                    new ASN1OctetString("cn=Directory Manager"), 2,
                    new ASN1OctetString("password"));
      LDAPMessage message = new LDAPMessage(1, bindRequest);
      w.writeElement(message.encode());

      message = LDAPMessage.decode(r.readElement().decodeAsSequence());
      BindResponseProtocolOp bindResponse = message.getBindResponseProtocolOp();
      assertEquals(bindResponse.getResultCode(), 0);

      DeleteRequestProtocolOp deleteRequest =
           new DeleteRequestProtocolOp(new ASN1OctetString("o=test"));
      ArrayList<LDAPControl> controls = new ArrayList<LDAPControl>(1);
      controls.add(new LDAPControl(OID_MANAGE_DSAIT_CONTROL, true));
      message = new LDAPMessage(2, deleteRequest, controls);
      w.writeElement(message.encode());

      message = LDAPMessage.decode(r.readElement().decodeAsSequence());
      DeleteResponseProtocolOp deleteResponse =
           message.getDeleteResponseProtocolOp();
      assertEquals(deleteResponse.getResultCode(),
                   LDAPResultCode.PROTOCOL_ERROR);
    }
    finally
    {
      try
      {
        r.close();
      } catch (Exception e) {}
      try
      {
        w.close();
      } catch (Exception e) {}
      try
      {
        s.close();
      } catch (Exception e) {}
    }
  }



  /**
   * Tests to ensure that the server will reject an LDAPv2 modify request if it
   * contains any controls.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testRejectModifyControls()
         throws Exception
  {
    Socket     s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);

    try
    {
      BindRequestProtocolOp bindRequest =
           new BindRequestProtocolOp(
                    new ASN1OctetString("cn=Directory Manager"), 2,
                    new ASN1OctetString("password"));
      LDAPMessage message = new LDAPMessage(1, bindRequest);
      w.writeElement(message.encode());

      message = LDAPMessage.decode(r.readElement().decodeAsSequence());
      BindResponseProtocolOp bindResponse = message.getBindResponseProtocolOp();
      assertEquals(bindResponse.getResultCode(), 0);

      ArrayList<RawModification> mods = new ArrayList<RawModification>();
      mods.add(RawModification.create(ModificationType.REPLACE,
                                      "description", "foo"));

      ModifyRequestProtocolOp modifyRequest =
           new ModifyRequestProtocolOp(new ASN1OctetString("o=test"), mods);
      ArrayList<LDAPControl> controls = new ArrayList<LDAPControl>(1);
      controls.add(new LDAPControl(OID_MANAGE_DSAIT_CONTROL, true));
      message = new LDAPMessage(2, modifyRequest, controls);
      w.writeElement(message.encode());

      message = LDAPMessage.decode(r.readElement().decodeAsSequence());
      ModifyResponseProtocolOp modifyResponse =
           message.getModifyResponseProtocolOp();
      assertEquals(modifyResponse.getResultCode(),
                   LDAPResultCode.PROTOCOL_ERROR);
    }
    finally
    {
      try
      {
        r.close();
      } catch (Exception e) {}
      try
      {
        w.close();
      } catch (Exception e) {}
      try
      {
        s.close();
      } catch (Exception e) {}
    }
  }



  /**
   * Tests to ensure that the server will reject an LDAPv2 modify DN request if
   * it contains any controls.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testRejectModifyDNControls()
         throws Exception
  {
    Socket     s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);

    try
    {
      BindRequestProtocolOp bindRequest =
           new BindRequestProtocolOp(
                    new ASN1OctetString("cn=Directory Manager"), 2,
                    new ASN1OctetString("password"));
      LDAPMessage message = new LDAPMessage(1, bindRequest);
      w.writeElement(message.encode());

      message = LDAPMessage.decode(r.readElement().decodeAsSequence());
      BindResponseProtocolOp bindResponse = message.getBindResponseProtocolOp();
      assertEquals(bindResponse.getResultCode(), 0);

      ModifyDNRequestProtocolOp modifyDNRequest =
           new ModifyDNRequestProtocolOp(new ASN1OctetString("o=test"),
                                         new ASN1OctetString("cn=test"), false);
      ArrayList<LDAPControl> controls = new ArrayList<LDAPControl>(1);
      controls.add(new LDAPControl(OID_MANAGE_DSAIT_CONTROL, true));
      message = new LDAPMessage(2, modifyDNRequest, controls);
      w.writeElement(message.encode());

      message = LDAPMessage.decode(r.readElement().decodeAsSequence());
      ModifyDNResponseProtocolOp modifyDNResponse =
           message.getModifyDNResponseProtocolOp();
      assertEquals(modifyDNResponse.getResultCode(),
                   LDAPResultCode.PROTOCOL_ERROR);
    }
    finally
    {
      try
      {
        r.close();
      } catch (Exception e) {}
      try
      {
        w.close();
      } catch (Exception e) {}
      try
      {
        s.close();
      } catch (Exception e) {}
    }
  }



  /**
   * Tests to ensure that the server will reject an LDAPv2 search request if it
   * contains any controls.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testRejectSearchControls()
         throws Exception
  {
    Socket     s = new Socket("127.0.0.1", TestCaseUtils.getServerLdapPort());
    ASN1Reader r = new ASN1Reader(s);
    ASN1Writer w = new ASN1Writer(s);

    try
    {
      BindRequestProtocolOp bindRequest =
           new BindRequestProtocolOp(
                    new ASN1OctetString("cn=Directory Manager"), 2,
                    new ASN1OctetString("password"));
      LDAPMessage message = new LDAPMessage(1, bindRequest);
      w.writeElement(message.encode());

      message = LDAPMessage.decode(r.readElement().decodeAsSequence());
      BindResponseProtocolOp bindResponse = message.getBindResponseProtocolOp();
      assertEquals(bindResponse.getResultCode(), 0);

      SearchRequestProtocolOp searchRequest =
           new SearchRequestProtocolOp(new ASN1OctetString(),
                    SearchScope.BASE_OBJECT,
                    DereferencePolicy.NEVER_DEREF_ALIASES, 0, 0, false,
                    LDAPFilter.decode("(objectClass=*)"), null);
      ArrayList<LDAPControl> controls = new ArrayList<LDAPControl>(1);
      controls.add(new LDAPControl(OID_MANAGE_DSAIT_CONTROL, true));
      message = new LDAPMessage(2, searchRequest, controls);
      w.writeElement(message.encode());

      message = LDAPMessage.decode(r.readElement().decodeAsSequence());
      SearchResultDoneProtocolOp searchDone =
           message.getSearchResultDoneProtocolOp();
      assertEquals(searchDone.getResultCode(), LDAPResultCode.PROTOCOL_ERROR);
    }
    finally
    {
      try
      {
        r.close();
      } catch (Exception e) {}
      try
      {
        w.close();
      } catch (Exception e) {}
      try
      {
        s.close();
      } catch (Exception e) {}
    }
  }
}

