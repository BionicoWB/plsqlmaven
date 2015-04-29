
```
$ mvn help:describe -Dplugin=oraddl -Ddetail
[INFO] Scanning for projects...
[INFO] Searching repository for plugin with prefix: 'help'.
[INFO] ------------------------------------------------------------------------
[INFO] Building Maven Default Project
[INFO]    task-segment: [help:describe] (aggregator-style)
[INFO] ------------------------------------------------------------------------
[INFO] [help:describe {execution: default-cli}]
[INFO] com.google.code.plsqlmaven:oraddl-maven-plugin:1.11-SNAPSHOT

Name: Oracle DDL Maven Mojo
Description: This project contains tries to bring maven development to PL/SQL
  developers
Group Id: com.google.code.plsqlmaven
Artifact Id: oraddl-maven-plugin
Version: 1.11-SNAPSHOT
Goal Prefix: oraddl

This plugin has 7 goals:

oraddl:compare
  Description: Compare two different project trees and produce the DDL to
    bring the compared tree to the current one
  Implementation: com.google.code.plsqlmaven.oraddl.OraDdlCompareMojo
  Language: java

  Available parameters:

    password
      Database password.

    to
      Maven project (directory) to compare with the current project

    url
      Database URL.

    username
      Database username.

oraddl:drop-removed
  Description: Compare two different project trees and produce the DDL to
    drop objects found in the current project and not in the -Dto project
  Implementation: com.google.code.plsqlmaven.oraddl.OraDdlDropRemovedMojo
  Language: java

  Available parameters:

    password
      Database password.

    to
      Maven project (directory) to compare with the current project

    url
      Database URL.

    username
      Database username.

oraddl:extract
  Description: Extracts schema objects to xml files
  Implementation: com.google.code.plsqlmaven.oraddl.OraDdlExtractMojo
  Language: java

  Available parameters:

    exclude
      Exclude this objects from the extraction (comma separated list of Oracle
      regular expressions for REGEXP_LIKE operator)

    existing
      Whether to extract objects already in the project

    force
      Whether to force extraction even if the sources directory already exists

    objects
      A comma separated list of object names to extract

    password
      Database password.

    types
      A comma separated list of types (table,sequence... etc) of objects to
      extract

    url
      Database URL.

    username
      Database username.

oraddl:extract-remaining
  Description: Creates a directory target/remaining which contains all the
    objects not present in the current project (and sub-modules), but present
    in the current connection schema.
  Implementation: com.google.code.plsqlmaven.oraddl.OraDdlExtractRemainingMojo
  Language: java

  Available parameters:

    exclude
      Exclude this objects from the extraction (comma separated list of Oracle
      regular expressions for REGEXP_LIKE operator)

    password
      Database password.

    types
      A comma separated list of types (table,sequence... etc) of objects to
      extract

    url
      Database URL.

    username
      Database username.

oraddl:package
  Description: Packages schema objects
  Implementation: com.google.code.plsqlmaven.oraddl.OraDdlPackageMojo
  Language: java

  Available parameters:

    password
      Database password.

    url
      Database URL.

    username
      Database username.

oraddl:remove-obsolete
  Description: Remove files of objects that cannot be found on the current
    connected schema
  Implementation: com.google.code.plsqlmaven.oraddl.OraDdlRemoveObsoleteMojo
  Language: java

  Available parameters:

    force
      Do delete files

    password
      Database password.

    url
      Database URL.

    username
      Database username.

oraddl:sync
  Description: Extracts schema objects to xml files
  Implementation: com.google.code.plsqlmaven.oraddl.OraDdlSyncMojo
  Language: java

  Available parameters:

    changedOnly
      Sync only changed (from last sync) schema files

    detectOnly
      Detect changes but don't apply it

    password
      Database password.

    url
      Database URL.

    username
      Database username.


[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESSFUL
[INFO] ------------------------------------------------------------------------
[INFO] Total time: 4 seconds
[INFO] Finished at: Thu Feb 02 22:19:43 CET 2012
[INFO] Final Memory: 6M/16M
[INFO] ------------------------------------------------------------------------
```