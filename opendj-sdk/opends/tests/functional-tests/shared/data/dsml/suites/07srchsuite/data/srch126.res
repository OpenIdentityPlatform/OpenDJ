HTTP/1.1 200 OK
Cache-control: no-cache
Date: Mon, 01 Jan 2001 12:00:00 GMT
Accept-Ranges: none
Server: Sun-Java(tm)-System-Directory/6.2
Content-Type: text/xml; charset="utf-8"
Content-Length: 2260

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
   <searchResultEntry dn='cn=Peter Smith, ou=Recruiting, ou=HR, ou=Europe, ou=Search, o=IMC, c=US'>
      <attr name='cn'>
      <value>Peter Smith</value>
      </attr>
      <attr name='sn'>
      <value>Smith</value>
      </attr>
      <attr name='givenName'>
      <value>Peter</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalperson</value>
      <value>inetorgperson</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 720 0013</value>
      </attr>
      <attr name='mail'>
      <value>Peter.Smith@dirconnect2.org</value>
      </attr>
      <attr name='title'>
      <value>Director</value>
      </attr>
      <attr name='employeeNumber'>
      <value>2200013</value>
      </attr>
   </searchResultEntry>
   <searchResultEntry dn='cn=Paulette Smith, ou=Sales, ou=Europe, ou=Search, o=IMC, c=US'>
      <attr name='cn'>
      <value>Paulette Smith</value>
      </attr>
      <attr name='sn'>
      <value>Smith</value>
      </attr>
      <attr name='givenName'>
      <value>Paulette</value>
      </attr>
      <attr name='objectClass'>
      <value>top</value>
      <value>person</value>
      <value>organizationalperson</value>
      <value>inetorgperson</value>
      </attr>
      <attr name='telephoneNumber'>
      <value>+1 408 720 0014</value>
      </attr>
      <attr name='mail'>
      <value>Paulette.Smith@dirconnect2.org</value>
      </attr>
      <attr name='title'>
      <value>VP</value>
      </attr>
      <attr name='employeeNumber'>
      <value>2200014</value>
      </attr>
   </searchResultEntry>
   <searchResultDone>
      <resultCode code='0' descr='success'/>
   </searchResultDone>
   </searchResponse>
</batchResponse>
</soap-env:Body>
</soap-env:Envelope>
