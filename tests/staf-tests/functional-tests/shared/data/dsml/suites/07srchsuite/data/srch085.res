HTTP/1.1 200 OK
Cache-control: no-cache
Date: Mon, 01 Jan 2001 12:00:00 GMT
Accept-Ranges: none
Server: Sun-Java(tm)-System-Directory/6.2
Content-Type: text/xml; charset="utf-8"
Content-Length: 1429

<?xml version='1.0' encoding='UTF-8' ?>
<soap-env:Envelope 
   xmlns:xsd='http://www.w3.org/2001/XMLSchema' 
   xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance' 
   xmlns:soap-env='http://schemas.xmlsoap.org/soap/envelope/' 
   >
<soap-env:Body>
<batchResponse 
   xmlns:xsd='http://www.w3.org/2001/XMLSchema' 
   xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance' 
   xmlns='urn:oasis:names:tc:DSML:2:0:core' 
   >
   <searchResponse>
   <searchResultEntry dn='cn=Jonathan Adams, ou=Europe, ou=Search, o=IMC, c=US'>
      <attr name='cn'>
      <value>Jonathan Adams</value>
      </attr>
      <attr name='sn'>
      <value>Adams</value>
      </attr>
      <attr name='givenName'>
      <value>Jonathan</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalperson</value>
      <value>inetorgperson</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 720 0000</value>
      </attr>
      <attr name='mail'>
      <value>Jonathan.Adams@dirconnect2.org</value>
      </attr>
      <attr name='title'>
      <value>President</value>
      </attr>
      <attr name='employeeNumber'>
      <value>2200000</value>
      </attr>
   </searchResultEntry>
   <searchResultDone>
      <resultCode code='0' descr='success'/>
   </searchResultDone>
   </searchResponse>
</batchResponse>
</soap-env:Body>
</soap-env:Envelope>
