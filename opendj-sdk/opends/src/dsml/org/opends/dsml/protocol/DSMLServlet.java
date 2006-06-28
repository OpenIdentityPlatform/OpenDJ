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
package org.opends.dsml.protocol;



import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.StringTokenizer;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.soap.MessageFactory;
import javax.xml.soap.MimeHeaders;
import javax.xml.soap.Name;
import javax.xml.soap.SOAPBody;
import javax.xml.soap.SOAPConstants;
import javax.xml.soap.SOAPElement;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPFactory;
import javax.xml.soap.SOAPFault;
import javax.xml.soap.SOAPHeader;
import javax.xml.soap.SOAPMessage;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import org.opends.server.core.DirectoryServer;
import org.opends.server.tools.LDAPConnection;
import org.opends.server.tools.LDAPConnectionOptions;
import org.opends.server.util.Base64;



/**
 * This class provides the entry point for the DSML request.
 * It parses the SOAP request, calls the appropriate class
 * which performs the LDAP operation, and returns the response
 * as a DSML response.
 */
public class DSMLServlet extends HttpServlet
{
  private static final String PKG_NAME = "org.opends.dsml.protocol";
  private static final String SERVER_PROP_RESOURCE = "/server.properties";
  private static final String PORT = "port";
  private static final String HOST = "host";
  private static final long serialVersionUID = -3748022009593442973L;

  private Unmarshaller unmarshaller;
  private Marshaller marshaller;
  private ObjectFactory objFactory;
  private MessageFactory messageFactory;
  private DocumentBuilder db;

  private String hostName;
  private int port = 389;

  /**
   * This method will be called by the Servlet Container when
   * this servlet is being placed into service.
   * @param config - the <CODE>ServletConfig</CODE> object that
   * contains configutation information for this servlet.
   *
   * @throws  ServletException  If an error occurs during processing.
   */
  public void init(ServletConfig config) throws ServletException
  {
    System.out.println("DSMLServlet: init()");

    try
    {
      URL myURL=config.getServletContext().getResource(SERVER_PROP_RESOURCE);
      InputStream in = myURL.openStream();
      Properties p = new Properties();
      p.load( in );
      // System.out.println( p.getProperty(HOST) );
      // System.out.println( p.getProperty(PORT) );
      hostName = p.getProperty(HOST);
      if(hostName == null)
      {
        hostName = "localhost";
      }

      String portStr = p.getProperty(PORT);
      port = Integer.parseInt(portStr);

      JAXBContext jaxbContext = JAXBContext.newInstance(PKG_NAME);
      unmarshaller = jaxbContext.createUnmarshaller();

      marshaller = jaxbContext.createMarshaller();
      marshaller.setProperty("com.sun.xml.bind.namespacePrefixMapper",
                             new NamespacePrefixMapperImpl());

      objFactory = new ObjectFactory();
      messageFactory = MessageFactory.newInstance();
      DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
      dbf.setNamespaceAware(true);
      db = dbf.newDocumentBuilder();

      DirectoryServer.bootstrapClient();

    } catch(Exception je)
    {
      je.printStackTrace();
      throw new ServletException(je.getMessage());
    }
  }


  /**
   * The HTTP GET operation.
   *
   * @param  req  Information about the request received from the client.
   * @param  res  Information about the response to send to the client.
   *
   * @throws  ServletException  If an error occurs during servlet processing.
   *
   * @throws  IOException  If an error occurs while interacting with the client.
   */
  public void doGet(HttpServletRequest req, HttpServletResponse res)
  throws ServletException, IOException
  {
    super.doGet(req, res);
  }


  /**
   * The HTTP POST operation. This servlet expects a SOAP message
   * with a DSML request payload.
   *
   * @param  req  Information about the request received from the client.
   * @param  res  Information about the response to send to the client.
   *
   * @throws  ServletException  If an error occurs during servlet processing.
   *
   * @throws  IOException  If an error occurs while interacting with the client.
   */
  public void doPost(HttpServletRequest req, HttpServletResponse res)
      throws ServletException, IOException
  {
    SOAPMessage reply = null;
    LDAPConnectionOptions connOptions = new LDAPConnectionOptions();
    LDAPConnection connection = null;

    try
    {
      MimeHeaders mimeHeaders = new MimeHeaders();
      Enumeration en = req.getHeaderNames();
      String bindDN = "";
      String bindPassword = "";
      while (en.hasMoreElements())
      {
        String headerName = (String)en.nextElement();
        String headerVal = req.getHeader(headerName);
        if(headerName.equalsIgnoreCase("authorization"))
        {
          if(headerVal.startsWith("Basic "))
          {
            String authorization = headerVal.substring(6).trim();
            // Decode and parse the authorization credentials
                  String unencoded =
                    new String(Base64.decode(authorization));
                  int colon = unencoded.indexOf(':');
                  if (colon < 0)
                    continue;
                  bindDN = unencoded.substring(0, colon).trim();
                  bindPassword = unencoded.substring(colon + 1);
          }
        }
        StringTokenizer tk = new StringTokenizer(headerVal, ",");
        while (tk.hasMoreTokens())
        {
          mimeHeaders.addHeader(headerName, tk.nextToken().trim());
        }
      }

      SOAPMessage message =
        messageFactory.createMessage(mimeHeaders, req.getInputStream());
      message.writeTo(System.out);

      Document doc = db.newDocument();
      SOAPBody body = message.getSOAPBody();

      Iterator it = body.getChildElements();
      while(it.hasNext())
      {
        Object obj = it.next();
        if(!(obj instanceof SOAPElement))
        {
          continue;
        }
        SOAPElement se = (SOAPElement) obj;
        JAXBElement<BatchRequest> batchRequestElement =
          unmarshaller.unmarshal(se, BatchRequest.class);
        BatchRequest batchRequest = batchRequestElement.getValue();
        BatchResponse batchResponse = objFactory.createBatchResponse();

        List<JAXBElement<?>> batchResponses = batchResponse.getBatchResponses();
        List<DsmlMessage> list = batchRequest.getBatchRequests();

        for(DsmlMessage nextElement : list)
        {
          if(nextElement instanceof SearchRequest)
          {
            // Process the search request.
            connection = new LDAPConnection(hostName, port, connOptions);
            connection.connectToHost(bindDN, bindPassword);

            SearchRequest sr = (SearchRequest) nextElement;
            DSMLSearchOperation ds = new DSMLSearchOperation(connection);
            SearchResponse searchResponse = ds.doSearch(objFactory, sr);

                  JAXBElement<SearchResponse> searchResponseEl =
              objFactory.createBatchResponseSearchResponse(searchResponse);
            batchResponses.add(searchResponseEl);

          } else if(nextElement instanceof AddRequest)
          {
            // Process the add request.
            connection = new LDAPConnection(hostName, port, connOptions);
            connection.connectToHost(bindDN, bindPassword);
            AddRequest ar = (AddRequest) nextElement;
            DSMLAddOperation addOp = new DSMLAddOperation(connection);
            LDAPResult addResponse = addOp.doOperation(objFactory, ar);
                  JAXBElement<LDAPResult> addResponseEl =
              objFactory.createBatchResponseAddResponse(addResponse);
            batchResponses.add(addResponseEl);
          } else if(nextElement instanceof AbandonRequest)
          {
            // Process the abandon request.
            connection = new LDAPConnection(hostName, port, connOptions);
            connection.connectToHost(bindDN, bindPassword);
            AbandonRequest ar = (AbandonRequest) nextElement;
            DSMLAbandonOperation ao = new DSMLAbandonOperation(connection);
            LDAPResult abandonResponse = ao.doOperation(objFactory, ar);
          } else if(nextElement instanceof ExtendedRequest)
          {
            // Process the extended request.
            connection = new LDAPConnection(hostName, port, connOptions);
            connection.connectToHost(bindDN, bindPassword);
            ExtendedRequest er = (ExtendedRequest) nextElement;
            DSMLExtendedOperation eo = new DSMLExtendedOperation(connection);
            ExtendedResponse extendedResponse = eo.doOperation(objFactory, er);
                  JAXBElement<ExtendedResponse> extendedResponseEl =
              objFactory.createBatchResponseExtendedResponse(extendedResponse);
            batchResponses.add(extendedResponseEl);

          } else if (nextElement instanceof DelRequest)
          {
            // Process the delete request.
            connection = new LDAPConnection(hostName, port, connOptions);
            connection.connectToHost(bindDN, bindPassword);
            DelRequest dr = (DelRequest) nextElement;
            DSMLDeleteOperation delOp = new DSMLDeleteOperation(connection);
            LDAPResult delResponse = delOp.doOperation(objFactory, dr);
                  JAXBElement<LDAPResult> delResponseEl =
              objFactory.createBatchResponseDelResponse(delResponse);
            batchResponses.add(delResponseEl);
          } else if (nextElement instanceof CompareRequest)
          {
            // Process the compare request.
            connection = new LDAPConnection(hostName, port, connOptions);
            connection.connectToHost(bindDN, bindPassword);
            CompareRequest cr = (CompareRequest) nextElement;
            DSMLCompareOperation compareOp =
              new DSMLCompareOperation(connection);
            LDAPResult compareResponse = compareOp.doOperation(objFactory, cr);
                  JAXBElement<LDAPResult> compareResponseEl =
              objFactory.createBatchResponseCompareResponse(compareResponse);
            batchResponses.add(compareResponseEl);
          } else if (nextElement instanceof ModifyDNRequest)
          {
            // Process the Modify DN request.
            connection = new LDAPConnection(hostName, port, connOptions);
            connection.connectToHost(bindDN, bindPassword);
            ModifyDNRequest mr = (ModifyDNRequest) nextElement;
            DSMLModifyDNOperation moddnOp =
              new DSMLModifyDNOperation(connection);
            LDAPResult moddnResponse = moddnOp.doOperation(objFactory, mr);
                  JAXBElement<LDAPResult> moddnResponseEl =
              objFactory.createBatchResponseModDNResponse(moddnResponse);
            batchResponses.add(moddnResponseEl);
          } else if( nextElement instanceof ModifyRequest)
          {
            // Process the Modify request.
            connection = new LDAPConnection(hostName, port, connOptions);
            connection.connectToHost(bindDN, bindPassword);
            ModifyRequest modr = (ModifyRequest) nextElement;
            DSMLModifyOperation modOp = new DSMLModifyOperation(connection);
            LDAPResult modResponse = modOp.doOperation(objFactory,  modr);
                  JAXBElement<LDAPResult> modResponseEl =
              objFactory.createBatchResponseModifyResponse(modResponse);
            batchResponses.add(modResponseEl);
          } else
          {
            String msg = "Invalid DSML request:" + nextElement;
            throw new IOException(msg);
          }
        }

        JAXBElement<BatchResponse> batchResponseElement =
         objFactory.createBatchResponse(batchResponse);

        marshaller.marshal(batchResponseElement, System.out);

        marshaller.marshal(batchResponseElement, doc);
      }

      // Send the DSML response back to the client.
      reply = messageFactory.createMessage();
      sendResponse(doc, false, reply, res, null);

    } catch(Exception se)
    {
      se.printStackTrace();
      // send SOAP fault
      try
      {
        reply = messageFactory.createMessage();
        sendResponse(null, true, reply, res, se);
      } catch(Exception e) { }
    } finally
    {
      if(connection != null)
      {
        connection.close();
      }
    }
  }

  /**
   * Send a response back to the client. This could be either a SOAP fault
   * or a correct DSML response.
   *
   * @param  doc    The document to include in the response.
   * @param  error  Indicates whether an error occurred.
   * @param  reply  The reply to send to the client.
   * @param  res    Information about the HTTP response to the client.
   * @param  e      Information about any exception that was thrown.
   *
   * @throws  IOException  If an error occurs while interacting with the client.
   *
   * @throws  SOAPException  If an encoding or decoding error occurs.
   */
  private void sendResponse(Document doc, boolean error, SOAPMessage reply,
    HttpServletResponse res, Exception e)
    throws IOException, SOAPException
  {
    SOAPHeader header = reply.getSOAPHeader();
    header.detachNode();
    SOAPBody replyBody = reply.getSOAPBody();

    res.setHeader("Content-Type", "text/xml");

    if(error)
    {
      SOAPFault fault = replyBody.addFault();
      Name faultName = SOAPFactory.newInstance().createName("Server",
          "", SOAPConstants.URI_NS_SOAP_ENVELOPE);
      fault.setFaultCode(faultName);
      fault.setFaultString("Server Error: " + e.getMessage());
      // FIXME - Set correct fault actor
      fault.setFaultActor("http://localhost:8080");
    } else
    {
      SOAPElement bodyElement = replyBody.addDocument(doc);
    }

    reply.saveChanges();

    OutputStream os = res.getOutputStream();
    reply.writeTo(os);
    os.flush();
  }
}

