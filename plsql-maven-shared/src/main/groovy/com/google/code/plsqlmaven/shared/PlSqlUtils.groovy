package com.google.code.plsqlmaven.shared;

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
import java.sql.SQLException


/**
 * Basic mojo to extend for PL/SQL Goals
 */
public class PlSqlUtils
{
    public static final PLSQL_EXTENSION= '.plsql';

    private ant;
    
    private Sql sql;
    
    private log;

    private dropForceTypes;

    private createForceViews;

    public PlSqlUtils(ant,log,dropForceTypes,createForceViews)
    {
        this.ant= ant;
        this.log= log;
        this.dropForceTypes= dropForceTypes;
        this.createForceViews= createForceViews;
    }
    
    public PlSqlUtils(ant,log,sql,dropForceTypes,createForceViews)
    {
        this.ant= ant;
        this.log= log;
        this.sql= sql;
        this.dropForceTypes= dropForceTypes;
        this.createForceViews= createForceViews;
    }
    
    public void setSql(sql)
    {
        this.sql= sql;
    }
        
    public static getSourceDescriptor(File source)
    {
            String[] path= source.getAbsolutePath().split((File.separator=='\\' ? '\\\\' : '/'))
            def name= path[path.length-1]
            name= name.substring(0, name.indexOf('.'))
            def type= path[path.length-2]
            def baseType= type
            
            if (type == name)
            {
              baseType= type= path[path.length-3]
              
              def ext= path[path.length-1].split('\\.');
              
              if (ext[1] in ['pkb','tpb'])
                type+=' BODY';
            }
             
            return ['name': name.toUpperCase(), 'baseType': baseType, 'type': type.toUpperCase(), 'file': source]
    }
    
    public getPlsqlSourceFiles(dir)
    {
        
        if (!new File(dir).exists())
         return [];

        def scanner=  ant.fileScanner
        {
            fileset(dir: dir)
            {
                include(name: "**/*"+PLSQL_EXTENSION)
            }
        }
        
        def files= []
        
        for (file in scanner)
           files << file
           
        return files;
    }
    
    public getTypeExt(type)
    {
        switch (type.toLowerCase())
        {
            case 'package':
               return 'pks'
            case 'package body':
               return 'pkb'
            case 'type':
               return 'tps'
            case 'type body':
               return 'tpb'
            case 'function':
               return 'fnc'
            case 'procedure':
               return 'prc'
            case 'view':
               return 'vw'
            case 'trigger':
               return 'trg'
            default:
               return 'unk'
        }
    }
    
    public compile(File source)
    {
        def ddl= source.getText().replace('\r','')
        log.debug ddl
        def sp= ddl.lastIndexOf("/")

        if (dropForceTypes)
        { 
           def desc= getSourceDescriptor(source)
           if (desc.type=='TYPE'
               &&sql.firstRow("select 1 type_exists from user_objects where object_name= ${desc.name} and object_type= 'TYPE'")?.type_exists)
              sql.execute("drop type ${desc.name} force".toString());
        }	

        if (sp>0)
         ddl= ddl.substring(0,sp)
        
		try
		{
                     sql.execute(ddl)
		}
		catch (SQLException ex)
		{
			// ignore compile errors (we will read user_errors) 
			if (ex.errorCode!=24344)
			  throw ex
        	}
    }
    
    public compileDirectory(String dirPath)
    {
        def files= getPlsqlSourceFiles(dirPath);
        def sources= []
        
        for (file in files)
        {
               sources << getSourceDescriptor(file)
			   log.info 'compiling: '+file.absolutePath+'...'
               compile(file)
        }
        
        sources.each()
        {
               def ddl= "alter ${it.baseType} ${it.name} compile";
			   log.info "re-compiling: ${it.baseType} ${it.name}..."
               sql.execute(ddl.toString())
        }
    }
    
    public void deployJar(jarPath)
    {
        def rootDir= (System.getProperty("java.io.tmpdir")
                      +File.separator
                      +jarPath.substring(jarPath.lastIndexOf(File.separator)+1,
                                         jarPath.lastIndexOf('.'))
                      +'_'+Math.random())
                    
        ant.delete(dir: rootDir)
        ant.mkdir(dir: rootDir)
        ant.unzip(src: jarPath, dest: rootDir)
        
        compileDirectory(rootDir+File.separator+'plsql')
        
        if (!new File(rootDir+File.separator+'xdb').exists())
          importDirectory(rootDir+File.separator+'xdb')
    }
	
	public extractFile(targetDir,name,type)
	{
		def lname= name.toLowerCase()
		def ltype= type.toLowerCase()
		def ext= getTypeExt(ltype)
        def target_file
		
        if ((ltype =~ /^package/) || (ltype =~ /^type/))
        {
           def type_dir= get_dir(targetDir, ltype.split()[0])
           
	         if (! (name =~ /^SYS_PLSQL_/)) // plsql generated types from pipelined functions
	         {
	             def odir= get_dir(type_dir, lname)
	             target_file= new File(odir, lname+".${ext}"+PLSQL_EXTENSION)
	             extract(name,ltype,target_file)
	         }
        }
		else
		if (ltype=='view')
		{
           def type_dir= get_dir(targetDir, ltype)
           target_file= new File(type_dir, lname+".${ext}"+PLSQL_EXTENSION)
           extractView(name,target_file)
	    }
        else
        {
           def type_dir= get_dir(targetDir, ltype)
           target_file= new File(type_dir, lname+".${ext}"+PLSQL_EXTENSION)
           extract(name,ltype,target_file)
        }

        return target_file
		
    }
	
	
	private extract(name,type,file)
	{
		  log.info "extracting: "+file.absolutePath+"..."
		  
		  ant.truncate(file: file.absolutePath)
		  
		  file << "create or replace "
		  
		  def last_text= "";
		  
		  sql.eachRow("""select text
						   from user_source
						  where type= upper(${type})
							and name= ${name}
					   order by line""")
		  {
			 if (last_text) file << last_text
			 last_text = it.text
		  }

          file << last_text.replaceAll('(\n*)?$','')+"\n/"
	}

	public get_dir(base,name)
	{
			def dir= new File(base, name)
			if (!dir.exists()) dir.mkdir()
			return dir;
	}
	
	private extractView(name,file)
	{
		  log.info "extracting: "+file.absolutePath+"..."
		  
		  ant.truncate(file: file.absolutePath)
		  
		  file << "create or replace"+(createForceViews ? ' force' : '')+" view "+sid(name)
		  
		  def columns= []
		  def last_text= "";
		  
		  sql.eachRow("select column_name from user_tab_columns a where table_name = ${name} order by column_id")
		  { columns << sid(it.column_name) }
		  
		  file << '('+columns.join(',')+")\nas\n"
		  
		  sql.eachRow("""select text
						   from user_views
						  where view_name= ${name}""")
		  {
			 if (last_text) file << last_text
			 last_text = it.text
		  }
		  
          file << last_text.replaceAll('(\n*)?$','')+"\n/"
	}

	public sid(oracleIdentifier)
	{
		  if (oracleIdentifier==null)
			return null
			
		  if (oracleIdentifier!=oracleIdentifier.toUpperCase()
			  ||(oracleIdentifier==~'.* .*')
			  ||(oracleIdentifier==~'^[^A-Z].*'))
			return '"'+oracleIdentifier+'"'
		  else
			return oracleIdentifier.toLowerCase()
	}

}
