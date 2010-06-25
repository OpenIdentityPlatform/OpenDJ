HTTP1.1 200 OK
Date: Wed, 19 Aug 2009 08:28:06 GMT
Server: Apache-Coyote/1.1
Content-Type: text/xml
Connection: close


<SOAP-ENV:Envelope xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/">
  <SOAP-ENV:Body>
    <batchResponse xmlns="urn:oasis:names:tc:DSML:2:0:core" requestID="[attr] element has no child [value] element">
      <addResponse>
        <resultCode code="32"/>
        <errorMessage>Entry uid=abergin,ou=People,dc=siroe,dc=com cannot be added because its parent entry ou=People,dc=siroe,dc=com does not exist in the server
        </errorMessage>
      </addResponse>
    </batchResponse>
  </SOAP-ENV:Body>
</SOAP-ENV:Envelope>
