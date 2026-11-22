"""跑 gradle 并只打印关键行。

本机默认 java 是 1.8，而 AGP 8.13 需要 JDK 11+，故显式指定 JDK 18。
用法：python tools/gradle.py :app:testDebugUnitTest
"""

import os
import subprocess
import sys

ROOT = r'D:\workspace\dengY\DengYing'
KEY = ('BUILD', 'FAILED', 'e: ', 'tests completed')


def main() -> None:
    """按命令行参数执行 gradle 任务，过滤输出。"""
    tasks = sys.argv[1:] or [':app:compileDebugKotlin']
    env = dict(os.environ)
    env['JAVA_HOME'] = r'C:\Program Files\Java\jdk-18.0.2.1'
    env['PATH'] = r'C:\Program Files\Java\jdk-18.0.2.1\bin;' + env['PATH']
    proc = subprocess.run(
        [os.path.join(ROOT, 'gradlew.bat')] + tasks + ['--console=plain'],
        cwd=ROOT,
        capture_output=True,
        env=env,
    )
    raw = (proc.stdout or b'') + (proc.stderr or b'')
    out = raw.decode('utf-8', 'replace')
    print('rc', proc.returncode)
    for line in out.split('\n'):
        if any(k in line for k in KEY):
            print(line.rstrip())


if __name__ == '__main__':
    main()
