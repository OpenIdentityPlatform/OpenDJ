#!/usr/bin/python

def write_ldaps_ldif_file(path, port):
    ldif_file = open(path + "/ldaps_port.ldif","w")

    ldif_file.write("dn: cn=LDAPS Connection Handler,cn=Connection Handlers,cn=config\n")
    ldif_file.write("changetype: modify\n")
    ldif_file.write("replace: ds-cfg-listen-port\n")
    ldif_file.write("ds-cfg-listen-port: ")
    ldif_file.write(port)
    ldif_file.write("\n")
    ldif_file.write("-\n")
    ldif_file.write("replace: ds-cfg-connection-handler-enabled\n")
    ldif_file.write("ds-cfg-connection-handler-enabled: true\n")
    ldif_file.write("-\n")
    ldif_file.write("replace: ds-cfg-ssl-cert-nickname\n")
    ldif_file.write("ds-cfg-ssl-cert-nickname: server-cert\n")
     
    ldif_file.close()


