This is a plugin implemented in groovy that enables you to code in PL/SQL following the maven development lifecycle.

Actually you can:
  * create PL/SQL maven projects (even nested submodules with profile based activation)
  * extract sources from database to the project folder in a ton of different ways
  * use your favorite SCM (and text editor, or PL/SQL IDE)
  * compile sources to an Oracle schema
  * package them as jar or war (for plsql web applications)
  * or deploy them directly to a database or if you wish to your favorite J2EE container
  * manage XDB files inside your project (and SCM) and have it synced to the database

Schema (EXPERIMENTAL, does not support object renaiming yet be careful!):
  * extract the structure of your tables, indexes, sequences, synonyms etc... in pretty printed .xml files
  * version the structure of your schema with your favorite SCM
  * sync back an existing oracle schema to a version of your XML
  * compare two XML versions and get a SQL script to sync manually
  * and many other things like this


For PL/SQL webapps:
  * you have a maven-integrated PL/SQL Gateway so that you can develop webapps locally (es. http://localhost:8080/myprojectname/pls/myproc, http://localhost:8080/myprojectname/my.js ...etc)
  * then you can choose to package you app as a WAR and deploy it on a J2EE container or run it with a traditional mod\_plsql implementation. (it is completly transparent to you code, you are still using htp.p owa\_utils etc...)
  * if you package it like a .war you get some extra features: static resources like js,css, and images can be served by your J2EE container, you can implement full HTTP and REST applications.. (put,post,get,delete and have access to HTTP\_BODY), and your war contains ALL your app (plsql,js,css,images, even xdb files if you use it, and if you want to get fancy even .xml describing your tables, indexes, views, synonyms, etc...) so when you deploy it on the J2EE container it syncs your codebase in the database. This means you can happly tag your code on the SCM and produce a single file per tag to deploy...

Download?:
  * NO: use it... it is on maven central
  * get maven 2.2.1 (here http://maven.apache.org/download.html) and follow the wiki http://code.google.com/p/plsqlmaven/wiki/Basics
  * dev snapshot? (svn co http://plsqlmaven.googlecode.com/svn/trunk/ plsqlmaven; cd  plsqlmaven; mvn install)

Maven help:describe results
  * mvn help:describe -Dplugin=plsql -Ddetail ([wiki](http://code.google.com/p/plsqlmaven/wiki/plsqlhelpdescribe))
  * mvn help:describe -Dplugin=oraddl -Ddetail ([wiki](http://code.google.com/p/plsqlmaven/wiki/oraddlhelpdescribe))
  * mvn help:describe -Dplugin=xdb -Ddetail ([wiki](http://code.google.com/p/plsqlmaven/wiki/xdbhelpdescribe))



What we have to do (roadmap/wishlist):
  * provide support for loadjava (done [changeset](http://code.google.com/p/plsqlmaven/source/detail?r=166))
  * provide a good way to package and deploy PLSQL libraries (PAR [POC](http://code.google.com/p/plsqlmaven/wiki/parpoc) [project](https://github.com/aaaristo/par))
  * provide support for dependencies between artifacts
  * provide support for utplsql or pluto for unit testing
  * provide support for pldoc (or others?) for documentation

Enjoy!

This project make use of http://code.google.com/p/plsqlgateway/ to provide the PL/SQL Gateway interface

