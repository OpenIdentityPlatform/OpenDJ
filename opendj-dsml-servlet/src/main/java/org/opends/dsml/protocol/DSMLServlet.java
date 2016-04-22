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
 * Copyright 2006-2010 Sun Microsystems, Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 */
package org.opends.dsml.protocol;


import static javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI;
import static org.opends.server.protocols.ldap.LDAPResultCode.
  CLIENT_SIDE_CONNECT_ERROR;
import static org.opends.server.util.ServerConstants.SASL_MECHANISM_PLAIN;
import static org.opends.messages.CoreMessages.
  INFO_RESULT_CLIENT_SIDE_ENCODING_ERROR;
import static org.opends.messages.CoreMessages.INFO_RESULT_AUTHORIZATION_DENIED;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.URL;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.XMLConstants;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.soap.MessageFactory;
import javax.xml.soap.MimeHeaders;
import javax.xml.soap.SOAPBody;
import javax.xml.soap.SOAPConstants;
import javax.xml.soap.SOAPElement;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPHeader;
import javax.xml.soap.SOAPMessage;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DereferenceAliasesPolicy;
import org.forgerock.opendj.ldap.SearchScope;
import org.opends.server.controls.ProxiedAuthV2Control;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.ldap.LDAPConstants;
import org.opends.server.protocols.ldap.LDAPFilter;
import org.opends.server.protocols.ldap.LDAPMessage;
import org.opends.server.protocols.ldap.LDAPResultCode;
import org.opends.server.protocols.ldap.SearchRequestProtocolOp;
import org.opends.server.schema.SchemaConstants;
import org.opends.server.tools.LDAPConnection;
import org.opends.server.tools.LDAPConnectionException;
import org.opends.server.tools.LDAPConnectionOptions;
import org.opends.server.tools.SSLConnectionException;
import org.opends.server.tools.SSLConnectionFactory;
import org.opends.server.types.LDAPException;
import org.opends.server.util.Base64;
import org.w3c.dom.Document;
import org.xml.sax.Attributes;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

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
  private static final String USERDN = "ldap.userdn";
  private static final String USERPWD = "ldap.userpassword";
  private static final String USESSL = "ldap.usessl";
  private static final String USESTARTTLS = "ldap.usestarttls";
  private static final String TRUSTSTOREPATH = "ldap.truststore.path";
  private static final String TRUSTSTOREPASSWORD = "ldap.truststore.password";
  private static final String TRUSTALLCERTS = "ldap.trustall";
  private static final String USEHTTPAUTHZID = "ldap.authzidtypeisid";
  private static final String EXOPSTRINGPREFIX = "ldap.exop.string.";
  private static final long serialVersionUID = -3748022009593442973L;
  private static final AtomicInteger nextMessageID = new AtomicInteger(1);

  // definitions of return error messages
  private static final String MALFORMED_REQUEST = "malformedRequest";
  private static final String NOT_ATTEMPTED = "notAttempted";
  private static final String AUTHENTICATION_FAILED = "authenticationFailed";
  private static final String COULD_NOT_CONNECT = "couldNotConnect";
  private static final String GATEWAY_INTERNAL_ERROR = "gatewayInternalError";
  private static final String UNRESOLVABLE_URI = "unresolvableURI";

  // definitions of onError values
  private static final String ON_ERROR_EXIT = "exit";

  private static JAXBContext jaxbContext;
  private static Schema schema;

  /** Prevent multiple logging when trying to set unavailable/unsupported parser features */
  private static final AtomicBoolean logFeatureWarnings = new AtomicBoolean(false);

  private String hostName;
  private Integer port;
  private String userDN;
  private String userPassword;
  private Boolean useSSL;
  private Boolean useStartTLS;
  private String trustStorePathValue;
  private String trustStorePasswordValue;
  private Boolean trustAll;
  private Boolean useHTTPAuthzID;
  private final Set<String> exopStrings = new HashSet<>();

  /**
   * This method will be called by the Servlet Container when
   * this servlet is being placed into service.
   *
   * @param config - the <CODE>ServletConfig</CODE> object that
   *               contains configuration information for this servlet.
   * @throws ServletException If an error occurs during processing.
   */
  @Override
  public void init(ServletConfig config) throws ServletException {
    try {
      hostName = stringValue(config, HOST);
      port = Integer.valueOf(stringValue(config, PORT));
      userDN = stringValue(config, USERDN);
      userPassword = stringValue(config, USERPWD);
      useSSL = booleanValue(config, USESSL);
      useStartTLS = booleanValue(config, USESTARTTLS);
      trustStorePathValue = stringValue(config, TRUSTSTOREPATH);
      trustStorePasswordValue = stringValue(config, TRUSTSTOREPASSWORD);
      trustAll = booleanValue(config, TRUSTALLCERTS);
      useHTTPAuthzID = booleanValue(config, USEHTTPAUTHZID);

      /*
       * Find all the param-names matching the pattern:
       * ldap.exop.string.1.2.3.4.5
       * and if the value's true then mark that OID (1.2.3.4.5) as one returning
       * a string value.
       */
      Enumeration<String> names = config.getServletContext().getInitParameterNames();
      while (names.hasMoreElements())
      {
        String name = names.nextElement();
        if (name.startsWith(EXOPSTRINGPREFIX) &&
            Boolean.valueOf(config.getServletContext().getInitParameter(name)))
        {
          exopStrings.add(name.substring(EXOPSTRINGPREFIX.length()));
        }
      }

      // allow the use of anyURI values in adds and modifies
      System.setProperty("mapAnyUriToUri", "true");

      if(jaxbContext==null)
      {
        jaxbContext = JAXBContext.newInstance(PKG_NAME, getClass().getClassLoader());
      }
      // assign the DSMLv2 schema for validation
      if(schema==null)
      {
        URL url = getClass().getResource("/resources/DSMLv2.xsd");
        if ( url != null ) {
          SchemaFactory sf = SchemaFactory.newInstance(W3C_XML_SCHEMA_NS_URI);
          schema = sf.newSchema(url);
        }
      }

      DirectoryServer.bootstrapClient();
    } catch (Exception je) {
      je.printStackTrace();
      throw new ServletException(je.getMessage());
    }
  }

  private boolean booleanValue(ServletConfig config, String paramName)
  {
    return Boolean.valueOf(stringValue(config, paramName));
  }

  private String stringValue(ServletConfig config, String paramName)
  {
    return config.getServletContext().getInitParameter(paramName);
  }

  /**
   * Check if using the proxy authz control will work, by using it to read
   * the Root DSE.
   *
   * @param connection The authenticated LDAP connection used to check.
   * @param authorizationID The authorization ID, in the format
   *                        "u:&lt;userid&gt;" or "dn:&lt;DN&gt;".
   * @return a configured proxy authz control.
   * @throws  LDAPConnectionException  If an error occurs during the check.
   *
   */
  private org.opends.server.types.Control checkAuthzControl(
      LDAPConnection connection, String authorizationID)
      throws LDAPConnectionException
  {
    LinkedHashSet<String>attributes = new LinkedHashSet<>(1);
    attributes.add(SchemaConstants.NO_ATTRIBUTES);
    ArrayList<org.opends.server.types.Control> controls = new ArrayList<>(1);
    org.opends.server.types.Control proxyAuthzControl =
        new ProxiedAuthV2Control(true, ByteString.valueOfUtf8(authorizationID));
    controls.add(proxyAuthzControl);

    try
    {
      SearchRequestProtocolOp protocolOp = new SearchRequestProtocolOp(
          ByteString.empty(), SearchScope.BASE_OBJECT,
          DereferenceAliasesPolicy.NEVER, 0, 0, true,
          LDAPFilter.objectClassPresent(), attributes);
      byte opType;
      LDAPMessage msg =
        new LDAPMessage(DSMLServlet.nextMessageID(), protocolOp, controls);
      connection.getLDAPWriter().writeMessage(msg);
      do {
        LDAPMessage responseMessage = connection.getLDAPReader().
            readMessage();
        opType = responseMessage.getProtocolOpType();
        switch (opType)
        {
        case LDAPConstants.OP_TYPE_SEARCH_RESULT_DONE:
          switch (responseMessage.getSearchResultDoneProtocolOp().
              getResultCode())
          {
            default:
              LocalizableMessage m = INFO_RESULT_AUTHORIZATION_DENIED.get();
              throw new LDAPConnectionException(m, CLIENT_SIDE_CONNECT_ERROR,
                  null);
            case LDAPResultCode.SUCCESS:
              return proxyAuthzControl;
          }
        }
      } while (true);
    }
    catch (LDAPException | IOException ie)
    {
      LocalizableMessage m = INFO_RESULT_CLIENT_SIDE_ENCODING_ERROR.get();
      throw new LDAPConnectionException(m, CLIENT_SIDE_CONNECT_ERROR, null, ie);
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
  @Override
  public void doPost(HttpServletRequest req, HttpServletResponse res)
  throws ServletException, IOException {
    LDAPConnectionOptions connOptions = new LDAPConnectionOptions();
    connOptions.setUseSSL(useSSL);
    connOptions.setStartTLS(useStartTLS);

    LDAPConnection connection = null;
    BatchRequest batchRequest = null;

    // Keep the Servlet input stream buffered in case the SOAP un-marshalling
    // fails, the SAX parsing will be able to retrieve the requestID even if
    // the XML is malformed by resetting the input stream.
    BufferedInputStream is = new BufferedInputStream(req.getInputStream(),
                                                     65536);
    if ( is.markSupported() ) {
      is.mark(65536);
    }

    // Create response in the beginning as it might be used if the parsing
    // fails.
    ObjectFactory objFactory = new ObjectFactory();
    BatchResponse batchResponse = objFactory.createBatchResponse();
    List<JAXBElement<?>> batchResponses = batchResponse.getBatchResponses();

    // Thi sis only used for building the response
    Document doc = createSafeDocument();

    MessageFactory messageFactory = null;
    String messageContentType = null;

    if (useSSL || useStartTLS)
    {
      SSLConnectionFactory sslConnectionFactory = new SSLConnectionFactory();
      try
      {
        sslConnectionFactory.init(trustAll, null, null, null,
                                  trustStorePathValue, trustStorePasswordValue);
      }
      catch(SSLConnectionException e)
      {
        batchResponses.add(
          createErrorResponse(objFactory,
            new LDAPException(LDAPResultCode.CLIENT_SIDE_CONNECT_ERROR,
              LocalizableMessage.raw(
              "Invalid SSL or TLS configuration to connect to LDAP server."))));
      }
      connOptions.setSSLConnectionFactory(sslConnectionFactory);
    }

    SOAPBody soapBody = null;

    MimeHeaders mimeHeaders = new MimeHeaders();
    String bindDN = null;
    String bindPassword = null;
    boolean authenticationInHeader = false;
    boolean authenticationIsID = false;
    final Enumeration<String> en = req.getHeaderNames();
    while (en.hasMoreElements()) {
      String headerName = en.nextElement();
      String headerVal = req.getHeader(headerName);
      if (headerName.equalsIgnoreCase("content-type")) {
        try
        {
          if (headerVal.startsWith(SOAPConstants.SOAP_1_1_CONTENT_TYPE))
          {
            messageFactory = MessageFactory.newInstance(SOAPConstants.SOAP_1_1_PROTOCOL);
            messageContentType = SOAPConstants.SOAP_1_1_CONTENT_TYPE;
          }
          else if (headerVal.startsWith(SOAPConstants.SOAP_1_2_CONTENT_TYPE))
          {
            MessageFactory.newInstance(SOAPConstants.SOAP_1_2_PROTOCOL);
            messageContentType = SOAPConstants.SOAP_1_2_CONTENT_TYPE;
          }
          else {
            throw new ServletException("Content-Type does not match SOAP 1.1 or SOAP 1.2");
          }
        }
        catch (SOAPException e)
        {
          throw new ServletException(e.getMessage());
        }
      } else if (headerName.equalsIgnoreCase("authorization") && headerVal.startsWith("Basic "))
      {
        authenticationInHeader = true;
        String authorization = headerVal.substring(6).trim();
        try {
          String unencoded = new String(Base64.decode(authorization));
          int colon = unencoded.indexOf(':');
          if (colon > 0) {
            if (useHTTPAuthzID)
            {
              connOptions.setSASLMechanism("mech=" + SASL_MECHANISM_PLAIN);
              connOptions.addSASLProperty(
                  "authid=u:" + unencoded.substring(0, colon).trim());
              authenticationIsID = true;
            }
            else
            {
              bindDN = unencoded.substring(0, colon).trim();
            }
            bindPassword = unencoded.substring(colon + 1);
          }
        } catch (ParseException ex) {
          // user/DN:password parsing error
          batchResponses.add(
            createErrorResponse(objFactory,
                  new LDAPException(LDAPResultCode.INVALID_CREDENTIALS,
                  LocalizableMessage.raw(ex.getMessage()))));
          break;
        }
      }
      StringTokenizer tk = new StringTokenizer(headerVal, ",");
      while (tk.hasMoreTokens()) {
        mimeHeaders.addHeader(headerName, tk.nextToken().trim());
      }
    }

    if ( ! authenticationInHeader ) {
      // if no authentication, set default user from web.xml
      if (userDN != null)
      {
        bindDN = userDN;
        if (userPassword != null)
        {
          bindPassword = userPassword;
        }
        else
        {
          batchResponses.add(
              createErrorResponse(objFactory,
                    new LDAPException(LDAPResultCode.INVALID_CREDENTIALS,
                    LocalizableMessage.raw("Invalid configured credentials."))));
        }
      }
      else
      {
        bindDN = "";
        bindPassword = "";
      }
    } else {
      // otherwise if DN or password is null, send back an error
      if (((!authenticationIsID && bindDN == null) || bindPassword == null)
         && batchResponses.isEmpty()) {
        batchResponses.add(
              createErrorResponse(objFactory,
                    new LDAPException(LDAPResultCode.INVALID_CREDENTIALS,
                    LocalizableMessage.raw("Unable to retrieve credentials."))));
      }
    }

    // if an error already occurred, the list is not empty
    if ( batchResponses.isEmpty() ) {
      try {
        SOAPMessage message = messageFactory.createMessage(mimeHeaders, is);
        soapBody = message.getSOAPBody();
      } catch (SOAPException ex) {
        // SOAP was unable to parse XML successfully
        batchResponses.add(
          createXMLParsingErrorResponse(is,
                                        objFactory,
                                        batchResponse,
                                        String.valueOf(ex.getCause())));
      }
    }

    if ( soapBody != null ) {
      Iterator<?> it = soapBody.getChildElements();
      while (it.hasNext()) {
        Object obj = it.next();
        if (!(obj instanceof SOAPElement)) {
          continue;
        }
        // Parse and unmarshall the SOAP object - the implementation prevents the use of a
        // DOCTYPE and xincludes, so should be safe. There is no way to configure a more
        // restrictive parser.
        SOAPElement se = (SOAPElement) obj;
        JAXBElement<BatchRequest> batchRequestElement = null;
        try {
          Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
          unmarshaller.setSchema(schema);
          batchRequestElement = unmarshaller.unmarshal(se, BatchRequest.class);
        } catch (JAXBException e) {
          // schema validation failed
          batchResponses.add(createXMLParsingErrorResponse(is,
                                                       objFactory,
                                                       batchResponse,
                                                       String.valueOf(e)));
        }
        if ( batchRequestElement != null ) {
          boolean authzInBind = false;
          boolean authzInControl = false;
          batchRequest = batchRequestElement.getValue();

          /*
           *  Process optional authRequest (i.e. use authz)
           */
          if (batchRequest.authRequest != null) {
            if (authenticationIsID) {
              // If we are using SASL, then use the bind authz.
              connOptions.addSASLProperty("authzid=" +
                  batchRequest.authRequest.getPrincipal());
              authzInBind = true;
            } else {
              // If we are using simple then we have to do some work after
              // the bind.
              authzInControl = true;
            }
          }
          // set requestID in response
          batchResponse.setRequestID(batchRequest.getRequestID());
          org.opends.server.types.Control proxyAuthzControl = null;

          boolean connected = false;

          if ( connection == null ) {
            connection = new LDAPConnection(hostName, port, connOptions);
            try {

              connection.connectToHost(bindDN, bindPassword);
              if (authzInControl)
              {
                proxyAuthzControl = checkAuthzControl(connection,
                    batchRequest.authRequest.getPrincipal());
              }
              if (authzInBind || authzInControl)
              {
                LDAPResult authResponse = objFactory.createLDAPResult();
                ResultCode code = ResultCodeFactory.create(objFactory,
                    LDAPResultCode.SUCCESS);
                authResponse.setResultCode(code);
                batchResponses.add(
                    objFactory.createBatchResponseAuthResponse(authResponse));
              }
              connected = true;
            } catch (LDAPConnectionException e) {
              // if connection failed, return appropriate error response
              batchResponses.add(createErrorResponse(objFactory, e));
            }
          }
          if ( connected ) {
            List<DsmlMessage> list = batchRequest.getBatchRequests();

            for (DsmlMessage request : list) {
              JAXBElement<?> result = performLDAPRequest(connection, objFactory, proxyAuthzControl, request);
              if ( result != null ) {
                batchResponses.add(result);
              }
              // evaluate response to check if an error occurred
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
                  && code != LDAPResultCode.COMPARE_FALSE && ON_ERROR_EXIT.equals(batchRequest.getOnError()) )
                {
                  break;
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
      Marshaller marshaller = jaxbContext.createMarshaller();
      marshaller.marshal(objFactory.createBatchResponse(batchResponse), doc);
      sendResponse(doc, messageFactory, messageContentType, res);
    } catch (Exception e) {
      e.printStackTrace();
    }

  }



  /**
   * Safely set a feature on an XMLReader instance.
   *
   * @param xmlReader The reader to configure.
   * @param feature The feature string to set.
   * @param flag The value to set the feature to.
   */
  private void safeSetFeature(XMLReader xmlReader, String feature, boolean flag)
  {
    try
    {
      xmlReader.setFeature(feature, flag);
    }
    catch (SAXNotSupportedException e)
    {
      if (logFeatureWarnings.compareAndSet(false, true))
      {
        Logger.getLogger(PKG_NAME).log(Level.SEVERE, "XMLReader unsupported feature " + feature);
      }
    }
    catch (SAXNotRecognizedException e)
    {
      if (logFeatureWarnings.compareAndSet(false, true))
      {
        Logger.getLogger(PKG_NAME).log(Level.SEVERE, "XMLReader unrecognized feature " + feature);
      }
    }
  }



  /**
   * Returns an error response after a parsing error. The response has the
   * requestID of the batch request, the error response message of the parsing
   * exception message and the type 'malformed request'.
   *
   * @param is the XML InputStream to parse
   * @param objFactory the object factory
   * @param batchResponse the JAXB object to fill in
   * @param parserErrorMessage the parsing error message
   *
   * @return a JAXBElement that contains an ErrorResponse
   */
  private JAXBElement<ErrorResponse> createXMLParsingErrorResponse(
                                                    InputStream is,
                                                    ObjectFactory objFactory,
                                                    BatchResponse batchResponse,
                                                    String parserErrorMessage) {
    ErrorResponse errorResponse = objFactory.createErrorResponse();
    DSMLContentHandler contentHandler = new DSMLContentHandler();

    try
    {
      // try alternative XML parsing using SAX to retrieve requestID value
      final XMLReader xmlReader = createSafeXMLReader();
      xmlReader.setContentHandler(contentHandler);
      is.reset();

      xmlReader.parse(new InputSource(is));
    }
    catch (ParserConfigurationException | SAXException | IOException e)
    {
      // ignore
    }
    if ( parserErrorMessage!= null ) {
      errorResponse.setMessage(parserErrorMessage);
    }
    batchResponse.setRequestID(contentHandler.requestID);

    errorResponse.setType(MALFORMED_REQUEST);

    return objFactory.createBatchResponseErrorResponse(errorResponse);
  }

  /**
   * Returns an error response with attributes set according to the exception
   * provided as argument.
   *
   * @param objFactory the object factory
   * @param t the exception that occurred
   *
   * @return a JAXBElement that contains an ErrorResponse
   */
  private JAXBElement<ErrorResponse> createErrorResponse(ObjectFactory objFactory, Throwable t) {
    // potential exceptions are IOException, LDAPException, DecodeException

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
          errorResponse.setType(MALFORMED_REQUEST);
          break;
      }
    } else if ( t instanceof LDAPConnectionException ) {
      errorResponse.setType(COULD_NOT_CONNECT);
    } else if ( t instanceof IOException ) {
      errorResponse.setType(UNRESOLVABLE_URI);
    } else {
      errorResponse.setType(GATEWAY_INTERNAL_ERROR);
    }

    return objFactory.createBatchResponseErrorResponse(errorResponse);
  }

  /**
   * Performs the LDAP operation and sends back the result (if any). In case
   * of error, an error response is returned.
   *
   * @param connection a connected connection
   * @param objFactory the object factory
   * @param proxyAuthzControl a proxy authz control, or null
   * @param request the JAXB request to perform
   *
   * @return null for an abandon request, the expect result for all other
   *         requests or an error in case of unexpected behaviour.
   */
  private JAXBElement<?> performLDAPRequest(LDAPConnection connection,
                                            ObjectFactory objFactory,
                                            org.opends.server.types.Control proxyAuthzControl,
                                            DsmlMessage request) {
    ArrayList<org.opends.server.types.Control> controls = new ArrayList<>(1);
    if (proxyAuthzControl != null)
    {
      controls.add(proxyAuthzControl);
    }
    try {
      if (request instanceof SearchRequest) {
        // Process the search request.
        SearchRequest sr = (SearchRequest) request;
        DSMLSearchOperation ds = new DSMLSearchOperation(connection);
        SearchResponse searchResponse = ds.doSearch(objFactory, sr, controls);
        return objFactory.createBatchResponseSearchResponse(searchResponse);
      } else if (request instanceof AddRequest) {
        // Process the add request.
        AddRequest ar = (AddRequest) request;
        DSMLAddOperation addOp = new DSMLAddOperation(connection);
        LDAPResult addResponse = addOp.doOperation(objFactory, ar, controls);
        return objFactory.createBatchResponseAddResponse(addResponse);
      } else if (request instanceof AbandonRequest) {
        // Process the abandon request.
        AbandonRequest ar = (AbandonRequest) request;
        DSMLAbandonOperation ao = new DSMLAbandonOperation(connection);
        ao.doOperation(objFactory, ar, controls);
        return null;
      } else if (request instanceof ExtendedRequest) {
        // Process the extended request.
        ExtendedRequest er = (ExtendedRequest) request;
        DSMLExtendedOperation eo = new DSMLExtendedOperation(connection,
            exopStrings);
        ExtendedResponse extendedResponse = eo.doOperation(objFactory, er,
            controls);
        return objFactory.createBatchResponseExtendedResponse(extendedResponse);

      } else if (request instanceof DelRequest) {
        // Process the delete request.
        DelRequest dr = (DelRequest) request;
        DSMLDeleteOperation delOp = new DSMLDeleteOperation(connection);
        LDAPResult delResponse = delOp.doOperation(objFactory, dr, controls);
        return objFactory.createBatchResponseDelResponse(delResponse);
      } else if (request instanceof CompareRequest) {
        // Process the compare request.
        CompareRequest cr = (CompareRequest) request;
        DSMLCompareOperation compareOp =
                new DSMLCompareOperation(connection);
        LDAPResult compareResponse = compareOp.doOperation(objFactory, cr,
            controls);
        return objFactory.createBatchResponseCompareResponse(compareResponse);
      } else if (request instanceof ModifyDNRequest) {
        // Process the Modify DN request.
        ModifyDNRequest mr = (ModifyDNRequest) request;
        DSMLModifyDNOperation moddnOp =
                new DSMLModifyDNOperation(connection);
        LDAPResult moddnResponse = moddnOp.doOperation(objFactory, mr,
            controls);
        return objFactory.createBatchResponseModDNResponse(moddnResponse);
      } else if (request instanceof ModifyRequest) {
        // Process the Modify request.
        ModifyRequest modr = (ModifyRequest) request;
        DSMLModifyOperation modOp = new DSMLModifyOperation(connection);
        LDAPResult modResponse = modOp.doOperation(objFactory, modr, controls);
        return objFactory.createBatchResponseModifyResponse(modResponse);
      } else if (request instanceof AuthRequest) {
        // Process the Auth request.
        // Only returns an BatchResponse with an AuthResponse containing the
        // LDAP result code AUTH_METHOD_NOT_SUPPORTED
        ResultCode resultCode = objFactory.createResultCode();
        resultCode.setCode(LDAPResultCode.AUTH_METHOD_NOT_SUPPORTED);

        LDAPResult ldapResult = objFactory.createLDAPResult();
        ldapResult.setResultCode(resultCode);

        return objFactory.createBatchResponseAuthResponse(ldapResult);
      }
    } catch (Throwable t) {
      return createErrorResponse(objFactory, t);
    }
    // should never happen as the schema was validated
    return null;
  }


  /**
   * Send a response back to the client. This could be either a SOAP fault
   * or a correct DSML response.
   *
   * @param doc   The document to include in the response.
   * @param messageFactory  The SOAP message factory.
   * @param contentType  The MIME content type to send appropriate for the MessageFactory
   * @param res   Information about the HTTP response to the client.
   *
   * @throws IOException   If an error occurs while interacting with the client.
   * @throws SOAPException If an encoding or decoding error occurs.
   */
  private void sendResponse(Document doc, MessageFactory messageFactory, String contentType, HttpServletResponse res)
    throws IOException, SOAPException {

    SOAPMessage reply = messageFactory.createMessage();
    SOAPHeader header = reply.getSOAPHeader();
    header.detachNode();
    SOAPBody replyBody = reply.getSOAPBody();

    res.setHeader("Content-Type", contentType);

    replyBody.addDocument(doc);

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
   * Safely set a feature on an DocumentBuilderFactory instance.
   *
   * @param factory The DocumentBuilderFactory to configure.
   * @param feature The feature string to set.
   * @param flag The value to set the feature to.
   */
  private void safeSetFeature(DocumentBuilderFactory factory, String feature, boolean flag)
  {
    try
    {
      factory.setFeature(feature, flag);
    }
    catch (ParserConfigurationException e) {
      if (logFeatureWarnings.compareAndSet(false, true))
      {
        Logger.getLogger(PKG_NAME).log(Level.SEVERE, "DocumentBuilderFactory unsupported feature " + feature);
      }
    }
  }

  /**
   * Create a Document object that is safe against XML External Entity (XXE) Processing
   * attacks.
   *
   * @return A Document object
   * @throws ServletException if a Document object could not be created.
   */
  private Document createSafeDocument()
          throws ServletException
  {
    final DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
    try
    {
      dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
    }
    catch (ParserConfigurationException e)
    {
      if (logFeatureWarnings.compareAndSet(false, true)) {
        Logger.getLogger(PKG_NAME).log(Level.SEVERE, "DocumentBuilderFactory cannot be configured securely");
      }
    }
    dbf.setXIncludeAware(false);
    dbf.setNamespaceAware(true);
    dbf.setValidating(true);
    safeSetFeature(dbf, "http://apache.org/xml/features/disallow-doctype-decl", true);
    safeSetFeature(dbf, "http://xml.org/sax/features/external-general-entities", false);
    safeSetFeature(dbf, "http://xml.org/sax/features/external-parameter-entities", false);
    dbf.setExpandEntityReferences(false);

    final DocumentBuilder db;
    try
    {
      db = dbf.newDocumentBuilder();
    }
    catch (ParserConfigurationException e)
    {
      throw new ServletException(e.getMessage());
    }
    db.setEntityResolver(new SafeEntityResolver());
    return db.newDocument();

  }

  /**
   * Create an XMLReader that is safe against XML External Entity (XXE) Processing attacks.
   *
   * @return an XMLReader
   * @throws ParserConfigurationException if we cannot obtain a parser.
   * @throws SAXException if we cannot obtain a parser.
   */
  private XMLReader createSafeXMLReader()
          throws ParserConfigurationException, SAXException
  {
    final SAXParserFactory saxParserFactory = SAXParserFactory.newInstance();
    // Ensure we are doing basic secure processing.
    saxParserFactory.setXIncludeAware(false);
    saxParserFactory.setNamespaceAware(true);
    saxParserFactory.setValidating(false);

    // Configure a safe XMLReader appropriate for SOAP.
    final XMLReader xmlReader = saxParserFactory.newSAXParser().getXMLReader();
    safeSetFeature(xmlReader, XMLConstants.FEATURE_SECURE_PROCESSING, true);
    safeSetFeature(xmlReader, "http://apache.org/xml/features/disallow-doctype-decl", true);
    safeSetFeature(xmlReader, "http://xml.org/sax/features/external-general-entities", false);
    safeSetFeature(xmlReader, "http://xml.org/sax/features/external-parameter-entities", false);
    xmlReader.setEntityResolver(new SafeEntityResolver());
    return xmlReader;
  }

  /**
   * This class is used when an XML request is malformed to retrieve the
   * requestID value using an event XML parser.
   */
  private class DSMLContentHandler extends DefaultHandler {
    private String requestID;
    /**
     * This function fetches the requestID value of the batchRequest xml
     * element and call the default implementation (super).
     */
    @Override
    public void startElement(String uri, String localName, String qName,
                             Attributes attributes) throws SAXException {
      if ( requestID==null && localName.equals("batchRequest") ) {
        requestID = attributes.getValue("requestID");
      }
      super.startElement(uri, localName, qName, attributes);
    }
  }

  /**
   * This is defensive - we prevent entity resolving by configuration, but
   * just in case, we ensure that nothing resolves.
   */
  private class SafeEntityResolver implements EntityResolver
  {
    @Override
    public InputSource resolveEntity(String publicId, String systemId)
    {
      return new InputSource(new StringReader(""));
    }
  }
}

