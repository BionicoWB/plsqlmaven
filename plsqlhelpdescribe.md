
```
$ mvn help:describe -Dplugin=plsql -Ddetail

[INFO] Scanning for projects...
[INFO] Searching repository for plugin with prefix: 'help'.
[INFO] ------------------------------------------------------------------------
[INFO] Building Maven Default Project
[INFO]    task-segment: [help:describe] (aggregator-style)
[INFO] ------------------------------------------------------------------------
[INFO] [help:describe {execution: default-cli}]
[INFO] com.google.code.plsqlmaven:plsql-maven-plugin:1.11-SNAPSHOT

Name: PL/SQL Maven Mojo
Description: This project contains tries to bring maven development to PL/SQL
  developers
Group Id: com.google.code.plsqlmaven
Artifact Id: plsql-maven-plugin
Version: 1.11-SNAPSHOT
Goal Prefix: plsql

This plugin has 12 goals:

plsql:compile
  Description: Compile PL/SQL sources.
  Implementation: com.google.code.plsqlmaven.plsql.PlSqlCompileMojo
  Language: java
  Bound to phase: compile

  Available parameters:

    defaultPage (Default: home)
      Default procedure to invoke on dad access

    dropForceTypes
      Whether to use DROP TYPE mytype FORCE before type spec compilation

    loop
      Whether to loop waiting for changes, expressend in seconds between loops

    nativeComp
      Whether to compile code natively in C or not

    password
      Database password.

    plsqlSource
      The specific source file to compile

    sourceDir
      Specify source directory

    url
      Database URL.

    username
      Database username.

plsql:compile-dependencies
  Description: Compile PL/SQL dependencies sources.
  Implementation: com.google.code.plsqlmaven.plsql.PlSqlCompileDependenciesMojo
  Language: java
  Bound to phase: process-sources

  Available parameters:

    defaultPage (Default: home)
      Default procedure to invoke on dad access

    dropForceTypes
      Whether to use DROP TYPE mytype FORCE before type spec compilation

    loop
      Whether to loop waiting for changes, expressend in seconds between loops

    nativeComp
      Whether to compile code natively in C or not

    password
      Database password.

    plsqlSource
      The specific source file to compile

    sourceDir
      Specify source directory

    url
      Database URL.

    username
      Database username.

plsql:deploy
  Description: Deploy the contents of a PL/SQL jar archive into the given
    database connection
  Implementation: com.google.code.plsqlmaven.plsql.PlSqlDeployMojo
  Language: java

  Available parameters:

    defaultPage (Default: home)
      Default procedure to invoke on dad access

    dropForceTypes
      Whether to use DROP TYPE mytype FORCE before type spec compilation

    jarPath
      path to the jar to deploy

    password
      Database password.

    sourceDir
      Specify source directory

    url
      Database URL.

    username
      Database username.

plsql:drop-removed
  Description: Compare two different project trees and produce the DDL to
    drop objects found in the current project and not in the -Dto project
  Implementation: com.google.code.plsqlmaven.plsql.PlSqlDropRemovedMojo
  Language: java

  Available parameters:

    defaultPage (Default: home)
      Default procedure to invoke on dad access

    dropForceTypes
      Whether to use DROP TYPE mytype FORCE before type spec compilation

    password
      Database password.

    sourceDir
      Specify source directory

    to
      Maven project (directory) to compare with the current project

    url
      Database URL.

    username
      Database username.

plsql:extract
  Description: Extracts PL/SQL sources from the database to filesystem
  Implementation: com.google.code.plsqlmaven.plsql.PlSqlExtractMojo
  Language: java

  Available parameters:

    defaultPage (Default: home)
      Default procedure to invoke on dad access

    dropForceTypes
      Whether to use DROP TYPE mytype FORCE before type spec compilation

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

    sourceDir
      Specify source directory

    types
      A comma separated list of types (package,procedure... etc) of objects to
      extract

    url
      Database URL.

    username
      Database username.

plsql:extract-remaining
  Description: Creates a directory target/remaining which contains all the
    objects not present in the current project (and sub-modules), but present
    in the current connection schema.
  Implementation: com.google.code.plsqlmaven.plsql.PlsqlExtractRemainingMojo
  Language: java

  Available parameters:

    defaultPage (Default: home)
      Default procedure to invoke on dad access

    dropForceTypes
      Whether to use DROP TYPE mytype FORCE before type spec compilation

    exclude
      Exclude this objects from the extraction (comma separated list of Oracle
      regular expressions for REGEXP_LIKE operator)

    password
      Database password.

    sourceDir
      Specify source directory

    types
      A comma separated list of types (package,function... etc) of objects to
      extract

    url
      Database URL.

    username
      Database username.

plsql:gateway
  Description: Starts a java PL/SQL Gateway and allows to debug OWA apps
  Implementation: com.google.code.plsqlmaven.plsql.PlSqlGatewayMojo
  Language: java

  Available parameters:

    bindAddress
      Address to bind

    defaultPage (Default: home)
      Default procedure to invoke on dad access

    dropForceTypes
      Whether to use DROP TYPE mytype FORCE before type spec compilation

    password
      Database password.

    port
      Port to listen

    sourceDir
      Specify source directory

    url
      Database URL.

    username
      Database username.

    webappContext
      WebApp context path

    webappRoot
      WebApp root directory

plsql:loadjava
  Description: Loads dependencies jar and java classes of the current java
    project to the current database connection, the user should have the
    JAVAUSERPRIV role granted.
  Implementation: com.google.code.plsqlmaven.plsql.PlSqlLoadJavaMojo
  Language: java

  Available parameters:

    defaultPage (Default: home)
      Default procedure to invoke on dad access

    dropForceTypes
      Whether to use DROP TYPE mytype FORCE before type spec compilation

    password
      Database password.

    skipClasses
      Whether to skip classes and load resources only

    skipCreate
      Whether to skip classes creation (you now you have already created all
      off them)

    skipDependencies
      Whether to skip dependencies and load,compile,resolve project things only

    skipLoad
      Whether to skip loading of classes (you now you have already loaded all
      off them in the create$java$lob$table table)

    skipResolve
      Whether to skip classes resolution (you want them to be resolved later or
      at runtime)

    skipResources
      Whether to skip resources and load classes only

    sourceDir
      Specify source directory

    url
      Database URL.

    username
      Database username.

plsql:package
  Description: Packages the PL/SQL artifact
  Implementation: com.google.code.plsqlmaven.plsql.PlSqlPackageMojo
  Language: java
  Bound to phase: prepare-package

  Available parameters:

    defaultPage (Default: home)
      Default procedure to invoke on dad access

    dropForceTypes
      Whether to use DROP TYPE mytype FORCE before type spec compilation

    password
      Database password.

    sourceDir
      Specify source directory

    url
      Database URL.

    username
      Database username.

plsql:remove-obsolete
  Description: Remove files of objects that cannot be found on the current
    connected schema
  Implementation: com.google.code.plsqlmaven.plsql.PlSqlRemoveObsoleteMojo
  Language: java

  Available parameters:

    defaultPage (Default: home)
      Default procedure to invoke on dad access

    dropForceTypes
      Whether to use DROP TYPE mytype FORCE before type spec compilation

    force
      Do delete files

    password
      Database password.

    sourceDir
      Specify source directory

    url
      Database URL.

    username
      Database username.

plsql:sqlplus
  Description: Creates a SQL version of the artifact to be run with SQL*Plus.
  Implementation: com.google.code.plsqlmaven.plsql.PlSqlSqlPlusMojo
  Language: java

  Available parameters:

    defaultPage (Default: home)
      Default procedure to invoke on dad access

    dependencies
      Whether to include dependencies in the script or not

    dropForceTypes
      Whether to use DROP TYPE mytype FORCE before type spec compilation

    password
      Database password.

    sourceDir
      Specify source directory

    url
      Database URL.

    username
      Database username.

plsql:template
  Description: Quickstart goal to create PL/SQL source files
  Implementation: com.google.code.plsqlmaven.plsql.PlSqlTemplateMojo
  Language: java

  Available parameters:

    defaultPage (Default: home)
      Default procedure to invoke on dad access

    dropForceTypes
      Whether to use DROP TYPE mytype FORCE before type spec compilation

    name
      Name of the object to be created (eg myprc)

    password
      Database password.

    sourceDir
      Specify source directory

    type
      Type of the object to be created (eg function,procedure,package)

    url
      Database URL.

    username
      Database username.


[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESSFUL
[INFO] ------------------------------------------------------------------------
[INFO] Total time: 3 seconds
[INFO] Finished at: Thu Feb 02 22:06:43 CET 2012
[INFO] Final Memory: 7M/17M
[INFO] ------------------------------------------------------------------------
```