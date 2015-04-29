
```
$ mvn help:describe -Dplugin=xdb -Ddetail
[INFO] Scanning for projects...
[INFO] Searching repository for plugin with prefix: 'help'.
[INFO] ------------------------------------------------------------------------
[INFO] Building Maven Default Project
[INFO]    task-segment: [help:describe] (aggregator-style)
[INFO] ------------------------------------------------------------------------
[INFO] [help:describe {execution: default-cli}]
[INFO] com.google.code.plsqlmaven:xdb-maven-plugin:1.11-SNAPSHOT

Name: XDB Maven Mojo
Description: This project contains tries to bring maven development to PL/SQL
  developers
Group Id: com.google.code.plsqlmaven
Artifact Id: xdb-maven-plugin
Version: 1.11-SNAPSHOT
Goal Prefix: xdb

This plugin has 3 goals:

xdb:export
  Description: Extracts XDB files from the database to filesystem
  Implementation: com.google.code.plsqlmaven.xdb.XdbExportMojo
  Language: java

  Available parameters:

    basePath
      The base path from witch the export should start

    dirPaths
      A comma separated list of xdb directory paths relative to the basePath to
      export use instead of filePaths to export entire subdirectories

    filePaths
      A comma separated list of xdb paths relative to the basePath to export

    force
      Whether to force export even if the local file exists

    password
      Database password.

    translateEntities
      A list of comma separated file extensions to enable html entity
      translation

    url
      Database URL.

    username
      Database username.

xdb:import
  Description: Import files to XDB
  Implementation: com.google.code.plsqlmaven.xdb.XdbImportMojo
  Language: java

  Available parameters:

    basePath
      The base path from witch the export should start

    changedOnly
      Whether to import only files changed from last import

    loop
      Whether to loop waiting for changes, expressend in seconds between loops

    password
      Database password.

    translateEntities
      A list of comma separated file extensions to enable html entity
      translation

    url
      Database URL.

    username
      Database username.

xdb:package
  Description: Packages the PL/SQL artifact
  Implementation: com.google.code.plsqlmaven.xdb.XdbPackageMojo
  Language: java
  Bound to phase: prepare-package

  Available parameters:

    basePath
      The base path from witch the export should start

    password
      Database password.

    translateEntities
      A list of comma separated file extensions to enable html entity
      translation

    url
      Database URL.

    username
      Database username.


[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESSFUL
[INFO] ------------------------------------------------------------------------
[INFO] Total time: 2 seconds
[INFO] Finished at: Thu Feb 02 22:20:37 CET 2012
[INFO] Final Memory: 6M/16M
[INFO] ------------------------------------------------------------------------
```