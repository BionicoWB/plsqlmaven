package com.google.code.plsqlmaven.oraddl

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

import groovy.xml.MarkupBuilder
import com.google.code.plsqlmaven.oraddl.helpers.DDLException


/**
 * Extracts schema objects to xml files
 *
 * @goal sync
 */
public class OraDdlSyncMojo
    extends OraDdlMojo
{
  /**
   * Sync only changed (from last sync) schema files
   * @since 1.0
   * @parameter property="changedOnly"
   */
   private boolean changedOnly;

  /**
   * Detect changes but don't apply it
   * @since 1.0
   * @parameter property="detectOnly"
   */
   private boolean detectOnly;

   /**
    * Location of the touch file.
    */
   private touchFile;

  /**
   * Last sync time.
   */
   private lastSyncTime= 0L;

   /**
    * Specify destination directory for synchronization's script
    * @default src/main/schema
    * @since 1.12
    * @parameter property="destDir"
    */
    private String destDir = "src/main/schema";

    /**
     * A comma separated list of types of objects to sync
     * @default table,index,sequence,synonym,view,materialized view
     * @since 1.12
     * @parameter property="types"
     */
     private String types= "table,index,sequence,synonym,view,materialized view";


   void execute()
   {
       if (!connectToDatabase())
       {
         fail('Need an Oracle connection')
         return
       }

       getLastSyncTime()
       syncObjects()
       touchReferenceFile()
       disconnectFromDatabase()
   }

   private syncObjects()
   {
       def typeList = types.split(',').collect({ it.toLowerCase() })

	   if (!new File(sourceDirectory).exists())
	   return;

       def scanner=  ant.fileScanner
       {
           fileset(dir: sourceDirectory)
           {
               include(name: '**/*.xml')
           }
       }

       def objects= [:]

       for (file in scanner)
       {
           if (changedOnly&&file.lastModified()<lastSyncTime) continue;
           def object= getSourceDescriptor(file)

           if(typeList.indexOf(object.type) >= 0)
           {
                if (!objects[object.type]) objects[object.type]= []

                objects[object.type] << object
           }
       }

       if (!schemaUtils.sync(objects, detectOnly, destDir))
           fail("DDL errors found")
   }

   private void getLastSyncTime()
   {
       touchFile= new File(project.build.directory,".schema")
       lastSyncTime= touchFile.lastModified();
       log.debug("touch file: ${touchFile.absolutePath}")
   }

   private void touchReferenceFile()
   {
       ant.mkdir(dir: project.build.directory)
       ant.touch(file: touchFile.getAbsolutePath())
   }

}
