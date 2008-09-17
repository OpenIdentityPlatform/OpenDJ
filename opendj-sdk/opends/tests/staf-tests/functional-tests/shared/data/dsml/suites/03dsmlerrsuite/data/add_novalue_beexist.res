HTTP/1.1 200 OK
X-Powered-By: Servlet/2.5
Server: Sun Java System Application Server 9.1
Content-Type: text/xml
Date: Thu, 29 Nov 2007 15:18:31 GMT
Connection: close

<SOAP-ENV:Envelope xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/">
<SOAP-ENV:Body>
<dsml:batchResponse xmlns:dsml="urn:oasis:names:tc:DSML:2:0:core" requestID="[attr] element has no child [value] element">
<dsml:addResponse>
<dsml:resultCode code="65"/>
<dsml:errorMessage>
Entry uid=aubergine,ou=People,o=dsmlfe.com violates the Directory Server schema configuration because it includes attribute givenName without any values</dsml:errorMessage>
</dsml:addResponse>
</dsml:batchResponse>
</SOAP-ENV:Body>
</SOAP-ENV:Envelope>
