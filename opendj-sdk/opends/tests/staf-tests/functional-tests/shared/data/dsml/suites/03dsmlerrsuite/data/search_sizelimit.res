HTTP/1.1 200 OK
X-Powered-By: Servlet/2.5
Server: Sun Java System Application Server 9.1
Content-Type: text/xml
Date: Wed, 28 Nov 2007 15:54:14 GMT
Connection: close

<SOAP-ENV:Envelope xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/">
<SOAP-ENV:Body>
<dsml:batchResponse xmlns:dsml="urn:oasis:names:tc:DSML:2:0:core" requestID="Optional attribute [sizeLimit] of [searchRequest] is not an integer">
<dsml:errorResponse type="malformedRequest">
<dsml:message>
javax.xml.bind.UnmarshalException
 - with linked exception:
[org.xml.sax.SAXParseException: cvc-datatype-valid.1.2.1: 'de' is not a valid value for 'integer'.]</dsml:message>
</dsml:errorResponse>
</dsml:batchResponse>
</SOAP-ENV:Body>
</SOAP-ENV:Envelope>
