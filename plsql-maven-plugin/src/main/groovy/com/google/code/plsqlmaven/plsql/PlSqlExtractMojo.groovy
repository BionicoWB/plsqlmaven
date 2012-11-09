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
 * Extracts PL/SQL sources from the database to filesystem
 *
 * @goal extract
 * 
 */
public class PlSqlExtractMojo
    extends PlSqlMojo
{

   /**
    * A comma separated list of object names to extract
    * @since 1.0
    * @parameter expression="${objects}"
    */
   private String objects;

  /**
   * A comma separated list of types (package,procedure... etc) of objects to extract
   * @since 1.8
   * @parameter expression="${types}"
   */
   private String types;

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

  /**
   * Include this objects in the extraction (comma separated list of 
   * Oracle regular expressions for REGEXP_LIKE operator) 
   * @since 1.11
   * @parameter expression="${include}"
   */
   private String include;

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
        def continueExtraction= (force||!new File(sourceDir).exists())
        
        if (continueExtraction)
          ant.mkdir(dir: sourceDir)
        else
          fail('BE CAREFUL: The source directory exists... Remove it to regenerate PL/SQL source files')
          
        return (continueExtraction);
    }
    
    private createSources()
    {
        def objectsFilter= '';
        def typeFilter= '';
        def existingFilter= '';
		def excludeFilter= '';
		def includeFilter= '';
		
        if (objects)
            objectsFilter= " and object_name in ('"+objects.split(',').collect({ it.toUpperCase() }).join("','")+"')"
        
        if (types)
            typeFilter= " and object_type in ('"+types.split(',').collect({ it.toUpperCase() }).join("','")+"')"
         
        if (existing)
            existingFilter= buildExistingFilter()
      
        if (exclude)
            excludeFilter= buildExcludeFilter()
			
        if (include)
            includeFilter= buildIncludeFilter()
			
  		def objectsQuery="""select object_name,
								   object_type
							  from user_objects
							 where object_type in ('PROCEDURE','FUNCTION','PACKAGE','TYPE','VIEW','TRIGGER','PACKAGE BODY','TYPE BODY')
							   and object_name not like 'SYS\\_%' escape '\\'"""+
						  typeFilter+
						  objectsFilter+
						  existingFilter+
						  excludeFilter+
						  includeFilter
							  
         log.debug objectsQuery
							
         sql.eachRow(objectsQuery)
		 {
			def file= plsqlUtils.extractFile(sourceDir,
							                 it.object_name,
							                 it.object_type)
			
			if (file)
				log.info "extracted ${file.absolutePath}"
		 }

    }
    
    private buildExistingFilter()
    {
        def files= getPlsqlSourceFiles()
        
        def objects= [];
        
        for (file in files)
          objects << getSourceDescriptor(file)
        
        if (objects.size()>0)
         return ' and ('+objects.collect{ object -> "(object_name= '${object.name.toUpperCase()}' and object_type= '${object.type.toUpperCase()}')" }.join(' or ')+')'
        else
         return ' and 1=0'
    }

	private buildExcludeFilter()
	{
		def excludes= exclude.split(',')
		
        return ' and not ('+excludes.collect{ "regexp_like(object_name,'${it}')" }.join(' or ')+')'
    }

	private buildIncludeFilter()
	{
		def includes= include.split(',')
		
        return ' and not ('+includes.collect{ "regexp_like(object_name,'${it}')" }.join(' or ')+')'
    }
}
