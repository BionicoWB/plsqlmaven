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

/**
 * Remove files of objects that cannot be found 
 * on the current connected schema
 *
 * @goal remove-obsolete
 * @since 1.9
 */
public class OraDdlRemoveObsoleteMojo
	extends OraDdlMojo
{

   /**
	* Do delete files
	* @since 1.9
	* @parameter expression="${force}"
	*/
	private boolean force;
 
   void execute()
   {
       if (!connectToDatabase())
       {
         fail('Need an Oracle connection')
         return
       }
       
       removeObsolete()

	   disconnectFromDatabase()
   }
   
   private removeObsolete()
   {
       def scanner=  ant.fileScanner
       {
           fileset(dir: sourceDirectory)
           {
               include(name: '**/*.xml')
           }
       }

	   def parser= new XmlParser()
       
       for (file in scanner)
       {
           def object= getSourceDescriptor(file)
		   def helper= schemaUtils.getHelper(object.type)
		   
		   try
		   {
   		      def xml= parser.parse(file)
              object= ['name': xml.'@name', 'type': object.type]
		   }
		   catch (Exception ex)
		   {
			   object= ['name': object.name, 'type': object.type]
		   }
		   
		   if (!(sql.firstRow("select 1 object_exists from user_objects where object_type= '${object.type.toUpperCase()}' and object_name= '${oid(object.name,false)}'".toString())?.object_exists))
		     if (force)
		       ant.delete(file: file.absolutePath)
			 else
			   log.info "file: ${file.absolutePath} will be deleted with -Dforce"
           
       }
   }
}
