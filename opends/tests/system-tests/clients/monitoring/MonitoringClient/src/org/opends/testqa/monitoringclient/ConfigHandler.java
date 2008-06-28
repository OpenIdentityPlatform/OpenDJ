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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */

package org.opends.testqa.monitoringclient;

import java.util.Hashtable;
import java.util.Properties;

import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Parse a XML config file.
 */
public class ConfigHandler extends DefaultHandler {

  /**
   * Main class of the client.
   */
  private MonitoringClient client;

  private Properties parsedArguments;

  /**
   * The parsed properties.
   */
  private Hashtable<String,Properties> config;

  /**
   * The locator used for display informations about the line and the column
   * number of the error.
   */
  private Locator locator;

  /**
   *  The name of the protocol markup.
   */
  private String inProtocol;

  /**
   * Indicate if a attribute markup have already been open.
   */
  private boolean inAttribute;

  /**
   * The constructor of the handler.
   *
   * @param client  The main class of the client
   * @param parsedArguments  The parsed arguments
   */
  public ConfigHandler(MonitoringClient client, Properties parsedArguments) {
    super();
    this.client = client;
    this.parsedArguments = parsedArguments;
    this.config = new Hashtable<String,Properties>();
    inProtocol = "";
    inAttribute = false;
  }

  /**
   * Set the document locator to display information about the line or column
   * number of the error.
   *
   * @param locator The locator used for display informations about the line and
   * the column number of the error.
   */
  @Override
  public void setDocumentLocator (Locator locator) {
    this.locator = locator;
  }

  /**
   * If an element is open, set the parameters of the client or add an attribute
   * to monitor.
   *
   * @param uri         The Namespace URI, or the empty string if the element
   * has no Namespace URI or if Namespace processing is not being performed.
   * @param localName   The local name (without prefix), or the empty string if
   * Namespace processing is not being performed.
   * @param qName       The qualified name (with prefix), or the empty string if
   * qualified names are not available.
   * @param attributes  The attributes attached to the element. If there are no
   * attributes, it shall be an empty Attributes object.
   * @throws org.xml.sax.SAXException Any SAX exception, possibly wrapping
   * another exception.
   */
  @Override
  public void startElement(String uri, String localName, String qName,
          Attributes attributes) throws SAXException {
    Properties params = new Properties();
    if (qName.equals("protocol")) {
      for (int i=0; i<attributes.getLength(); i++) {
        params.setProperty(attributes.getQName(i), attributes.getValue(i));
      }
      config.put(attributes.getValue("name"),params);
      inProtocol = attributes.getValue("name");

    } else if (!inProtocol.equals("") && qName.equals("attribute")) {

      if ((inProtocol.equals("LDAP") &&
              attributes.getValue("name") == null &&
              attributes.getValue("baseDN") == null) ||

              (inProtocol.equals("JMX") &&
              attributes.getValue("name") == null &&
              attributes.getValue("MBeanName") == null) ||

              (inProtocol.equals("JVM") &&
              attributes.getValue("name") == null &&
              attributes.getValue("MBeanName") == null) ||

              (inProtocol.equals("SNMP") &&
              attributes.getValue("oid") == null)) {
        this.error(new SAXParseException("Incorrect attributes for the balise "
                + qName, locator));
      }

      params.setProperty("protocol", inProtocol);
      for (int i=0; i<attributes.getLength(); i++) {
        params.setProperty(attributes.getQName(i),
                attributes.getValue(i).replace(
                "${port}", parsedArguments.getProperty("LDAPport")));
      }
      if (attributes.getValue("name") != null) {
        client.getDatasBuffer().addAttributeToMonitor(attributes.getValue(
                "name"), params);
      } else {
        client.getDatasBuffer().addAttributeToMonitor(attributes.getValue(
                "oid"), params);
      }
      inAttribute = true;

    } else if ( !qName.equals("config")) {
       this.error(new SAXParseException("Unknown balise " + qName, locator));
    }
  }

  /**
   * Verify the syntax of the XML file.
   *
   * @param uri       The Namespace URI, or the empty string if the element has
   * no Namespace URI or if Namespace processing is not being performed.
   * @param localName The local name (without prefix), or the empty string if
   * Namespace processing is not being performed.
   * @param qName     The qualified name (with prefix), or the empty string if
   * qualified names are not available.
   * @throws org.xml.sax.SAXException Any SAX exception, possibly wrapping
   * another exception.
   */
  @Override
  public void endElement (String uri, String localName, String qName)
          throws SAXException {
    if (qName.equals("protocol") && !inProtocol.equals("") && !inAttribute) {
      inProtocol = "";
    } else if (qName.equals("protocol") &&
            (inProtocol.equals("") || inAttribute)) {
       this.error(new SAXParseException("Incorrect end balise " + qName,
               locator));
    } else if (qName.equals("attribute")) {
      inAttribute = false;
    } else if ( !qName.equals("config")) {
       this.error(new SAXParseException("Unknown balise " + qName, locator));
    }
  }

  /**
   * At the end of the file, set the configuration of the client.
   *
   * @throws org.xml.sax.SAXException Any SAX exception, possibly wrapping
   * another exception.
   */
  @Override
  public void endDocument() throws SAXException {
    client.setProducersConfig(config);
  }

  /**
   * Display a warning.
   *
   * @param e Any SAX exception, possibly wrapping another exception.
   * @throws org.xml.sax.SAXException Any SAX exception, possibly wrapping
   * another exception.
   */
  @Override
  public void warning(SAXParseException e) throws SAXException {
    System.out.println("Warning: ");
    printInfo(e);
  }

  /**
   * Display an error an exit the application.
   *
   * @param e The warning information encoded as an exception.
   * @throws org.xml.sax.SAXException Any SAX exception, possibly wrapping
   * another exception.
   */
  @Override
  public void error(SAXParseException e) throws SAXException {
    System.out.println("Error: ");
    printInfo(e);
    System.exit(1);
  }

  /**
   * Display a fatal error an exit the application.
   *
   * @param e The warning information encoded as an exception.
   * @throws org.xml.sax.SAXException Any SAX exception, possibly wrapping
   * another exception.
   */
  @Override
  public void fatalError(SAXParseException e) throws SAXException {
    System.out.println("Fatal error: ");
    printInfo(e);
    System.exit(1);
  }

  /**
   * Display the errors infos.
   * @param e The error to display
   */
  private void printInfo(SAXParseException e) {
    System.out.println("   Public ID: " + e.getPublicId());
    System.out.println("   System ID: " + e.getSystemId());
    System.out.println("   Line number: " + e.getLineNumber());
    System.out.println("   Column number: " + e.getColumnNumber());
    System.out.println("   Message: " + e.getMessage());
  }

}
