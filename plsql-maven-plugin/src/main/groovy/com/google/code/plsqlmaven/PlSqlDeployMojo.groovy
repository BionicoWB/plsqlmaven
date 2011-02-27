package com.google.code.plsqlmaven;

/*
 * Copyright 2001-2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */




/**
 * Deploy the contents of a PL/SQL jar archive into the given
 * database connection
 */
public class PackageInstaller
extends PlSqlMojo
{
   /**
    * path to the jar to deploy
    * @since 1.0
    * @parameter expression="${jarPath}"
    * @required
    */
   private String jarPath;
     
     void execute()
     {
           if (!connectToDatabase())
           {
               fail('Need an Oracle connection');
               return;
           }
           
           deploy()
           
           disconnectFromDatabase()
     }
    
     public void deploy()
     {
         def rootDir= (System.getProperty("java.io.tmpdir")
                       +File.separator
                       +jarPath.substring(jarPath.lastIndexOf(File.separator)+1,
                                          jarPath.lastIndexOf('.'))
                       +'_'+Math.random())
                     
         ant.delete(dir: rootDir)
         ant.mkdir(dir: rootDir)
         ant.unzip(src: getJarPath(), dest: rootDir)
         
         
     }
     
}
