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

"""Run a command until an absolute UTC deadline, including its child processes."""

import os
import signal
import subprocess
import sys
import time


def main(argv):
    if len(argv) < 3:
        print('usage: scale-deadline.py <epoch milliseconds> <command> [arguments...]', file=sys.stderr)
        return 2
    process = None

    def stop(signum, _frame):
        if process is not None:
            try:
                os.killpg(process.pid, signal.SIGKILL)
            except ProcessLookupError:
                pass
            process.wait()
        raise SystemExit(128 + signum)

    signal.signal(signal.SIGTERM, stop)
    signal.signal(signal.SIGINT, stop)
    try:
        remaining = int(argv[1]) / 1000 - time.time()
        if remaining <= 0:
            return 124
        process = subprocess.Popen(argv[2:], start_new_session=True)
        try:
            code = process.wait(timeout=remaining)
            return code if code >= 0 else 128 - code
        except subprocess.TimeoutExpired:
            try:
                os.killpg(process.pid, signal.SIGKILL)
            except ProcessLookupError:
                pass
            process.wait()
            return 124
    except (OSError, ValueError) as error:
        print('cannot run before deadline: %s' % error, file=sys.stderr)
        return 2


if __name__ == '__main__':
    sys.exit(main(sys.argv))
