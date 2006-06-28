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
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */
package org.opends.server.protocols.asn1;



import java.io.*;
import java.util.*;
import junit.framework.*;
import org.opends.server.*;

import static org.opends.server.util.StaticUtils.*;



/**
 * This class defines a set of JUnit tests for the
 * org.opends.server.protocols.asn1.ASN1Reader and
 * org.opends.server.protocols.asn1.ASN1Writer classes.
 *
 *
 * @author   Neil A. Wilson
 */
public class TestASN1ReaderAndWriter
       extends DirectoryServerTestCase
{
  // The set of ASN.1 Boolean elements that will be written and read.
  private ArrayList<ASN1Boolean> booleanElements;

  // The set of ASN.1 enumerated elements that will be written and read.
  private ArrayList<ASN1Enumerated> enumeratedElements;

  // The set of generic ASN.1 elements that will be written and read.
  private ArrayList<ASN1Element> genericElements;

  // The set of ASN.1 integer elements that will be written and read.
  private ArrayList<ASN1Integer> integerElements;

  // The set of ASN.1 null elements that will be written and read.
  private ArrayList<ASN1Null> nullElements;

  // The set of ASN.1 octet string elements that will be written and read.
  private ArrayList<ASN1OctetString> octetStringElements;

  // The set of ASN.1 sequence elements that will be written and read.
  private ArrayList<ASN1Sequence> sequenceElements;

  // The set of ASN.1 enumerated elements that will be written and read.
  private ArrayList<ASN1Set> setElements;

  // The data file to which data will be written and read back.
  private File dataFile;



  /**
   * Creates a new instance of this JUnit test case with the provided name.
   *
   * @param  name  The name to use for this JUnit test case.
   */
  public TestASN1ReaderAndWriter(String name)
  {
    super(name);
  }



  /**
   * Performs any necessary initialization for this test case.
   *
   * @throws  Exception  If a problem occurs during initialization.
   */
  public void setUp()
         throws Exception
  {
    // Create the temporary file that we will write to and read from.
    dataFile = File.createTempFile("TestASN1ReaderAndWriter-", ".data");


    // Create the set of generic elements that will be written and read.
    genericElements = new ArrayList<ASN1Element>();
    genericElements.add(new ASN1Element((byte) 0x00));
    genericElements.add(new ASN1Element((byte) 0x00, null));
    genericElements.add(new ASN1Element((byte) 0x00, new byte[0]));
    genericElements.add(new ASN1Element((byte) 0x00, new byte[1]));
    genericElements.add(new ASN1Element((byte) 0x00, new byte[127]));
    genericElements.add(new ASN1Element((byte) 0x00, new byte[128]));
    genericElements.add(new ASN1Element((byte) 0x00, new byte[255]));
    genericElements.add(new ASN1Element((byte) 0x00, new byte[256]));
    genericElements.add(new ASN1Element((byte) 0x00, new byte[32767]));
    genericElements.add(new ASN1Element((byte) 0x00, new byte[32768]));
    genericElements.add(new ASN1Element((byte) 0x00, new byte[65535]));
    genericElements.add(new ASN1Element((byte) 0x00, new byte[65536]));


    // Create the set of Boolean elements that will be written and read.
    booleanElements = new ArrayList<ASN1Boolean>();
    booleanElements.add(new ASN1Boolean(false));
    booleanElements.add(new ASN1Boolean(true));
    booleanElements.add(new ASN1Boolean((byte) 0x00, false));
    booleanElements.add(new ASN1Boolean((byte) 0x00, true));


    // Create the set of enumerated elements that will be written and read.
    enumeratedElements = new ArrayList<ASN1Enumerated>();
    enumeratedElements.add(new ASN1Enumerated(0));
    enumeratedElements.add(new ASN1Enumerated(1));
    enumeratedElements.add(new ASN1Enumerated(127));
    enumeratedElements.add(new ASN1Enumerated(128));
    enumeratedElements.add(new ASN1Enumerated(255));
    enumeratedElements.add(new ASN1Enumerated(256));
    enumeratedElements.add(new ASN1Enumerated(32767));
    enumeratedElements.add(new ASN1Enumerated(32768));
    enumeratedElements.add(new ASN1Enumerated(65535));
    enumeratedElements.add(new ASN1Enumerated(65536));


    // Create the set of integer elements that will be written and read.
    integerElements = new ArrayList<ASN1Integer>();
    integerElements.add(new ASN1Integer(0));
    integerElements.add(new ASN1Integer(1));
    integerElements.add(new ASN1Integer(127));
    integerElements.add(new ASN1Integer(128));
    integerElements.add(new ASN1Integer(255));
    integerElements.add(new ASN1Integer(256));
    integerElements.add(new ASN1Integer(32767));
    integerElements.add(new ASN1Integer(32768));
    integerElements.add(new ASN1Integer(65535));
    integerElements.add(new ASN1Integer(65536));


    // Create the set of null elements that will be written and read.
    nullElements = new ArrayList<ASN1Null>();
    nullElements.add(new ASN1Null());
    for (int i=0; i < 256; i++)
    {
      byte type = (byte) (i & 0xFF);
      nullElements.add(new ASN1Null(type));
    }


    // Create the set of octet string elements that will be written and read.
    octetStringElements = new ArrayList<ASN1OctetString>();
    octetStringElements.add(new ASN1OctetString());
    octetStringElements.add(new ASN1OctetString((byte[]) null));
    octetStringElements.add(new ASN1OctetString((String) null));
    octetStringElements.add(new ASN1OctetString(new byte[0]));
    octetStringElements.add(new ASN1OctetString(new byte[1]));
    octetStringElements.add(new ASN1OctetString(new byte[127]));
    octetStringElements.add(new ASN1OctetString(new byte[128]));
    octetStringElements.add(new ASN1OctetString(new byte[255]));
    octetStringElements.add(new ASN1OctetString(new byte[256]));
    octetStringElements.add(new ASN1OctetString(new byte[32767]));
    octetStringElements.add(new ASN1OctetString(new byte[32768]));
    octetStringElements.add(new ASN1OctetString(new byte[65535]));
    octetStringElements.add(new ASN1OctetString(new byte[65536]));
    octetStringElements.add(new ASN1OctetString(""));
    octetStringElements.add(new ASN1OctetString("a"));

    char[] chars127 = new char[127];
    Arrays.fill(chars127, 'a');
    octetStringElements.add(new ASN1OctetString(new String(chars127)));

    char[] chars128 = new char[128];
    Arrays.fill(chars128, 'a');
    octetStringElements.add(new ASN1OctetString(new String(chars128)));


    // Create the set of sequence elements that will be written and read.
    sequenceElements = new ArrayList<ASN1Sequence>();
    sequenceElements.add(new ASN1Sequence());
    sequenceElements.add(new ASN1Sequence(null));
    sequenceElements.add(new ASN1Sequence(new ArrayList<ASN1Element>(0)));
    sequenceElements.add(new ASN1Sequence(genericElements));
    sequenceElements.add(new ASN1Sequence(
                              new ArrayList<ASN1Element>(booleanElements)));
    sequenceElements.add(new ASN1Sequence(
                              new ArrayList<ASN1Element>(enumeratedElements)));
    sequenceElements.add(new ASN1Sequence(
                              new ArrayList<ASN1Element>(integerElements)));
    sequenceElements.add(new ASN1Sequence(
                              new ArrayList<ASN1Element>(nullElements)));
    sequenceElements.add(new ASN1Sequence(
                              new ArrayList<ASN1Element>(octetStringElements)));


    // Create the set of set elements that will be written and read.
    setElements = new ArrayList<ASN1Set>();
    setElements.add(new ASN1Set());
    setElements.add(new ASN1Set(null));
    setElements.add(new ASN1Set(new ArrayList<ASN1Element>(0)));
    setElements.add(new ASN1Set(genericElements));
    setElements.add(new ASN1Set(new ArrayList<ASN1Element>(booleanElements)));
    setElements.add(new ASN1Set(
                             new ArrayList<ASN1Element>(enumeratedElements)));
    setElements.add(new ASN1Set(new ArrayList<ASN1Element>(integerElements)));
    setElements.add(new ASN1Set(new ArrayList<ASN1Element>(nullElements)));
    setElements.add(new ASN1Set(
                             new ArrayList<ASN1Element>(octetStringElements)));
    setElements.add(new ASN1Set(new ArrayList<ASN1Element>(sequenceElements)));
  }



  /**
   * Performs any necessary cleanup for this test case.
   *
   * @throws  Exception  If a problem occurs during cleanup.
   */
  public void tearDown()
         throws Exception
  {
    // Delete the temporary data file.
    dataFile.delete();
  }



  /**
   * Tests the <CODE>ASN1Writer.writeElement</CODE> and the
   * <CODE>ASN1Reader.readElement</CODE> methods.
   */
  public void testWriteAndRead()
  {
    // Create the ASN.1 writer that will be used to write the elements.
    ASN1Writer asn1Writer;
    try
    {
     asn1Writer = new ASN1Writer(new FileOutputStream(dataFile, false));
    }
    catch (IOException ioe)
    {
      String message = "Unable to create output file " +
                       dataFile.getAbsolutePath() + " for writing";
      printError(message);
      printException(ioe);
      throw new AssertionFailedError(message + " -- " +
                                     stackTraceToSingleLineString(ioe));
    }


    // Write the set of generic elements.
    for (ASN1Element element : genericElements)
    {
      try
      {
        asn1Writer.writeElement(element);
      }
      catch (IOException ioe)
      {
        String message = "I/O exception attempting to write generic ASN.1 " +
                         "element " + String.valueOf(element);
        printError(message);
        printException(ioe);
        throw new AssertionFailedError(message + " -- " +
                                       stackTraceToSingleLineString(ioe));
      }
    }

    // Write the set of Boolean elements.
    for (ASN1Boolean element : booleanElements)
    {
      try
      {
        asn1Writer.writeElement(element);
      }
      catch (IOException ioe)
      {
        String message = "I/O exception attempting to write ASN.1 Boolean " +
                         "element " + String.valueOf(element);
        printError(message);
        printException(ioe);
        throw new AssertionFailedError(message + " -- " +
                                       stackTraceToSingleLineString(ioe));
      }
    }

    // Write the set of enumerated elements.
    for (ASN1Enumerated element : enumeratedElements)
    {
      try
      {
        asn1Writer.writeElement(element);
      }
      catch (IOException ioe)
      {
        String message = "I/O exception attempting to write ASN.1 enumerated " +
                         "element " + String.valueOf(element);
        printError(message);
        printException(ioe);
        throw new AssertionFailedError(message + " -- " +
                                       stackTraceToSingleLineString(ioe));
      }
    }

    // Write the set of integer elements.
    for (ASN1Integer element : integerElements)
    {
      try
      {
        asn1Writer.writeElement(element);
      }
      catch (IOException ioe)
      {
        String message = "I/O exception attempting to write ASN.1 integer " +
                         "element " + String.valueOf(element);
        printError(message);
        printException(ioe);
        throw new AssertionFailedError(message + " -- " +
                                       stackTraceToSingleLineString(ioe));
      }
    }

    // Write the set of null elements.
    for (ASN1Null element : nullElements)
    {
      try
      {
        asn1Writer.writeElement(element);
      }
      catch (IOException ioe)
      {
        String message = "I/O exception attempting to write ASN.1 null " +
                         "element " + String.valueOf(element);
        printError(message);
        printException(ioe);
        throw new AssertionFailedError(message + " -- " +
                                       stackTraceToSingleLineString(ioe));
      }
    }

    // Write the set of octet string elements.
    for (ASN1OctetString element : octetStringElements)
    {
      try
      {
        asn1Writer.writeElement(element);
      }
      catch (IOException ioe)
      {
        String message = "I/O exception attempting to write ASN.1 octet " +
                         "string element " + String.valueOf(element);
        printError(message);
        printException(ioe);
        throw new AssertionFailedError(message + " -- " +
                                       stackTraceToSingleLineString(ioe));
      }
    }

    // Write the set of sequence elements.
    for (ASN1Sequence element : sequenceElements)
    {
      try
      {
        asn1Writer.writeElement(element);
      }
      catch (IOException ioe)
      {
        String message = "I/O exception attempting to write ASN.1 sequence " +
                         "element " + String.valueOf(element);
        printError(message);
        printException(ioe);
        throw new AssertionFailedError(message + " -- " +
                                       stackTraceToSingleLineString(ioe));
      }
    }

    // Write the set of set elements.
    for (ASN1Set element : setElements)
    {
      try
      {
        asn1Writer.writeElement(element);
      }
      catch (IOException ioe)
      {
        String message = "I/O exception attempting to write ASN.1 set " +
                         "element " + String.valueOf(element);
        printError(message);
        printException(ioe);
        throw new AssertionFailedError(message + " -- " +
                                       stackTraceToSingleLineString(ioe));
      }
    }


    // Always remember to close the output file.
    asn1Writer.close();


    // Create the ASN.1 reader that will be used to read the elements back.
    ASN1Reader asn1Reader;
    try
    {
      asn1Reader = new ASN1Reader(new FileInputStream(dataFile));
    }
    catch (IOException ioe)
    {
      String message = "Unable to open data file " +
                       dataFile.getAbsolutePath() + " for reading";
      printError(message);
      printException(ioe);
      throw new AssertionFailedError(message + " -- " +
                                     stackTraceToSingleLineString(ioe));
    }


    // Read back the set of generic elements.
    for (ASN1Element element : genericElements)
    {
      try
      {
        assertEquals(element, asn1Reader.readElement());
      }
      catch (IOException ioe)
      {
        String message = "I/O exception attempting to read generic element " +
                         String.valueOf(element);
        printError(message);
        printException(ioe);
        throw new AssertionFailedError(message + " -- " +
                                       stackTraceToSingleLineString(ioe));
      }
      catch (ASN1Exception ae)
      {
        String message = "ASN.1 exception attempting to decode generic " +
                         "element " + String.valueOf(element);
        printError(message);
        printException(ae);
        throw new AssertionFailedError(message + " -- " +
                                       stackTraceToSingleLineString(ae));
      }
      catch (AssertionFailedError afe)
      {
        printError("readElement failed to read generic element equal to " +
                   String.valueOf(element));
        throw afe;
      }
    }

    // Read back the set of Boolean elements.
    for (ASN1Boolean element : booleanElements)
    {
      try
      {
        assertEquals(element.booleanValue(),
                     asn1Reader.readElement().decodeAsBoolean().booleanValue());
      }
      catch (IOException ioe)
      {
        String message = "I/O exception attempting to read Boolean element " +
                         String.valueOf(element);
        printError(message);
        printException(ioe);
        throw new AssertionFailedError(message + " -- " +
                                       stackTraceToSingleLineString(ioe));
      }
      catch (ASN1Exception ae)
      {
        String message = "ASN.1 exception attempting to decode Boolean " +
                         "element " + String.valueOf(element);
        printError(message);
        printException(ae);
        throw new AssertionFailedError(message + " -- " +
                                       stackTraceToSingleLineString(ae));
      }
      catch (AssertionFailedError afe)
      {
        printError("readElement failed to read Boolean element equal to " +
                   String.valueOf(element));
        throw afe;
      }
    }

    // Read back the set of enumerated elements.
    for (ASN1Enumerated element : enumeratedElements)
    {
      try
      {
        assertEquals(element.intValue(),
                     asn1Reader.readElement().decodeAsEnumerated().intValue());
      }
      catch (IOException ioe)
      {
        String message = "I/O exception attempting to read enumerated " +
                         "element " + String.valueOf(element);
        printError(message);
        printException(ioe);
        throw new AssertionFailedError(message + " -- " +
                                       stackTraceToSingleLineString(ioe));
      }
      catch (ASN1Exception ae)
      {
        String message = "ASN.1 exception attempting to decode enumerated " +
                         "element " + String.valueOf(element);
        printError(message);
        printException(ae);
        throw new AssertionFailedError(message + " -- " +
                                       stackTraceToSingleLineString(ae));
      }
      catch (AssertionFailedError afe)
      {
        printError("readElement failed to read enumerated element equal to " +
                   String.valueOf(element));
        throw afe;
      }
    }

    // Read back the set of integer elements.
    for (ASN1Integer element : integerElements)
    {
      try
      {
        assertEquals(element.intValue(),
                     asn1Reader.readElement().decodeAsInteger().intValue());
      }
      catch (IOException ioe)
      {
        String message = "I/O exception attempting to read integer element " +
                         String.valueOf(element);
        printError(message);
        printException(ioe);
        throw new AssertionFailedError(message + " -- " +
                                       stackTraceToSingleLineString(ioe));
      }
      catch (ASN1Exception ae)
      {
        String message = "ASN.1 exception attempting to decode integer " +
                         "element " + String.valueOf(element);
        printError(message);
        printException(ae);
        throw new AssertionFailedError(message + " -- " +
                                       stackTraceToSingleLineString(ae));
      }
      catch (AssertionFailedError afe)
      {
        printError("readElement failed to read integer element equal to " +
                   String.valueOf(element));
        throw afe;
      }
    }

    // Read back the set of null elements.
    for (ASN1Null element : nullElements)
    {
      try
      {
        assertEquals(element, asn1Reader.readElement().decodeAsNull());
      }
      catch (IOException ioe)
      {
        String message = "I/O exception attempting to read null element " +
                         String.valueOf(element);
        printError(message);
        printException(ioe);
        throw new AssertionFailedError(message + " -- " +
                                       stackTraceToSingleLineString(ioe));
      }
      catch (ASN1Exception ae)
      {
        String message = "ASN.1 exception attempting to decode null " +
                         "element " + String.valueOf(element);
        printError(message);
        printException(ae);
        throw new AssertionFailedError(message + " -- " +
                                       stackTraceToSingleLineString(ae));
      }
      catch (AssertionFailedError afe)
      {
        printError("readElement failed to read null element equal to " +
                   String.valueOf(element));
        throw afe;
      }
    }

    // Read back the set of octet string elements.
    for (ASN1OctetString element : octetStringElements)
    {
      try
      {
        assertEquals(element, asn1Reader.readElement().decodeAsOctetString());
      }
      catch (IOException ioe)
      {
        String message = "I/O exception attempting to read octet string " +
                         "element " + String.valueOf(element);
        printError(message);
        printException(ioe);
        throw new AssertionFailedError(message + " -- " +
                                       stackTraceToSingleLineString(ioe));
      }
      catch (ASN1Exception ae)
      {
        String message = "ASN.1 exception attempting to decode octet string " +
                         "element " + String.valueOf(element);
        printError(message);
        printException(ae);
        throw new AssertionFailedError(message + " -- " +
                                       stackTraceToSingleLineString(ae));
      }
      catch (AssertionFailedError afe)
      {
        printError("readElement failed to read octet string element equal to " +
                   String.valueOf(element));
        throw afe;
      }
    }

    // Read back the set of sequence elements.
    for (ASN1Sequence element : sequenceElements)
    {
      try
      {
        assertTrue(listsAreEqual(element.elements(),
             asn1Reader.readElement().decodeAsSequence().elements()));
      }
      catch (IOException ioe)
      {
        String message = "I/O exception attempting to read sequence element " +
                         String.valueOf(element);
        printError(message);
        printException(ioe);
        throw new AssertionFailedError(message + " -- " +
                                       stackTraceToSingleLineString(ioe));
      }
      catch (ASN1Exception ae)
      {
        String message = "ASN.1 exception attempting to decode sequence " +
                         "element " + String.valueOf(element);
        printError(message);
        printException(ae);
        throw new AssertionFailedError(message + " -- " +
                                       stackTraceToSingleLineString(ae));
      }
      catch (AssertionFailedError afe)
      {
        printError("readElement failed to read sequence element equal to " +
                   String.valueOf(element));
        throw afe;
      }
    }

    // Read back the set of set elements.
    for (ASN1Set element : setElements)
    {
      try
      {
        assertTrue(listsAreEqual(element.elements(),
                        asn1Reader.readElement().decodeAsSet().elements()));
      }
      catch (IOException ioe)
      {
        String message = "I/O exception attempting to read set element " +
                         String.valueOf(element);
        printError(message);
        printException(ioe);
        throw new AssertionFailedError(message + " -- " +
                                       stackTraceToSingleLineString(ioe));
      }
      catch (ASN1Exception ae)
      {
        String message = "ASN.1 exception attempting to decode set " +
                         "element " + String.valueOf(element);
        printError(message);
        printException(ae);
        throw new AssertionFailedError(message + " -- " +
                                       stackTraceToSingleLineString(ae));
      }
      catch (AssertionFailedError afe)
      {
        printError("readElement failed to read set element equal to " +
                   String.valueOf(element));
        throw afe;
      }
    }


    // Always remember to close the input file.
    asn1Reader.close();
  }
}

