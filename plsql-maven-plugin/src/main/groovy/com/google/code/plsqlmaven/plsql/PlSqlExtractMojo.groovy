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
        def continueExtraction= (force||!new File(project.build.sourceDirectory).exists())
        
        if (continueExtraction)
          ant.mkdir(dir: project.build.sourceDirectory)
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
		
        if (objects)
            objectsFilter= " and name in ('"+objects.split(',').collect({ it.toUpperCase() }).join("','")+"')"
        
        if (types)
            typeFilter= " and type in ('"+types.split(',').collect({ it.toUpperCase() }).join("','")+"')"
         
        if (existing)
            existingFilter= buildExistingFilter()
      
        if (exclude)
            excludeFilter= buildExcludeFilter()
			
        def objectsQuery= """select distinct type 
                               from (select type, name
		                               from user_source 
		                              where name not like 'SYS_PLSQL_%' 
		                                and type not like 'JAVA%'
		                             union all
		                             select 'VIEW' type, view_name name
		                               from user_views) where 1=1 """+
						  typeFilter+
						  objectsFilter+
						  existingFilter+
						  excludeFilter
               
        log.debug objectsQuery
            
        sql.eachRow(objectsQuery)
        {
            def type= it.type.toLowerCase();
            def ext= plsqlUtils.getTypeExt(type);
                        
            log.info(type+'...')
            
            def things_to_do_to_extract_this_type
            
            if ((type =~ /^package/) || (type =~ /^type/))
            {
               def type_dir= get_dir(project.build.sourceDirectory, type.split()[0])
               
               things_to_do_to_extract_this_type=
               {
                 if (! (it.name =~ /^SYS_PLSQL_/)) // plsql generated types from pipelined functions
                 {
                     def name= it.name.toLowerCase();
                     log.info("    "+name)
                     def odir= get_dir(type_dir, name)
                     def target_file= new File(odir, name+".${ext}"+PLSQL_EXTENSION)
                     extract(it.name,type,target_file)
                 }
               }
            }
			else
			if (type=='view')
			{
               def type_dir= get_dir(project.build.sourceDirectory, type)
			   
               things_to_do_to_extract_this_type=
               {
                  def name= it.name.toLowerCase();
                  log.info("    "+name)
                  def target_file= new File(type_dir, name+".${ext}"+PLSQL_EXTENSION)
                  extractView(it.name,type,target_file)
               }
		    }
            else
            {
               def type_dir= get_dir(project.build.sourceDirectory, type)
       
               things_to_do_to_extract_this_type=
               {
                   def name= it.name.toLowerCase();
                   log.info("    "+name)
                   def target_file= new File(type_dir, name+".${ext}"+PLSQL_EXTENSION)
                   extract(it.name,type,target_file)
               }
            }
       
            def typeQuery= """select name, type 
                                from (select distinct name, type 
		                                from user_source 
		                               where type= ${it.type}
		                              union
		                              select distinct view_name name, 'VIEW' type 
		                                from user_views 
		                               where 'VIEW'= ${it.type})
		                       where 1=1"""+
							   objectsFilter+
							   existingFilter+
							   excludeFilter
            log.debug typeQuery
            sql.eachRow(typeQuery,
                        things_to_do_to_extract_this_type)
        }
        
    }
    
    private buildExistingFilter()
    {
        def files= getPlsqlSourceFiles()
        
        def objects= [];
        
        for (file in files)
          objects << getSourceDescriptor(file)
        
        if (objects.size()>0)
         return ' and ('+objects.collect{ object -> "(name= '${object.name.toUpperCase()}' and type= '${object.type.toUpperCase()}')" }.join(' or ')+')'
        else
         return ' and 1=0'
    }

    private extract(name,type,file)
    {
          ant.truncate(file: file.absolutePath)
          
          file << "create or replace "
          
          def last_text= "";
          
          sql.eachRow("""select text
                           from user_source
                          where type= upper(${type})
                            and name= ${name}
                       order by line""")
          {
             file << it.text
             last_text= it.text
          }
          
          file << (last_text.endsWith(";") ? "\n/" : "/")
    }
    
    private extractView(name,type,file)
    {
          ant.truncate(file: file.absolutePath)
          
          file << "create or replace view "+sid(name)
          
		  def columns= []
		  
		  sql.eachRow("select column_name from user_tab_columns a where table_name = ${name} order by column_id")
		  { columns << sid(it.column_name) }
          
		  file << '('+columns.join(',')+")\nas\n"
		  
          sql.eachRow("""select text
                           from user_views
                          where view_name= ${name}""")
          {
             file << it.text
          }
          
          file << "\n/"
    }
	
	private buildExcludeFilter()
	{
		def excludes= exclude.split(',')
		
        return ' and not ('+excludes.collect{ "regexp_like(name,'${it}')" }.join(' or ')+')'
    }
}
