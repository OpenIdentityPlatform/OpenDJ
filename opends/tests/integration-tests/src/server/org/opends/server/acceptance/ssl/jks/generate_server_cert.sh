#!/bin/sh

keytool -genkey -alias server-cert -keyalg rsa -dname "cn=server-cert,dc=com" -keystore "keystore" -storepass "servercert" -keypass "servercert" 

keytool -selfcert -alias server-cert -keystore "keystore" -storepass "servercert" 

