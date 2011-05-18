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

class ExtractObjectsThread extends Thread
{
	   def targetDir,log,sql,objects,types,exclude,plsqlUtils
	   
	   public ExtractObjectsThread(targetDir,log,sql,objects,types,exclude,plsqlUtils)
	   {
		   this.targetDir= targetDir
		   this.log= log
		   this.sql= sql
		   this.objects= objects
		   this.types= types
		   this.exclude= exclude
		   this.plsqlUtils= plsqlUtils
	   }
	   
	   public void run()
	   {
		   log.info "final objects: "+objects.size()
		   
		   if (objects.size()>0)
		   {
			   log.info "extracting to ${targetDir.absolutePath}..."
			   
			   def typeFilter= ''
			   def excludeFilter= ''
			   
			   if (types)
				 typeFilter= " and object_type in ('"+types.split(',').collect({ it.toUpperCase() }).join("','")+"')"
				 
			   if (exclude)
				 excludeFilter= buildExcludeFilter()

			   def objectsQuery="""select object_name,
										   object_type
									 from user_objects
									where object_type in ('PROCEDURE','FUNCTION','PACKAGE','TYPE','VIEW','TRIGGER','PACKAGE BODY','TYPE BODY')
									   and object_name not like 'SYS\\_%' escape '\\'"""+
							   typeFilter+
							   buildObjectsFilter()+
							   excludeFilter
									 
			   log.debug objectsQuery
			   
			   sql.eachRow(objectsQuery)
			   {
				   def file= plsqlUtils.extractFile(targetDir.absolutePath,
													 it.object_name,
													it.object_type)
				   
				   if (file)
					 log.info "extracted ${file.absolutePath}"
			   }
		

		   }
	   }
	   
	   private buildExcludeFilter()
	   {
		   def excludes= exclude.split(',')
		   
		   return ' and not ('+excludes.collect{ "regexp_like(object_name,'${it}')" }.join(' or ')+')'
	   }
	   
	   
	   private String buildObjectsFilter()
	   {
		   return ' and ('+objects.collect{ object -> "not (object_name= '${object.name.toUpperCase()}' and object_type= '${object.type.toUpperCase()}')" }.join(' and ')+')'
	   }
	
	
}
