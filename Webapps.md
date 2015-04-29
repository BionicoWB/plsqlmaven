
```
$ cd myproj
$ mkdir src/main/webapp
```
  * start the PL/SQL gateway
```
$ mvn -Pmydb1 plsql:gateway
```
  * go to the URL it prints with your browser http://localhost:8080/myproj/pls/home and start your develop, refresh and scm commit cycle...
  * any file placed in the webapp directory eg. src/main/webapp/dummy.js is accessible from http://localhost:8080/myproj/dummy.js
  * when you are ready to release create the .war
```
$ mvn -Pmydb1 clean package
```
  * deploy it to your favourite J2EE container.