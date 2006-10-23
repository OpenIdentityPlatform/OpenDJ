#!/usr/bin/python

def write_ldaps_ldif_file(path, port):
    ldif_file = open(path + "/ldaps_port.ldif","w")

    ldif_file.write("dn: cn=LDAPS Connection Handler,cn=Connection Handlers,cn=config\n")
    ldif_file.write("objectclass: top\n")
    ldif_file.write("objectclass: ds-cfg-connection-handler\n")
    ldif_file.write("objectclass: ds-cfg-ldap-connection-handler\n")
    ldif_file.write("cn: LDAPS Connection Handler\n")
    ldif_file.write("ds-cfg-connection-handler-class: org.opends.server.protocols.ldap.LDAPConnectionHandler\n")
    ldif_file.write("ds-cfg-connection-handler-enabled: true\n")
    ldif_file.write("ds-cfg-listen-address: 0.0.0.0\n")
    
    ldif_file.write("ds-cfg-listen-port: ")
    ldif_file.write(port)
    ldif_file.write("\n")
    
    ldif_file.write("ds-cfg-allow-ldapv2: true\n")
    ldif_file.write("ds-cfg-keep-stats: true\n")
    ldif_file.write("ds-cfg-use-tcp-keepalive: true\n")
    ldif_file.write("ds-cfg-use-tcp-nodelay: true\n")
    ldif_file.write("ds-cfg-allow-tcp-reuse-address: true\n")
    ldif_file.write("ds-cfg-send-rejection-notice: true\n")
    ldif_file.write("ds-cfg-max-request-size: 5 mb\n")
    ldif_file.write("ds-cfg-num-request-handlers: 2\n")
    ldif_file.write("ds-cfg-allow-start-tls: false\n")
    ldif_file.write("ds-cfg-use-ssl: true\n")
    ldif_file.write("ds-cfg-ssl-client-auth-policy: optional\n")
    ldif_file.write("ds-cfg-ssl-cert-nickname: server-cert\n")

    ldif_file.close()


