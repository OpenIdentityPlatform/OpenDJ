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
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

import javax.xml.XMLConstants;

import org.forgerock.testng.ForgeRockTestCase;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import jakarta.xml.bind.JAXBElement;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Tests the XML parsing the gateway configures itself, as opposed to the
 * parser SAAJ builds internally (which {@link SoapEntityExpansionTestCase}
 * covers).
 * <p>
 * {@code DSMLServlet.createSafeXMLReader()} hardens its reader through
 * {@code safeSetFeature}, which logs and carries on when a feature is not
 * supported by the parser in use - so a parser change, or a feature name
 * change, would disarm it without failing anything. That reader parses
 * untrusted input: when SAAJ refuses a request, the gateway hands the very same
 * body to it a second time to recover the requestID
 * ({@code createXMLParsingErrorResponse}).
 * <p>
 * The tests only ever reference {@code file:} URIs under a temporary
 * directory, so they perform no network or DNS I/O.
 */
@SuppressWarnings("javadoc")
@Test(groups = { "precommit", "dsml" })
public class DSMLXmlParsingTestCase extends ForgeRockTestCase
{
  private static final String CANARY = "s3cr3t-canary-value";
  private static final String DISALLOW_DOCTYPE = "http://apache.org/xml/features/disallow-doctype-decl";
  private static final String EXTERNAL_GENERAL_ENTITIES = "http://xml.org/sax/features/external-general-entities";
  private static final String EXTERNAL_PARAMETER_ENTITIES = "http://xml.org/sax/features/external-parameter-entities";

  private Path tempDir;
  private String canaryUri;
  private String externalDtdUri;

  @BeforeClass
  public void createPayloadFiles() throws IOException
  {
    tempDir = Files.createTempDirectory("dsml-parser-test");
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

  /**
   * The features the reader is hardened with must actually be in effect, not
   * merely have been requested.
   */
  @Test
  public void testSafeXmlReaderIsHardened() throws Exception
  {
    XMLReader xmlReader = new DSMLServlet().createSafeXMLReader();

    assertTrue(xmlReader.getFeature(XMLConstants.FEATURE_SECURE_PROCESSING),
        "secure processing is not in effect");
    assertTrue(xmlReader.getFeature(DISALLOW_DOCTYPE),
        "a DOCTYPE declaration would be accepted");
    assertFalse(xmlReader.getFeature(EXTERNAL_GENERAL_ENTITIES),
        "external general entities would be resolved");
    assertFalse(xmlReader.getFeature(EXTERNAL_PARAMETER_ENTITIES),
        "external parameter entities would be resolved");
  }

  @DataProvider
  public Object[][] entityPayloads()
  {
    return new Object[][] {
      { "external general entity",
        "<?xml version=\"1.0\"?>"
        + "<!DOCTYPE batchRequest [<!ENTITY xxe SYSTEM \"" + canaryUri + "\">]>"
        + batchRequest("&xxe;") },
      { "external DTD subset",
        "<?xml version=\"1.0\"?>"
        + "<!DOCTYPE batchRequest SYSTEM \"" + externalDtdUri + "\">"
        + batchRequest("&xxe;") },
    };
  }

  /**
   * A DOCTYPE is refused outright; should a future parser accept one, the file
   * contents must still not reach the parsed document.
   */
  @Test(dataProvider = "entityPayloads")
  public void testSafeXmlReaderNeverExpandsAnExternalEntity(String label, String request) throws Exception
  {
    XMLReader xmlReader = new DSMLServlet().createSafeXMLReader();
    TextCollector collector = new TextCollector();
    xmlReader.setContentHandler(collector);
    try
    {
      xmlReader.parse(new InputSource(asStream(request)));
    }
    catch (SAXException e)
    {
      // Refused, which is what disallow-doctype-decl is there for.
      return;
    }
    assertFalse(collector.text.toString().contains(CANARY),
        label + ": the external entity was expanded: " + collector.text);
  }

  /** A well-formed request must still parse, otherwise the test above is empty. */
  @Test
  public void testSafeXmlReaderParsesAPlainRequest() throws Exception
  {
    XMLReader xmlReader = new DSMLServlet().createSafeXMLReader();
    TextCollector collector = new TextCollector();
    xmlReader.setContentHandler(collector);
    xmlReader.parse(new InputSource(asStream(batchRequest("a plain attribute value"))));

    assertEquals(collector.requestID, "42");
    assertTrue(collector.text.toString().contains("a plain attribute value"),
        "unexpected content: " + collector.text);
  }

  /**
   * The recovery path the servlet takes when SAAJ has refused the request: the
   * body is parsed a second time, and that second parse must go through the
   * hardened reader rather than reintroduce the entity expansion.
   */
  @Test(dataProvider = "entityPayloads")
  public void testXmlParsingErrorResponseDoesNotExpandEntities(String label, String request) throws Exception
  {
    ObjectFactory objFactory = new ObjectFactory();
    BatchResponse batchResponse = objFactory.createBatchResponse();
    InputStream is = asStream(request);

    JAXBElement<ErrorResponse> response = new DSMLServlet()
        .createXMLParsingErrorResponse(is, objFactory, batchResponse, "parser error");

    assertFalse(String.valueOf(response.getValue().getMessage()).contains(CANARY),
        label + ": the error response carries the file contents");
    assertFalse(String.valueOf(batchResponse.getRequestID()).contains(CANARY),
        label + ": the requestID carries the file contents");
  }

  /** The same path, on a request the parser accepts: the requestID is recovered. */
  @Test
  public void testXmlParsingErrorResponseRecoversTheRequestID() throws Exception
  {
    ObjectFactory objFactory = new ObjectFactory();
    BatchResponse batchResponse = objFactory.createBatchResponse();

    JAXBElement<ErrorResponse> response = new DSMLServlet().createXMLParsingErrorResponse(
        asStream(batchRequest("a plain attribute value")), objFactory, batchResponse, "parser error");

    assertEquals(batchResponse.getRequestID(), "42");
    assertEquals(response.getValue().getMessage(), "parser error");
  }

  /** {@code createXMLParsingErrorResponse} rewinds the stream, so it must support it. */
  private InputStream asStream(String request)
  {
    return new ByteArrayInputStream(request.getBytes(StandardCharsets.UTF_8));
  }

  private static String batchRequest(String value)
  {
    return "<batchRequest xmlns=\"urn:oasis:names:tc:DSML:2:0:core\" requestID=\"42\">"
        + "<addRequest dn=\"cn=probe,dc=example,dc=com\">"
        + "<attr name=\"description\"><value>" + value + "</value></attr>"
        + "</addRequest></batchRequest>";
  }

  /** Collects the character data of a parsed document, and the requestID. */
  private static final class TextCollector extends DefaultHandler
  {
    private final StringBuilder text = new StringBuilder();
    private String requestID;

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes)
    {
      if (requestID == null && "batchRequest".equals(localName))
      {
        requestID = attributes.getValue("requestID");
      }
    }

    @Override
    public void characters(char[] ch, int start, int length)
    {
      text.append(ch, start, length);
    }
  }
}
