There are strange problems on Mac OS due to the MacRoman
charset used as java.nio.charset.Charset.defaultCharset

investigating a bit i found that if you don't set -Dfile.encoding=UTF-8
directly on the commandline of the very first java invocation
it will continue to get MacRoman from java.nio.charset.Charset.defaultCharset()
call.

This may be a problem for the plsql:gateway goal because you will see
wrong characters in place of multi-bytes ones.

The only solution i found to this is to put -Dfile.encoding=UTF-8
directly inside the mvn script like this:

```
exec "$JAVACMD" \
  -Dfile.encoding=UTF-8 \
  $MAVEN_OPTS \
  -classpath "${M2_HOME}"/boot/classworlds-*.jar \
  "-Dclassworlds.conf=${M2_HOME}/bin/m2.conf" \
  "-Dmaven.home=${M2_HOME}"  \
  ${CLASSWORLDS_LAUNCHER} $QUOTED_ARGS

```

(Note for groovy users: This is also true for groovy do the same in your startGroovy shell)