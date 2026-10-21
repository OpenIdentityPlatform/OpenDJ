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
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2026 3A Systems, LLC.
 */
package org.opends.dsml.protocol;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

import javax.xml.parsers.DocumentBuilderFactory;

import org.forgerock.testng.ForgeRockTestCase;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.w3c.dom.Document;

import jakarta.xml.soap.MessageFactory;
import jakarta.xml.soap.MimeHeaders;
import jakarta.xml.soap.SOAPConstants;
import jakarta.xml.soap.SOAPException;
import jakarta.xml.soap.SOAPMessage;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Pins the entity-expansion behaviour of the SAAJ implementation the DSMLv2
 * gateway parses requests with.
 * <p>
 * {@link DSMLServlet#doPost} hands the request body straight to
 * {@code MessageFactory.createMessage(MimeHeaders, InputStream)}, which builds
 * its own parser: the servlet cannot configure it, and the SAAJ API offers no
 * way to assert that it is safe. The protection therefore comes from the
 * implementation on the classpath, and swapping that implementation would
 * remove it silently - hence this test (GHSA-jr68-5c58-j463).
 * <p>
 * The tests only ever reference {@code file:} URIs under a temporary
 * directory, so they perform no network or DNS I/O.
 */
@SuppressWarnings("javadoc")
@Test(groups = { "precommit", "dsml" })
public class SoapEntityExpansionTestCase extends ForgeRockTestCase
{
  private static final String CANARY = "s3cr3t-canary-value";
  /** Literal text sitting next to the entity reference in every payload. */
  private static final String MARKER = "attribute-value-marker";
  private static final String SOAP_11_CONTENT_TYPE = "text/xml; charset=utf-8";
  private static final String SOAP_12_CONTENT_TYPE = "application/soap+xml; charset=utf-8";

  private Path tempDir;
  /** File an XXE payload tries to read. */
  private String canaryUri;
  /** External DTD subset declaring an entity which reads the canary file. */
  private String externalDtdUri;

  @BeforeClass
  public void createPayloadFiles() throws IOException
  {
    tempDir = Files.createTempDirectory("dsml-xxe-test");
    Path canary = Files.write(tempDir.resolve("canary.txt"), CANARY.getBytes(StandardCharsets.UTF_8));
    canaryUri = canary.toUri().toASCIIString();
    Path dtd = Files.write(tempDir.resolve("canary.dtd"),
        ("<!ENTITY xxe SYSTEM \"" + canaryUri + "\">").getBytes(StandardCharsets.UTF_8));
    externalDtdUri = dtd.toUri().toASCIIString();
  }

  @AfterClass
  public void deletePayloadFiles() throws IOException
  {
    if (tempDir != null)
    {
      try (Stream<Path> paths = Files.walk(tempDir))
      {
        paths.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
      }
    }
  }

  @DataProvider
  public Object[][] entityPayloads()
  {
    return new Object[][] {
      { "SOAP 1.1, external general entity", SOAPConstants.SOAP_1_1_PROTOCOL, SOAP_11_CONTENT_TYPE,
        "<?xml version=\"1.0\"?>"
        + "<!DOCTYPE soapenv:Envelope [<!ENTITY xxe SYSTEM \"" + canaryUri + "\">]>"
        + soap11Envelope("&xxe;") },
      { "SOAP 1.1, external DTD subset", SOAPConstants.SOAP_1_1_PROTOCOL, SOAP_11_CONTENT_TYPE,
        "<?xml version=\"1.0\"?>"
        + "<!DOCTYPE soapenv:Envelope SYSTEM \"" + externalDtdUri + "\">"
        + soap11Envelope("&xxe;") },
      { "SOAP 1.2, external general entity", SOAPConstants.SOAP_1_2_PROTOCOL, SOAP_12_CONTENT_TYPE,
        "<?xml version=\"1.0\"?>"
        + "<!DOCTYPE env:Envelope [<!ENTITY xxe SYSTEM \"" + canaryUri + "\">]>"
        + soap12Envelope("&xxe;") },
      { "SOAP 1.2, external DTD subset", SOAPConstants.SOAP_1_2_PROTOCOL, SOAP_12_CONTENT_TYPE,
        "<?xml version=\"1.0\"?>"
        + "<!DOCTYPE env:Envelope SYSTEM \"" + externalDtdUri + "\">"
        + soap12Envelope("&xxe;") },
    };
  }

  /**
   * A request declaring an external entity must either be rejected outright,
   * or be parsed without the entity ever being resolved. Both outcomes keep
   * the file contents out of the batch request; anything else is an XXE.
   */
  @Test(dataProvider = "entityPayloads")
  public void testExternalEntityIsNeverExpanded(String label, String protocol, String contentType,
      String request) throws Exception
  {
    String body;
    try
    {
      body = parseBody(protocol, contentType, request);
    }
    catch (SOAPException e)
    {
      // Rejected before any entity could be resolved.
      return;
    }
    // The implementation parsed the request instead of rejecting it. The rest
    // of this method is then the assertion that matters, so first make sure it
    // is looking at a body that really was parsed - an empty one would satisfy
    // the check below without proving anything.
    assertTrue(body.contains(MARKER),
        label + ": the request was accepted but its body is not there: " + body);
    assertFalse(body.contains(CANARY),
        label + ": the external entity was expanded into the request body: " + body);
  }

  /** The rejection above must not come from the gateway refusing plain requests. */
  @Test
  public void testRequestWithoutDoctypeIsParsed() throws Exception
  {
    String body = parseBody(SOAPConstants.SOAP_1_1_PROTOCOL, SOAP_11_CONTENT_TYPE,
        "<?xml version=\"1.0\"?>" + soap11Envelope("a plain attribute value"));
    assertTrue(body.contains("a plain attribute value"), "unexpected body: " + body);
  }

  /**
   * The payloads above are only worth anything if they would actually leak the
   * file through a parser that is not protected - otherwise the whole test case
   * could keep passing against an implementation that stopped protecting
   * anything. This is that control: the very same request, through a plain
   * DocumentBuilder, must hand the file contents over.
   */
  @Test
  public void testPayloadLeaksThroughAnUnprotectedParser() throws Exception
  {
    String request = null;
    for (Object[] payload : entityPayloads())
    {
      if (((String) payload[0]).contains("external general entity"))
      {
        request = (String) payload[3];
        break;
      }
    }
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(true);
    Document document = factory.newDocumentBuilder()
        .parse(new ByteArrayInputStream(request.getBytes(StandardCharsets.UTF_8)));
    assertTrue(document.getDocumentElement().getTextContent().contains(CANARY),
        "the XXE payload no longer reads the file even without any protection, "
            + "so this test case proves nothing anymore");
  }

  /** Parses a request the way {@link DSMLServlet#doPost} does, and returns the body as text. */
  private String parseBody(String protocol, String contentType, String request) throws Exception
  {
    MimeHeaders mimeHeaders = new MimeHeaders();
    mimeHeaders.addHeader("Content-Type", contentType);
    SOAPMessage message = MessageFactory.newInstance(protocol).createMessage(mimeHeaders,
        new ByteArrayInputStream(request.getBytes(StandardCharsets.UTF_8)));
    // SAAJ parses lazily, the body is only built here.
    return message.getSOAPBody().getTextContent();
  }

  private static String soap11Envelope(String dn)
  {
    return "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\">"
        + "<soapenv:Body>" + batchRequest(dn) + "</soapenv:Body></soapenv:Envelope>";
  }

  private static String soap12Envelope(String dn)
  {
    return "<env:Envelope xmlns:env=\"http://www.w3.org/2003/05/soap-envelope\">"
        + "<env:Body>" + batchRequest(dn) + "</env:Body></env:Envelope>";
  }

  /**
   * A batch request carrying the given text as an attribute value, so that an
   * expanded entity ends up in the text content of the SOAP body. The value is
   * prefixed with {@link #MARKER}, which tells a body that was parsed without
   * expanding the entity from a body that was never parsed at all.
   */
  private static String batchRequest(String value)
  {
    return "<batchRequest xmlns=\"urn:oasis:names:tc:DSML:2:0:core\">"
        + "<addRequest dn=\"cn=probe,dc=example,dc=com\">"
        + "<attr name=\"description\"><value>" + MARKER + value + "</value></attr>"
        + "</addRequest></batchRequest>";
  }
}
