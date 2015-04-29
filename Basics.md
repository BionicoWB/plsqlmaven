
```
<settings>
  <profiles>
           <profile>
             <id>mydb1</id>
             <properties>
                     <databaseUrl>jdbc:oracle:thin:@host:1521:mydb1</databaseUrl>
                     <databaseUser>xxxxx</databaseUser>
                     <databasePassword>xxxxxx</databasePassword>
             </properties>
           </profile>
           <profile>
             <id>mydb2</id>
             <properties>
                     <databaseUrl>jdbc:oracle:thin:@host:1521:mydb1</databaseUrl>
                     <databaseUser>xxxxx</databaseUser>
                     <databasePassword>xxxxxx</databasePassword>
             </properties>
           </profile>
  </profiles>  
  <pluginGroups>
    <pluginGroup>com.google.code.plsqlmaven</pluginGroup>
  </pluginGroups>
</settings>
```
  * install oracle JDBC driver ( http://www.oracle.com/technetwork/database/enterprise-edition/jdbc-112010-090769.html ):
```
$ mvn install:install-file -DgroupId=com.oracle -DartifactId=ojdbc6 -Dversion=11.2.0.2.0 -Dpackaging=jar -Dfile=ojdbc6.jar
```

  * sample project (the plugin will be downloaded automatically from maven central repository if you configured pluginGroups as shown)
```
$ mvn archetype:generate -DarchetypeGroupId=com.google.code.plsqlmaven -DarchetypeArtifactId=plsql-package-archetype -DarchetypeVersion=1.0 -DartifactId=dummy -DgroupId=org.myorg

$ cd dummy

$ mvn -Pmydb1 compile

$ mvn help:describe -Dplugin=plsql -Ddetail
or
$ mvn help:describe -DgroupId=com.google.code.plsqlmaven -DartifactId=plsql-maven-plugin
```

  * extract all plsql sources of a schema (not the best way to modularize your project)
```
$ mvn -Pmydb1 plsql:extract -Dforce
```


  * extract a couple of objects
```
$ mvn -Pmydb1 plsql:extract -Dobjects=mypkg1,myproc2 -Dforce
```