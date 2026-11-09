#!/usr/bin/env python3
#
# The contents of this file are subject to the terms of the Common Development and
# Distribution License (the License). You may not use this file except in compliance with the
# License.
#
# You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
# specific language governing permission and limitations under the License.
#
# When distributing Covered Software, include this CDDL Header Notice in each file and include
# the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
# Header, with the fields enclosed by brackets [] replaced by your own identifying
# information: "Portions copyright [year] [name of copyright owner]".
#
# Copyright 2026 3A Systems, LLC.

import importlib.util
from pathlib import Path
import socket
import subprocess
import sys
import threading
import time
import unittest

sys.dont_write_bytecode = True
spec = importlib.util.spec_from_file_location('monitor', Path(__file__).with_name('scale-monitor.py'))
monitor = importlib.util.module_from_spec(spec)
spec.loader.exec_module(monitor)


def wrap(tag, value):
    return monitor.tlv(tag, value)


def msg(message_id, tag, value):
    return wrap(0x30, wrap(0x02, bytes([message_id])) + wrap(tag, value))


def ldap_result(code):
    return wrap(0x0a, bytes([code])) + wrap(0x04, b'') + wrap(0x04, b'')


def entry():
    attrs = []
    for name, value in [(b'domain-name', b'dc=example,dc=com'),
                        (b'replicationServer-database', b'42'),
                        (b'last-change', b'000001a1a1e836e4002a00000001')]:
        attrs.append(wrap(0x30, wrap(0x04, name) + wrap(0x31, wrap(0x04, value))))
    return wrap(0x04, b'cn=example,cn=monitor') + wrap(0x30, b''.join(attrs))


def member_entry(dn, attributes):
    attrs = [wrap(0x30, wrap(0x04, name) + wrap(0x31, b''.join(wrap(0x04, v) for v in values)))
             for name, values in attributes.items()]
    return wrap(0x04, dn) + wrap(0x30, b''.join(attrs))


class MonitorTest(unittest.TestCase):
    def exchange(self, responses, timeout=3):
        client, server = socket.socketpair()
        errors = []
        def respond():
            try:
                monitor.read_frame(server, time.monotonic() + 3)
                server.sendall(responses[0])
                if len(responses) > 1:
                    monitor.read_frame(server, time.monotonic() + 3)
                    for data in responses[1:]:
                        for i in range(0, len(data), 3):
                            server.sendall(data[i:i + 3])
            except (BrokenPipeError, ConnectionResetError):
                pass
            except Exception as error:
                errors.append(error)
            finally:
                server.close()
        thread = threading.Thread(target=respond)
        thread.start()
        try:
            return monitor.search(client, b'password', time.monotonic() + timeout)
        finally:
            client.close()
            thread.join(4)
            if errors:
                raise errors[0]

    def test_search_collects_fragmented_entries_after_success(self):
        text = self.exchange([msg(1, 0x61, ldap_result(0)), msg(2, 0x64, entry()),
                              msg(2, 0x65, ldap_result(0))])
        self.assertIn('replicationServer-database:: NDI=', text)
        self.assertIn('dn:: ', text)
        self.assertTrue(text.endswith('\n\n'))

    def test_rejects_bind_failure(self):
        with self.assertRaises(monitor.LdapError):
            self.exchange([msg(1, 0x61, ldap_result(49))])

    def test_rejects_incomplete_search_even_after_entries(self):
        with self.assertRaises(monitor.LdapError):
            self.exchange([msg(1, 0x61, ldap_result(0)), msg(2, 0x64, entry()),
                           msg(2, 0x65, ldap_result(3))])

    def test_rejects_wrong_message_id(self):
        with self.assertRaises(monitor.LdapError):
            self.exchange([msg(7, 0x61, ldap_result(0))])

    def test_rejects_invalid_lengths_and_truncated_messages(self):
        for value in (b'\x30\x80', b'\x30\x82\xff\xff', b'\x30\x05\x02'):
            with self.subTest(value=value):
                client, server = socket.socketpair()
                server.sendall(value); server.close()
                try:
                    with self.assertRaises((monitor.LdapError, EOFError)):
                        monitor.read_frame(client, time.monotonic() + 1)
                finally:
                    client.close()

    def test_refuses_oversized_snapshot_without_returning_partial_data(self):
        original = monitor.MAX_SNAPSHOT
        monitor.MAX_SNAPSHOT = 10
        try:
            with self.assertRaises(monitor.LdapError):
                self.exchange([msg(1, 0x61, ldap_result(0)), msg(2, 0x64, entry()),
                               msg(2, 0x65, ldap_result(0))])
        finally:
            monitor.MAX_SNAPSHOT = original

    def test_expired_load_deadline_returns_no_snapshot(self):
        result = subprocess.run([sys.executable, str(Path(__file__).with_name('scale-monitor.py')), '1', '1'],
                                input=b'password', stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        self.assertEqual(result.returncode, 124)
        self.assertEqual(result.stdout, b'')
        self.assertIn(b'timeout=load', result.stderr)

    def test_deadline_after_final_response_does_not_return_a_snapshot(self):
        original = monitor.require_success
        calls = []
        def delayed_result(value):
            calls.append(1)
            original(value)
            if len(calls) == 2:
                time.sleep(0.4)
        monitor.require_success = delayed_result
        try:
            with self.assertRaises(TimeoutError):
                self.exchange([msg(1, 0x61, ldap_result(0)), msg(2, 0x65, ldap_result(0))], timeout=0.3)
            self.assertEqual(len(calls), 2)
        finally:
            monitor.require_success = original

    def test_no_bind_is_sent_after_the_deadline(self):
        client, server = socket.socketpair()
        server.settimeout(0.05)
        try:
            with self.assertRaises(TimeoutError):
                monitor.search(client, b'password', time.monotonic() - 1)
            with self.assertRaises(socket.timeout):
                server.recv(1)
        finally:
            client.close(); server.close()

    def test_absolute_deadline_interrupts_wait(self):
        client, server = socket.socketpair()
        try:
            with self.assertRaises(TimeoutError):
                monitor.read_frame(client, time.monotonic() + 0.05)
        finally:
            client.close(); server.close()

    def membership_exchange(self, registry, config, last_result=0):
        client, server = socket.socketpair()
        requests, errors = [], []
        def respond():
            try:
                for message_id, entries in [(1, None), (2, registry), (3, config)]:
                    requests.append(monitor.read_frame(server, time.monotonic() + 3))
                    if entries is None:
                        server.sendall(msg(1, 0x61, ldap_result(0)))
                    else:
                        for value in entries:
                            server.sendall(msg(message_id, 0x64, value))
                        server.sendall(msg(message_id, 0x65, ldap_result(last_result if message_id == 3 else 0)))
            except (BrokenPipeError, ConnectionResetError):
                pass
            except Exception as error:
                errors.append(error)
            finally:
                server.close()
        thread = threading.Thread(target=respond); thread.start()
        try:
            result = monitor.membership(client, b'password', time.monotonic() + 3, 'cv-r1', 'absent')
            return result, requests
        finally:
            client.close(); thread.join(4)
            if errors: raise errors[0]

    def test_membership_uses_one_bind_and_two_complete_searches(self):
        registry = [member_entry(b'cn=d0,cn=Servers,cn=admin data', {b'hostname': [b'cv-d0.example']})]
        config = [member_entry(b'cn=domain,cn=config', {b'ds-cfg-replication-server': [b'cv-r10.example:8989']})]
        result, requests = self.membership_exchange(registry, config)
        self.assertTrue(result)
        self.assertEqual(len(requests), 3)
        self.assertIn(b'cn=Servers,cn=admin data', requests[1])
        self.assertIn(b'cn=config', requests[2])

    def test_membership_rejects_a_partial_second_search(self):
        registry = [member_entry(b'cn=d0', {b'hostname': [b'cv-d0']})]
        config = [member_entry(b'cn=domain', {})]
        with self.assertRaises(monitor.LdapError):
            self.membership_exchange(registry, config, last_result=3)

    def test_membership_semantics_for_all_lists(self):
        good_registry = [{'hostname': ['CV-R1.example']}]
        good_config = [{'ds-cfg-replication-server': ['cv-r1.example:8989']}]
        self.assertTrue(monitor.membership_matches(good_registry, good_config, 'cv-r1', 'present'))
        self.assertFalse(monitor.membership_matches(good_registry, good_config + [{}], 'cv-r1', 'present'))
        self.assertFalse(monitor.membership_matches(good_registry, good_config, 'cv-r1', 'absent'))
        self.assertFalse(monitor.membership_matches([], good_config, 'cv-r1', 'absent'))
        self.assertFalse(monitor.membership_matches([{}], good_config, 'cv-r1', 'absent'))
        self.assertFalse(monitor.membership_matches([{'hostname': ['']}], [{}], 'cv-r1', 'absent'))
        self.assertFalse(monitor.membership_matches(good_registry, [], 'cv-r1', 'present'))
        self.assertTrue(monitor.membership_matches([{'hostname': ['cv-d0']}], [{}], 'cv-r1', 'absent'))
        self.assertFalse(monitor.membership_matches([{'hostname': ['cv-r0']}], good_config, 'cv-r1', 'present'))
        self.assertFalse(monitor.membership_matches(good_registry, [{'ds-cfg-replication-server': ['cv-r0:8989']}], 'cv-r1', 'present'))
        self.assertFalse(monitor.membership_matches([{'hostname': ['cv-d0']}], good_config, 'cv-r1', 'absent'))


if __name__ == '__main__':
    unittest.main()
