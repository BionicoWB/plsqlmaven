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

/**
 * Extracts schema objects to xml files
 *
 * @goal extract
 */
public class OraDdlExtractMojo
    extends OraDdlMojo
{
    /**
    * A comma separated list of object names to extract
    * @since 1.0
    * @parameter expression="${objects}"
    */
   private String objects;

  /**
   * Whether to force extraction even if the sources directory already exists
   * @since 1.0
   * @parameter expression="${force}"
   */
   private boolean force;

   void execute()
   {
       if (checkSourceDirectory())
       {
           if (!connectToDatabase())
           {
             fail('Need an Oracle connection')
             return
           }
             
           createSources()
           disconnectFromDatabase()
       }
   }
   
   private checkSourceDirectory()
   {
       def continueExtraction= (force||!new File(sourceDirectory).exists())
       
       if (continueExtraction)
         ant.mkdir(dir: sourceDirectory)
       else
         fail('BE CAREFUL: The source directory exists... Remove it to regenerate schema files')
         
       return (continueExtraction);
   }

   private createSources()
   {
       def objectsFilter= '';
       
       if (objects)
         objectsFilter= " and object_name in ('"+objects.split(',').collect({ it.toUpperCase() }).join("','")+"')"

       sql.eachRow("""select object_name, 
                             object_type
                        from user_objects
                       where object_type in ('SEQUENCE','TABLE','INDEX','SYNONYM') 
                         and object_name not like 'SYS\\_%' escape '\\'"""+objectsFilter)
       {
           def type= it.object_type.toLowerCase()
           def name= it.object_name.toLowerCase()
           def sourceFilePath= path("${sourceDirectory}/${type}/${name}.xml")
           ant.mkdir(dir: sourceFilePath.substring(0,sourceFilePath.lastIndexOf(File.separator)))
           ant.truncate(file: sourceFilePath)
           File file= new File(sourceFilePath)
           FileWriter writer= new FileWriter(file)
           writer.write('<?xml version="1.0" encoding="UTF-8"?>'+"\n")
           def xml = new MarkupBuilder(writer)
           xml.omitNullAttributes = true
           xml.doubleQuotes = true
    
           if (!schemaUtils.getHelper(type)?.extract(name,xml))
             file.delete()
           else
             log.info "extracted ${sourceFilePath}"
           
       }
   }
   
}
