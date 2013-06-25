HTTP/1.1 200 OK
CONTENT-TYPE: text/xml
CONNECTION: close
SERVER: Sun Java System Application Server 9.1
X-POWERED-BY: Servlet/2.5
DATE: Fri, 07 Dec 2007 09:11:20 GMT

<SOAP-ENV:Envelope xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/">
<SOAP-ENV:Body>
<dsml:batchResponse xmlns:dsml="urn:oasis:names:tc:DSML:2:0:core">
<dsml:errorResponse type="couldNotConnect">
<dsml:message>
org.opends.server.tools.LDAPConnectionException: The simple bind attempt failed</dsml:message>
</dsml:errorResponse>
</dsml:batchResponse>
</SOAP-ENV:Body>
</SOAP-ENV:Envelope>
