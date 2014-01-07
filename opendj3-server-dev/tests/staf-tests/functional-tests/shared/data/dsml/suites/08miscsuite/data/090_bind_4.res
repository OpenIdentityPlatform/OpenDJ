HTTP/1.1 200 OK
CONTENT-TYPE: text/xml
CONNECTION: close
SERVER: Sun Java System Application Server 9.1
X-POWERED-BY: Servlet/2.5
DATE: Wed, 05 Dec 2007 14:47:02 GMT

<SOAP-ENV:Envelope xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/">
<SOAP-ENV:Body>
<dsml:batchResponse xmlns:dsml="urn:oasis:names:tc:DSML:2:0:core">
<dsml:errorResponse type="authenticationFailed">
<dsml:message>
org.opends.server.types.LDAPException: The value cm9vdDpzZWNyZXQxMg= cannot be base64-decoded because it does not have a length that is a multiple of four bytes</dsml:message>
</dsml:errorResponse>
</dsml:batchResponse>
</SOAP-ENV:Body>
</SOAP-ENV:Envelope>

