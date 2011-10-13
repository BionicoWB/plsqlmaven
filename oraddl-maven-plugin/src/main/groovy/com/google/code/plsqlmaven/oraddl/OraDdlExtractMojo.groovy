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
    * A comma separated list of types (table,sequence... etc) of objects to extract
    * @since 1.8
    * @parameter expression="${types}"
    */
   private String types;

  /**
   * Whether to force extraction even if the sources directory already exists
   * @since 1.0
   * @parameter expression="${force}"
   */
   private boolean force;

  /**
   * Whether to extract objects already in the project 
   * @since 1.8
   * @parameter expression="${existing}"
   */
   private boolean existing;

  /**
   * Exclude this objects from the extraction (comma separated list of
   * Oracle regular expressions for REGEXP_LIKE operator)
   * @since 1.9
   * @parameter expression="${exclude}"
   */
   private String exclude;

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
       def typeFilter= '';
       def existingFilter= '';
	   def excludeFilter= '';
	   
       if (objects)
         objectsFilter= " and object_name in ('"+objects.split(',').collect({ it.toUpperCase() }).join("','")+"')"

       if (types)
         typeFilter= " and object_type in ('"+types.split(',').collect({ it.toUpperCase() }).join("','")+"')"
         
       if (existing)
         existingFilter= buildExistingFilter()

       if (exclude)
         excludeFilter= buildExcludeFilter()
		 
       def objectsQuery="""select object_name, 
                                  object_type
                             from user_objects a
                            where object_type in ('SEQUENCE','TABLE','INDEX','SYNONYM','VIEW','MATERIALIZED VIEW') 
                              and not (object_type = 'TABLE' and exists(select 1 from user_snapshots where table_name= a.object_name and prebuilt= 'NO'))
                              and object_name not like 'SYS\\_%' escape '\\'"""+
						typeFilter+
					    objectsFilter+
						existingFilter+
						excludeFilter
                              
       log.debug objectsQuery 
	                                    
       sql.eachRow(objectsQuery)
       {
		   def file= schemaUtils.extractFile(sourceDirectory,
			                                 it.object_type,
											it.object_name)
		   
		   if (file)
		     log.info "extracted ${file.absolutePath}"
       }
   }
   
   public String buildExistingFilter()
   {
       def scanner=  ant.fileScanner
       {
           fileset(dir: sourceDirectory)
           {
               include(name: '**/*.xml')
           }
       }

       def objects= []
	   def parser= new XmlParser()
       
       for (file in scanner)
       {
           def path= file.absolutePath.split((File.separator=='\\' ? '\\\\' : '/'))
           def type= path[path.length-2].replaceAll('_',' ')
           def name= path[path.length-1].split('\\.')[0]
		   
		   try
		   {
   		      def xml= parser.parse(file)
              objects << ['name': xml.'@name', 'type': type]
		   }
		   catch (Exception ex)
		   {
			   objects << ['name': name, 'type': type]
		   }
       }
       
       if (objects.size()>0)
         return ' and ('+objects.collect{ object -> "(object_name= '${oid(object.name,false)}' and object_type= '${object.type.toUpperCase()}')" }.join(' or ')+')'
       else
         return ' and 1=0'

   }

   private buildExcludeFilter()
   {
	   def excludes= exclude.split(',')
	   
	   return ' and not ('+excludes.collect{ "regexp_like(object_name,'${it}')" }.join(' or ')+')'
   }

}
