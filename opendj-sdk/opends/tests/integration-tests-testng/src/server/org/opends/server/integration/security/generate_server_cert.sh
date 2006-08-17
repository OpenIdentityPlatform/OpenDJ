#!/bin/sh

keytool -genkey -alias server-cert -keyalg rsa -dname "cn=client,O=Sun Microsystems,C=US" -keystore "keystore" -storepass "servercert" -keypass "servercert" 

keytool -selfcert -alias server-cert -keystore "keystore" -storepass "servercert" 

