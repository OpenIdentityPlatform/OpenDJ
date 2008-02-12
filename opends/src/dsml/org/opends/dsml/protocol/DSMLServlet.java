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
package org.opends.dsml.protocol;


import java.io.BufferedInputStream;
import java.io.InputStream;
import java.text.ParseException;
import static javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI;
import javax.xml.bind.JAXBException;
import org.opends.messages.Message;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.ldap.LDAPResultCode;
import org.opends.server.tools.LDAPConnection;
import org.opends.server.tools.LDAPConnectionOptions;
import org.opends.server.util.Base64;
import org.w3c.dom.Document;

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
import javax.xml.soap.*;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.StringTokenizer;
import java.util.concurrent.atomic.AtomicInteger;
import javax.xml.validation.SchemaFactory;
import org.opends.server.tools.LDAPConnectionException;
import org.opends.server.types.LDAPException;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;
import org.xml.sax.helpers.XMLReaderFactory;


/**
 * This class provides the entry point for the DSML request.
 * It parses the SOAP request, calls the appropriate class
 * which performs the LDAP operation, and returns the response
 * as a DSML response.
 */
public class DSMLServlet extends HttpServlet {
  private static final String PKG_NAME = "org.opends.dsml.protocol";
  private static final String PORT = "ldap.port";
  private static final String HOST = "ldap.host";
  private static final long serialVersionUID = -3748022009593442973L;
  private static final AtomicInteger nextMessageID = new AtomicInteger(1);

  // definitions of return error messages
  private static final String MALFORMED_REQUEST = "malformedRequest";
  private static final String NOT_ATTEMPTED = "notAttempted";
  private static final String AUTHENTICATION_FAILED = "authenticationFailed";
  private static final String COULD_NOT_CONNECT = "couldNotConnect";
  private static final String GATEWAY_INTERNAL_ERROR = "gatewayInternalError";
  
  private static final String UNKNOWN_ERROR = "Unknown error";

  // definitions of onError values
  private static final String ON_ERROR_RESUME = "resume";
  private static final String ON_ERROR_EXIT = "exit";
  
  private Unmarshaller unmarshaller;
  private Marshaller marshaller;
  private ObjectFactory objFactory;
  private MessageFactory messageFactory;
  private DocumentBuilder db;

  // this extends the default handler of SAX parser. It helps to retrieve the
  // requestID value when the xml request is malformed and thus unparsable
  // using SOAP or JAXB.
  private DSMLContentHandler contentHandler;
  
  private String hostName;
  private Integer port;
  
  /**
   * This method will be called by the Servlet Container when
   * this servlet is being placed into service.
   *
   * @param config - the <CODE>ServletConfig</CODE> object that
   *               contains configutation information for this servlet.
   * @throws ServletException If an error occurs during processing.
   */
  public void init(ServletConfig config) throws ServletException {
    
    try {
      hostName = config.getServletContext().getInitParameter(HOST);

      port = new Integer(config.getServletContext().getInitParameter(PORT));

      JAXBContext jaxbContext = JAXBContext.newInstance(PKG_NAME);
      unmarshaller = jaxbContext.createUnmarshaller();
      // assign the DSMLv2 schema for validation
      URL schema = getClass().getResource("/resources/DSMLv2.xsd");
      if ( schema != null ) {
        SchemaFactory sf = SchemaFactory.newInstance(W3C_XML_SCHEMA_NS_URI);
        unmarshaller.setSchema(sf.newSchema(schema));
      }

      marshaller = jaxbContext.createMarshaller();

      objFactory = new ObjectFactory();
      messageFactory = MessageFactory.newInstance();
      DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
      dbf.setNamespaceAware(true);
      db = dbf.newDocumentBuilder();

      this.contentHandler = new DSMLContentHandler();

      DirectoryServer.bootstrapClient();
    } catch (Exception je) {
      je.printStackTrace();
      throw new ServletException(je.getMessage());
    }
  }
  
  /**
   * The HTTP POST operation. This servlet expects a SOAP message
   * with a DSML request payload.
   *
   * @param req Information about the request received from the client.
   * @param res Information about the response to send to the client.
   * @throws ServletException If an error occurs during servlet processing.
   * @throws IOException   If an error occurs while interacting with the client.
   */
  public void doPost(HttpServletRequest req, HttpServletResponse res)
  throws ServletException, IOException {
    LDAPConnectionOptions connOptions = new LDAPConnectionOptions();
    LDAPConnection connection = null;
    BatchRequest batchRequest = null;
    
    // Keep the Servlet input stream buffered in case the SOAP unmarshalling
    // fails, the SAX parsing will be able to retrieve the requestID even if
    // the XML is malmformed by resetting the input stream.
    BufferedInputStream is = new BufferedInputStream(req.getInputStream(),
                                                     65536);
    if ( is.markSupported() ) {
      is.mark(65536);
    }

    // Create response in the beginning as it might be used if the parsing
    // failes.
    BatchResponse batchResponse = objFactory.createBatchResponse();
    List<JAXBElement<?>> batchResponses = batchResponse.getBatchResponses();
    Document doc = db.newDocument();
    
    SOAPBody soapBody = null;
    
    MimeHeaders mimeHeaders = new MimeHeaders();
    Enumeration en = req.getHeaderNames();
    String bindDN = null;
    String bindPassword = null;
    boolean authorizationInHeader = false;
    while (en.hasMoreElements()) {
      String headerName = (String) en.nextElement();
      String headerVal = req.getHeader(headerName);
      if (headerName.equalsIgnoreCase("authorization")) {
        if (headerVal.startsWith("Basic ")) {
          authorizationInHeader = true;
          String authorization = headerVal.substring(6).trim();
          try {
            String unencoded = new String(Base64.decode(authorization));
            int colon = unencoded.indexOf(':');
            if (colon > 0) {
              bindDN = unencoded.substring(0, colon).trim();
              bindPassword = unencoded.substring(colon + 1);
            }
          } catch (ParseException ex) {
            // DN:password parsing error
            batchResponses.add(
              createErrorResponse(
                    new LDAPException(LDAPResultCode.INVALID_CREDENTIALS,
                    Message.raw(ex.getMessage()))));
            break;
          }
        }
      }
      StringTokenizer tk = new StringTokenizer(headerVal, ",");
      while (tk.hasMoreTokens()) {
        mimeHeaders.addHeader(headerName, tk.nextToken().trim());
      }
    }

    if ( ! authorizationInHeader ) {
      // if no authorization, set default user
      bindDN = "";
      bindPassword = "";
    } else {
      // otherwise if DN or password is null, send back an error
      if ( (bindDN == null || bindPassword == null)
         && batchResponses.size()==0) {
        batchResponses.add(
              createErrorResponse(
                    new LDAPException(LDAPResultCode.INVALID_CREDENTIALS,
                    Message.raw("Unable to retrieve credentials."))));
      }
    }

    // if an error already occured, the list is not empty
    if ( batchResponses.size() == 0 ) {
      try {
        SOAPMessage message = messageFactory.createMessage(mimeHeaders, is);
        soapBody = message.getSOAPBody();
      } catch (SOAPException ex) {
        // SOAP was unable to parse XML successfully
        batchResponses.add(
          createXMLParsingErrorResponse(is, 
                                        batchResponse,
                                        String.valueOf(ex.getCause())));
      }
    }

    if ( soapBody != null ) {
      Iterator it = soapBody.getChildElements();
      while (it.hasNext()) {
        Object obj = it.next();
        if (!(obj instanceof SOAPElement)) {
          continue;
        }
        SOAPElement se = (SOAPElement) obj;
        JAXBElement<BatchRequest> batchRequestElement = null;
        try {
          batchRequestElement = unmarshaller.unmarshal(se, BatchRequest.class);
        } catch (JAXBException e) {
          // schema validation failed
          batchResponses.add(createXMLParsingErrorResponse(is,
                                                       batchResponse,
                                                       String.valueOf(e)));
        }
        if ( batchRequestElement != null ) {
          batchRequest = batchRequestElement.getValue();
          
          // set requestID in response
          batchResponse.setRequestID(batchRequest.getRequestID());
          
          boolean connected = false;
          if ( connection == null ) {
            connection = new LDAPConnection(hostName, port, connOptions);
            try {
              connection.connectToHost(bindDN, bindPassword);
              connected = true;
            } catch (LDAPConnectionException e) {
              // if connection failed, return appropriate error response
              batchResponses.add(createErrorResponse(e));
            }
          }
          if ( connected ) {
            List<DsmlMessage> list = batchRequest.getBatchRequests();
            
            for (DsmlMessage request : list) {
              JAXBElement<?> result = performLDAPRequest(connection, request);
              if ( result != null ) {
                batchResponses.add(result);
              }
              // evaluate response to check if an error occured
              Object o = result.getValue();
              if ( o instanceof ErrorResponse ) {
                if ( ON_ERROR_EXIT.equals(batchRequest.getOnError()) ) {
                  break;
                }
              } else if ( o instanceof LDAPResult ) {
                int code = ((LDAPResult)o).getResultCode().getCode();
                if ( code != LDAPResultCode.SUCCESS
                  && code != LDAPResultCode.REFERRAL
                  && code != LDAPResultCode.COMPARE_TRUE
                  && code != LDAPResultCode.COMPARE_FALSE ) {
                  if ( ON_ERROR_EXIT.equals(batchRequest.getOnError()) ) {
                    break;
                  }
                }
              }
            }
          }
          // close connection to LDAP server
          if ( connection != null ) {
            connection.close(nextMessageID);
          }
        }
      }
    }
    try {
      marshaller.marshal(objFactory.createBatchResponse(batchResponse), doc);
      sendResponse(doc, res);
    } catch (Exception e) {
      e.printStackTrace();
    }

  }

  /**
   * Returns an error response after a parsing error. The response has the
   * requestID of the batch request, the error response message of the parsing
   * exception message and the type 'malformed request'.
   *
   * @param is the xml InputStream to parse
   * @param batchResponse the JAXB object to fill in
   * @param parserErrorMessage the parsing error message
   *
   * @return a JAXBElement that contains an ErrorResponse
   */
  private JAXBElement<ErrorResponse> createXMLParsingErrorResponse(
                                                    InputStream is,
                                                    BatchResponse batchResponse,
                                                    String parserErrorMessage) {
    ErrorResponse errorResponse = objFactory.createErrorResponse();
    
    try {
      // try alternative XML parsing using SAX to retrieve requestID value
      XMLReader xmlReader = XMLReaderFactory.createXMLReader();
      // clear previous match
      this.contentHandler.requestID = null;
      xmlReader.setContentHandler(this.contentHandler);
      is.reset();
      
      xmlReader.parse(new InputSource(is));
    } catch (Throwable e) {
      // document is unparsable so will jump here
    }
    if ( parserErrorMessage!= null ) {
      errorResponse.setMessage(parserErrorMessage);
    }
    batchResponse.setRequestID(this.contentHandler.requestID);
    
    errorResponse.setType(MALFORMED_REQUEST);
    
    return objFactory.createBatchResponseErrorResponse(errorResponse);
  }

  /**
   * Returns an error response with attributes set according to the exception
   * provided as argument.
   *
   * @param t the exception that occured
   *
   * @return a JAXBElement that contains an ErrorResponse
   */
  private JAXBElement<ErrorResponse> createErrorResponse(Throwable t) {
    // potential exceptions are IOException, LDAPException, ASN1Exception
    
    ErrorResponse errorResponse = objFactory.createErrorResponse();
    errorResponse.setMessage(String.valueOf(t));
    
    if ( t instanceof LDAPException ) {
      switch(((LDAPException)t).getResultCode()) {
        case LDAPResultCode.AUTHORIZATION_DENIED:
        case LDAPResultCode.INAPPROPRIATE_AUTHENTICATION:
        case LDAPResultCode.INVALID_CREDENTIALS:
        case LDAPResultCode.STRONG_AUTH_REQUIRED:
          errorResponse.setType(AUTHENTICATION_FAILED);
          break;
          
        case LDAPResultCode.CLIENT_SIDE_CONNECT_ERROR:
          errorResponse.setType(COULD_NOT_CONNECT);
          break;
          
        case LDAPResultCode.UNWILLING_TO_PERFORM:
          errorResponse.setType(NOT_ATTEMPTED);
          break;
          
        default:
          errorResponse.setType(UNKNOWN_ERROR);
          break;
      }
    } else if ( t instanceof LDAPConnectionException ) {
      errorResponse.setType(COULD_NOT_CONNECT);
    } else {
      errorResponse.setType(GATEWAY_INTERNAL_ERROR);
    }
    
    return objFactory.createBatchResponseErrorResponse(errorResponse);
  }

  /**
   * Performs the LDAP operation and sends back the result (if any). In case
   * of error, an error reponse is returned.
   *
   * @param connection a connected connection
   * @param request the JAXB request to perform
   *
   * @return null for an abandon request, the expect result for all other
   *         requests or an error in case of unexpected behaviour.
   */
  private JAXBElement<?> performLDAPRequest(LDAPConnection connection,
                                            DsmlMessage request) {
    try {
      if (request instanceof SearchRequest) {
        // Process the search request.
        SearchRequest sr = (SearchRequest) request;
        DSMLSearchOperation ds = new DSMLSearchOperation(connection);
        SearchResponse searchResponse = ds.doSearch(objFactory, sr);
        
        return objFactory.createBatchResponseSearchResponse(searchResponse);
      } else if (request instanceof AddRequest) {
        // Process the add request.
        AddRequest ar = (AddRequest) request;
        DSMLAddOperation addOp = new DSMLAddOperation(connection);
        LDAPResult addResponse = addOp.doOperation(objFactory, ar);
        return objFactory.createBatchResponseAddResponse(addResponse);
      } else if (request instanceof AbandonRequest) {
        // Process the abandon request.
        AbandonRequest ar = (AbandonRequest) request;
        DSMLAbandonOperation ao = new DSMLAbandonOperation(connection);
        LDAPResult abandonResponse = ao.doOperation(objFactory, ar);
        return null;
      } else if (request instanceof ExtendedRequest) {
        // Process the extended request.
        ExtendedRequest er = (ExtendedRequest) request;
        DSMLExtendedOperation eo = new DSMLExtendedOperation(connection);
        ExtendedResponse extendedResponse = eo.doOperation(objFactory, er);
        return objFactory.createBatchResponseExtendedResponse(extendedResponse);
        
      } else if (request instanceof DelRequest) {
        // Process the delete request.
        DelRequest dr = (DelRequest) request;
        DSMLDeleteOperation delOp = new DSMLDeleteOperation(connection);
        LDAPResult delResponse = delOp.doOperation(objFactory, dr);
        return objFactory.createBatchResponseDelResponse(delResponse);
      } else if (request instanceof CompareRequest) {
        // Process the compare request.
        CompareRequest cr = (CompareRequest) request;
        DSMLCompareOperation compareOp =
                new DSMLCompareOperation(connection);
        LDAPResult compareResponse = compareOp.doOperation(objFactory, cr);
        return objFactory.createBatchResponseCompareResponse(compareResponse);
      } else if (request instanceof ModifyDNRequest) {
        // Process the Modify DN request.
        ModifyDNRequest mr = (ModifyDNRequest) request;
        DSMLModifyDNOperation moddnOp =
                new DSMLModifyDNOperation(connection);
        LDAPResult moddnResponse = moddnOp.doOperation(objFactory, mr);
        return objFactory.createBatchResponseModDNResponse(moddnResponse);
      } else if (request instanceof ModifyRequest) {
        // Process the Modify request.
        ModifyRequest modr = (ModifyRequest) request;
        DSMLModifyOperation modOp = new DSMLModifyOperation(connection);
        LDAPResult modResponse = modOp.doOperation(objFactory, modr);
        return objFactory.createBatchResponseModifyResponse(modResponse);
      } else if (request instanceof AuthRequest) {
        // Process the Auth request.
        // Only returns an BatchReponse with an AuthResponse containing the
        // LDAP result code AUTH_METHOD_NOT_SUPPORTED
        ResultCode resultCode = objFactory.createResultCode();
        resultCode.setCode(LDAPResultCode.AUTH_METHOD_NOT_SUPPORTED);
        
        LDAPResult ldapResult = objFactory.createLDAPResult();
        ldapResult.setResultCode(resultCode);
        
        return objFactory.createBatchResponseAuthResponse(ldapResult);
      } 
    } catch (Throwable t) {
      return createErrorResponse(t);
    }
    // should never happen as the schema was validated
    return null;
  }

  
  /**
   * Send a response back to the client. This could be either a SOAP fault
   * or a correct DSML response.
   *
   * @param doc   The document to include in the response.
   * @param res   Information about the HTTP response to the client.
   *
   * @throws IOException   If an error occurs while interacting with the client.
   * @throws SOAPException If an encoding or decoding error occurs.
   */
  private void sendResponse(Document doc, HttpServletResponse res)
    throws IOException, SOAPException {
    
    SOAPMessage reply = messageFactory.createMessage();
    SOAPHeader header = reply.getSOAPHeader();
    header.detachNode();
    SOAPBody replyBody = reply.getSOAPBody();
    
    res.setHeader("Content-Type", "text/xml");
    
    SOAPElement bodyElement = replyBody.addDocument(doc);
    
    reply.saveChanges();
    
    OutputStream os = res.getOutputStream();
    reply.writeTo(os);
    os.flush();
  }
  
  
  /**
   * Retrieves a message ID that may be used for the next LDAP message sent to
   * the Directory Server.
   *
   * @return  A message ID that may be used for the next LDAP message sent to
   *          the Directory Server.
   */
  public static int nextMessageID() {
    int nextID = nextMessageID.getAndIncrement();
    if (nextID == Integer.MAX_VALUE) {
      nextMessageID.set(1);
    }
    
    return nextID;
  }

  /**
   * This class is used when a xml request is malformed to retrieve the
   * requestID value using an event xml parser.
   */
  private static class DSMLContentHandler extends DefaultHandler {
    private String requestID;
    /*
     * This function fetches the requestID value of the batchRequest xml
     * element and call the default implementation (super).
     */
    public void startElement(String uri, String localName, String qName,
                             Attributes attributes) throws SAXException {
      if ( requestID==null && localName.equals("batchRequest") ) {
        requestID = attributes.getValue("requestID");
      }
      super.startElement(uri, localName, qName, attributes);
    }
  }
}

