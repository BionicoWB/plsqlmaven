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

/**
 * Creates a directory target/remaining which contains
 * all the objects not present in the current project (and sub-modules),
 * but present in the current connection schema.
 *
 * @goal extract-remaining
 * @since 1.9
 */
public class PlsqlExtractRemainingMojo
	extends PlSqlMojo
{

   /**
	* A comma separated list of types (package,function... etc) of objects to extract
	* @parameter expression="${types}"
	*/
   private String types;
   
  /**
   * Exclude this objects from the extraction (comma separated list of
   * Oracle regular expressions for REGEXP_LIKE operator)
   * @since 1.9
   * @parameter expression="${exclude}"
   */
   private String exclude;

   private static firstExecute= true
   private static objects= []
   
   void execute()
   {
	   if (firstExecute)
	   {
			def targetDir= path('target/remaining/plsql')
			ant.mkdir(dir: targetDir)
			connectToDatabase()
			Runtime.getRuntime().addShutdownHook(new ExtractObjectsThread(new File(targetDir),
																		  log,
																		 sql,
																		 objects,
																		 types,
																		 exclude,
																		 plsqlUtils))
			firstExecute= false;
	   }
	   
	   log.info project.basedir.absolutePath
	   def files= getPlsqlSourceFiles()
	   
	   files.each
	   {
		   file ->
		   
		   objects << getSourceDescriptor(file);
	   }
	   
	   log.info 'found '+objects.size()+' objects.'
   }
   
}
