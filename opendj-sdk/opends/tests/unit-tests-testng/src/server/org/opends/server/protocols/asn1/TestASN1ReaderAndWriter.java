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
package org.opends.server.protocols.asn1;



import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.*;
import org.opends.messages.Message;


/**
 * This class defines a set of tests for the
 * org.opends.server.protocols.asn1.ASN1Reader and
 * org.opends.server.protocols.asn1.ASN1Writer classes.
 */
public class TestASN1ReaderAndWriter
       extends ASN1TestCase
{
  /**
   * Create byte arrays with encoded ASN.1 elements to test decoding them as
   * octet strings.
   *
   * @return  A list of byte arrays with encoded ASN.1 elements that can be
   *          decoded as octet strings.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @DataProvider(name = "elementArrays")
  public Object[][] getElementArrays()
  {
    return new Object[][]
    {
      new Object[] { new byte[] { 0x04, 0x00 } },
      new Object[] { new byte[] { (byte) 0x50, 0x00 } },
      new Object[] { new byte[] { 0x04, 0x05, 0x48, 0x65, 0x6C, 0x6C, 0x6F } },
      new Object[] { new byte[] { 0x01, 0x01, 0x00 } },
      new Object[] { new byte[] { 0x01, 0x01, (byte) 0xFF } },
      new Object[] { new byte[] { 0x0A, 0x01, 0x00 } },
      new Object[] { new byte[] { 0x0A, 0x01, 0x01 } },
      new Object[] { new byte[] { 0x0A, 0x01, 0x7F } },
      new Object[] { new byte[] { 0x0A, 0x01, (byte) 0x80 } },
      new Object[] { new byte[] { 0x0A, 0x01, (byte) 0xFF } },
      new Object[] { new byte[] { 0x0A, 0x02, 0x01, 0x00 } },
      new Object[] { new byte[] { 0x02, 0x01, 0x00 } },
      new Object[] { new byte[] { 0x02, 0x01, 0x01 } },
      new Object[] { new byte[] { 0x02, 0x01, 0x7F } },
      new Object[] { new byte[] { 0x02, 0x02, 0x00, (byte) 0x80 } },
      new Object[] { new byte[] { 0x02, 0x02, 0x00, (byte) 0xFF } },
      new Object[] { new byte[] { 0x02, 0x02, 0x01, 0x00 } },
      new Object[] { new byte[] { 0x05, 0x00 } },
      new Object[] { new byte[] { 0x30, 0x00 } },
      new Object[] { new byte[] { 0x31, 0x00 } },
    };
  }



  /**
   * Tests writing elements to an output stream.
   *
   * @param  elementBytes  The byte array that makes up an encoded element.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "elementArrays")
  public void testWriteToStream(byte[] elementBytes)
         throws Exception
  {
    ASN1Element e = ASN1Element.decode(elementBytes);

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ASN1Writer writer = new ASN1Writer(baos);
    writer.writeElement(e);

    assertEquals(baos.toByteArray(), elementBytes);
    writer.close();
  }



  /**
   * Tests writing elements to a socket.
   *
   * @param  elementBytes  The byte array that makes up an encoded element.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "elementArrays")
  public void testWriteToSocket(byte[] elementBytes)
         throws Exception
  {
    ASN1Element e = ASN1Element.decode(elementBytes);

    SocketReadThread readThread = new SocketReadThread("testWriteToSocket");
    readThread.start();

    Socket s = new Socket("127.0.0.1", readThread.getListenPort());
    ASN1Writer writer = new ASN1Writer(s);
    int bytesWritten = writer.writeElement(e);

    assertEquals(readThread.getDataRead(bytesWritten), elementBytes);
    writer.close();
    readThread.close();
  }



  /**
   * Tests reading elements from an input stream.
   *
   * @param  elementBytes  The byte array that makes up an encoded element.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "elementArrays")
  public void testReadFromStream(byte[] elementBytes)
         throws Exception
  {
    ByteArrayInputStream bais = new ByteArrayInputStream(elementBytes);
    ASN1Reader reader = new ASN1Reader(bais);

    reader.setIOTimeout(30000);
    assertEquals(reader.getIOTimeout(), -1);

    ASN1Element e = reader.readElement();
    assertEquals(e.encode(), elementBytes);
    assertEquals(ASN1Element.decode(elementBytes), e);

    assertNull(reader.readElement());
    reader.close();
  }



  /**
   * Tests reading elements from a socket.
   *
   * @param  elementBytes  The byte array that makes up an encoded element.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "elementArrays")
  public void testReadFromSocket(byte[] elementBytes)
         throws Exception
  {
    SocketWriteThread writeThread  = null;
    Socket            socket       = null;
    ServerSocket      serverSocket = null;

    try
    {
      ASN1Element element = ASN1Element.decode(elementBytes);

      serverSocket = new ServerSocket();
      serverSocket.setReuseAddress(true);
      serverSocket.bind(new InetSocketAddress("127.0.0.1", 0));

      writeThread = new SocketWriteThread("testReadFromSocket",
                                          serverSocket.getLocalPort(),
                                          elementBytes);
      writeThread.start();

      socket = serverSocket.accept();
      ASN1Reader reader = new ASN1Reader(socket);
      reader.setIOTimeout(30000);
      assertEquals(reader.getIOTimeout(), 30000);

      ASN1Element element2 = reader.readElement();

      assertEquals(element2, element);
      assertEquals(element2.encode(), elementBytes);
    }
    finally
    {
      try
      {
        writeThread.close();
      } catch (Exception e) {}

      try
      {
        socket.close();
      } catch (Exception e) {}

      try
      {
        serverSocket.close();
      } catch (Exception e) {}
    }
  }



  /**
   * Tests reading elements from an input stream with all elements falling below
   * the maximum element size.
   *
   * @param  elementBytes  The byte array that makes up an encoded element.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "elementArrays")
  public void testReadSuccessWithMaxElementSize(byte[] elementBytes)
         throws Exception
  {
    ByteArrayInputStream bais = new ByteArrayInputStream(elementBytes);
    ASN1Reader reader = new ASN1Reader(bais);

    reader.setMaxElementSize(elementBytes.length);
    assertEquals(reader.getMaxElementSize(), elementBytes.length);

    ASN1Element e = reader.readElement();
    assertEquals(e.encode(), elementBytes);
    assertEquals(ASN1Element.decode(elementBytes), e);

    assertNull(reader.readElement());
    reader.close();
  }



  /**
   * Tests reading elements from an input stream with all elements falling above
   * the maximum element size.
   *
   * @param  elementBytes  The byte array that makes up an encoded element.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "elementArrays",
        expectedExceptions = { ASN1Exception.class, IOException.class })
  public void testReadFailureWithMaxElementSize(byte[] elementBytes)
         throws Exception
  {
    ByteArrayInputStream bais = new ByteArrayInputStream(elementBytes);
    ASN1Reader reader = new ASN1Reader(bais);

    reader.setMaxElementSize(1);
    assertEquals(reader.getMaxElementSize(), 1);

    try
    {
      ASN1Element e = reader.readElement();
      if (e.value().length <= 1)
      {
        throw new ASN1Exception(Message.raw("Too small to trip the max element size"));
      }
    }
    finally
    {
      reader.close();
    }
  }



  /**
   * Tests to ensure that attempting to read an element with a length encoded in
   * too many bytes will fail.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { ASN1Exception.class, IOException.class })
  public void testReadFailureLongLength()
         throws Exception
  {
    byte[] elementBytes = { 0x04, (byte) 0x85, 0x00, 0x00, 0x00, 0x00, 0x00 };
    ByteArrayInputStream bais = new ByteArrayInputStream(elementBytes);
    ASN1Reader reader = new ASN1Reader(bais);

    try
    {
      ASN1Element e = reader.readElement();
    }
    finally
    {
      reader.close();
    }
  }



  /**
   * Tests to ensure that attempting to read an element with a truncated length
   * will fail.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { ASN1Exception.class, IOException.class })
  public void testReadFailureTruncatedLength()
         throws Exception
  {
    byte[] elementBytes = { 0x04, (byte) 0x82, 0x00 };
    ByteArrayInputStream bais = new ByteArrayInputStream(elementBytes);
    ASN1Reader reader = new ASN1Reader(bais);

    try
    {
      ASN1Element e = reader.readElement();
    }
    finally
    {
      reader.close();
    }
  }



  /**
   * Tests to ensure that attempting to read an element with a truncated value
   * will fail.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(expectedExceptions = { ASN1Exception.class, IOException.class })
  public void testReadFailureTruncatedValue()
         throws Exception
  {
    byte[] elementBytes = { 0x04, 0x02, 0x00 };
    ByteArrayInputStream bais = new ByteArrayInputStream(elementBytes);
    ASN1Reader reader = new ASN1Reader(bais);

    try
    {
      ASN1Element e = reader.readElement();
    }
    finally
    {
      reader.close();
    }
  }
}

