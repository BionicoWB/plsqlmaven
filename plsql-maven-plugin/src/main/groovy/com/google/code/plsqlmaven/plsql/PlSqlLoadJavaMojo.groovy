package com.google.code.plsqlmaven.plsql

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

import groovy.sql.Sql

/**
 * Loads dependencies jar, java classes and resources of the current java 
 * project to the current database connection, the user should 
 * have the JAVAUSERPRIV role granted. 
 * 
 * @goal loadjava
 *
 * @requiresDependencyResolution compile
 */
public class PlSqlLoadJavaMojo
extends PlSqlMojo
{
   /**
    * Whether to skip loading of classes (you now you have already loaded all off
    * them in the create$java$lob$table table)
    * @since 1.11
    * @parameter expression="${skipLoad}"
    */
    private boolean skipLoad;

   /**
    * Whether to skip classes creation (you now you have already created all off them)
    * @since 1.11
    * @parameter expression="${skipCreate}"
    */
    private boolean skipCreate;

   /**
    * Whether to skip classes resolution (you want them to be resolved later or at runtime)
    * @since 1.11
    * @parameter expression="${skipResolve}"
    */
    private boolean skipResolve;

   /**
    * Whether to skip classes and load resources only
    * @since 1.11
    * @parameter expression="${skipClasses}"
    */
    private boolean skipClasses;

   /**
    * Whether to skip dependencies and load,compile,resolve project things only
    * @since 1.11
    * @parameter expression="${skipDependencies}"
    */
    private boolean skipDependencies;

   /**
    * Whether to skip resources and load classes only
    * @since 1.11
    * @parameter expression="${skipResources}"
    */
    private boolean skipResources;

    private toresolve= []

     void execute()
     {

           if (skipClasses) skipResolve= true;

           if (!connectToDatabase())
           {
               fail('Need an Oracle connection');
               return;
           }

           createJavaLobTable()

           if (!skipDependencies) loadDependencies()

           loadDir(new File(project.build.outputDirectory))

           if (!skipResolve) resolve()

           disconnectFromDatabase()
     }

     private void createJavaLobTable()
     {
        if (!(sql.firstRow('select 1 table_exists from user_tables where table_name= \'CREATE$JAVA$LOB$TABLE\'')?.table_exists))
            sql.execute('create table create$java$lob$table (name varchar2(700 byte) unique, lob blob, loadtime date)'); 
     }

     private void loadDir(dir)
     {
            def ac= sql.connection.autoCommit
            sql.connection.autoCommit = false

            def scanner=  ant.fileScanner
            {
                fileset(dir: dir.absolutePath)
                {
                    include(name: "**/*.*")
                }
            }

            for (file in scanner)
            {
               def desc= getFileDesc(file,file.absolutePath.substring(dir.absolutePath.length()+1));

               if (desc.isClass)
               {
                  if (skipClasses) 
                     continue;
                  else
                     toresolve << desc
               }
               else
                  if (skipResources)
                    continue;

               if (!skipLoad)
               {
                   log.info("loading: ${desc.fullName}")

                   sql.call("""declare
                                   v_blob  blob;
                               begin 
                                   begin
                                       insert into create\$java\$lob\$table (name, lob, loadtime) 
                                                                  values (${desc.fullName}, empty_blob, sysdate) 
                                                               returning lob into v_blob;
                                   exception 
                                     when dup_val_on_index then
                                       update create\$java\$lob\$table
                                          set lob= empty_blob,
                                              loadtime= sysdate
                                        where name= ${desc.fullName}
                                    returning lob into v_blob;
                                   end;
                                   ${Sql.BLOB}:= v_blob; 
                               end;""",
                   {
                      blob ->

                      def fin= new FileInputStream(file)
                      def bout= blob.setBinaryStream(1)
                      bout << fin
                      bout.flush()
                      bout.close()
                      fin.close()
                      sql.connection.commit()
                 
                   })
               }

               if (!skipCreate)
               {
                  log.info("creating: ${desc.fullName}")

                  if (desc.isClass)
                    sql.execute("create or replace java class using '${desc.fullName}'".toString())
                  else
                    sql.execute("create or replace java resource named \"${desc.fullName}\" using '${desc.fullName}'".toString())
               }
            }

            sql.connection.autoCommit = ac
        

     }

     private void loadDependencies()
     {
        ant.delete(dir: new File(project.build.directory,"javadeps"))

        def artifacts= project.getArtifacts()

        ant.mkdir(dir: project.build.directory)
        def depsDir= new File(project.build.directory,"javadeps");
        ant.mkdir(dir: depsDir.absolutePath)

        def objects= []

        for (artifact in artifacts)
        {

            if (artifact.scope == artifact.SCOPE_PROVIDED) continue;

            def artifactDir= new File(depsDir,artifact.id)
            ant.mkdir(dir: artifactDir.absolutePath)
            ant.unzip(src: artifact.file.absolutePath, dest: artifactDir.absolutePath)
             
            loadDir(artifactDir)
        }

     }

     private resolve()
     {
        for (desc in toresolve)
            if (desc.isClass)
            {
                log.info("resolving: ${desc.fullName}")
                sql.execute("alter java class \"${desc.fullName}\" resolve".toString())
            }
     }

     private getFileDesc(file,relativePath)
     {
         def isClass= relativePath.endsWith(".class");
         def pos= relativePath.lastIndexOf(File.separator)
         def desc= [ name: (isClass ? file.getName().split(/\./)[0] : file.getName()), pkg: (pos>-1 ? relativePath.substring(0,pos).replaceAll((File.separator=='\\' ? '\\\\' : '/'),'.') : ''), file: file, isClass: isClass ]; 
         desc['fullName']= (isClass ? (desc.pkg ? desc.pkg+'.'+desc.name : desc.name) : relativePath)
         return desc
     }

}
