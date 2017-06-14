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
import java.io.File
import java.sql.SQLException
import com.google.code.plsqlmaven.oraddl.helpers.DDLException;
import java.io.File;
import groovy.xml.MarkupBuilder


/**
 * Basic mojo to extend for PL/SQL Goals
 */
public class SchemaUtils
{
    private ant;
    private log;
    private Sql sql;
    private String username;

   /**
    * Type specific helpers
    */
    private helpers= [:];


    public SchemaUtils(ant,log,sql)
    {
        this.ant= ant;
        this.sql= sql;
        this.log= log;
        this.username= sql.firstRow("select user from dual").user
    }

    public SchemaUtils(ant,log)
    {
        this.ant= ant;
        this.log= log;
    }

    public void setSql(sql)
    {
        this.sql= sql;
        this.username= sql.firstRow("select user from dual").user
    }

    public void syncDirectory(dir)
    {
       if (!new File(dir).exists())
        return;

       def scanner=  ant.fileScanner
       {
          fileset(dir: dir)
          {
               include(name: '**/*.xml')
          }
       }

       def objects= [:]

       for (file in scanner)
       {
           def path= file.absolutePath.split(File.separator)
           def type= path[path.length-2].replaceAll('_',' ')
           def name= path[path.length-1].split('\\.')[0]
           if (!objects[type]) objects[type]= []
           objects[type] << ['file': file, 'name': name]
       }

       sync(objects)

    }

    public boolean sync(objects, detectOnly, destDir)
    {
       def order= ['table','index','sequence','synonym','view','materialized view']
       def parser= new XmlParser()
       def success= true
       def changes= []
       def helpers= []
        def target_dir
        def target_file

        if(destDir)
        {
            log.info "writing sync.sql file under \"" + destDir + "\""
            target_dir = new File(destDir)
            if (!target_dir.exists())
            {
                target_dir.mkdir()
            }
            target_file = new File(destDir, "sync.sql")
            ant.truncate(file: target_file)
        }

       order.each
       {
           type ->

           def helper= getHelper(type)
           helpers << helper

           objects[type].each
           {
               object ->

               try
               {
                       log.info "sync ${type} ${object.name}"
                       def target= parser.parseText(object.file.getText().replace('\r',''))

                       if (!helper.exists(target))
                          changes+= ensureList(helper.create(target))
                       else
                       {
                          def source= extractXml(parser,helper,object.name)
                          changes+= ensureList(helper.detectChanges(source,target))
                       }
               }
               catch (SQLException ex)
               {
                   log.error ex.message
                   success= false
               }

           }
       }

       helpers.each
       {
           helper ->

           changes= helper.reorder(changes)
       }

       changes.each
       {
           change ->

           log.debug change.toString()

           try
            {
                log.info change.type

                if(target_file)
                {
                    target_file << change.ddl.toString() + "\n/\n"
                }

                if(!detectOnly)
                {
                    sql.execute change.ddl.toString()
                }
            }
           catch (SQLException ex)
           {
               if (!change.failSafe)
               {
                   log.error ex.message+' executing: '+change.ddl.toString()
                   success= false
               }
           }
       }

       return success
    }

    public extractXml(parser,helper,name)
    {
        StringWriter writer= new StringWriter()
        writer.write('<?xml version="1.0" encoding="UTF-8"?>'+"\n")
        def xml = new MarkupBuilder(writer)
        xml.omitNullAttributes = true
        xml.doubleQuotes = true

        if (!helper.extract(helper.oid(name,false),xml))
          return null
        else
          return parser.parseText(writer.toString())
    }

    public extractFile(targetDir,type,name)
    {
		def ltype= type.toLowerCase();
		def lname= name.toLowerCase()
		def filePath= path("${targetDir}/${ltype.replaceAll(' ','_')}/${lname}.xml")
		ant.mkdir(dir: filePath.substring(0,filePath.lastIndexOf(File.separator)))
		ant.truncate(file: filePath)
		File file= new File(filePath)
		FileWriter writer= new FileWriter(file)
		writer.write('<?xml version="1.0" encoding="UTF-8"?>'+"\n")
		def xml = new MarkupBuilder(writer)
		xml.omitNullAttributes = true
		xml.doubleQuotes = true

		if (!getHelper(ltype)?.extract(name,xml))
		{
		  writer.close()
		  file.delete()
		  return false
		}
		else
		{
		  writer.close()
		  return file
		}
    }

    public getHelper(type)
    {
        def helper;

        if (!(helper=helpers[type]))
        {
            try
            {
                def camelType= initcap(type);
                def clazz= this.getClass().getClassLoader().loadClass("com.google.code.plsqlmaven.oraddl.helpers.${camelType}Helper")
                helper= clazz.newInstance(sql,log,username);
                helpers[type]= helper;
            }
            catch (ClassNotFoundException e)
            { /* ignore */ }
        }

        return helper;
    }

    private applyChanges(helper,changes,detectOnly=false)
    {
        if (log.debugEnabled)
          changes.each{ log.debug it.toString() }

        if (!detectOnly)
            changes.each
            {
                change ->

                helper."${change.type}"(change);
            }
    }

    public static getSourceDescriptor(File source)
    {
        def path= source.absolutePath.split((File.separator=='\\' ? '\\\\' : '/'))
        def type= path[path.length-2].replaceAll('_',' ')
        def name= path[path.length-1].split('\\.')[0]
        return ['name': name, 'type': type, 'file': source]
    }

    public getSchemaSourceFiles(dir)
    {

        if (!new File(dir).exists())
         return [];

        def scanner=  ant.fileScanner
        {
           fileset(dir: dir)
           {
               include(name: '**/*.xml')
           }
        }

        def files= []

        for (file in scanner)
           files << file

        return files;
    }

    private ensureList(o)
    {
        if (o instanceof List)
          return o;
        else
          return [o];
    }

	public String path(p)
	{
		return p.replace('/',File.separator)
	}

   private initcap(str)
   {
	char[] name = str.toCharArray()
	if (name.length == 0)
		return ""
	StringBuffer sb = new StringBuffer(name.length)
	for (i in 0..name.length - 1) {
		if (i == 0) {
			sb.append(Character.toUpperCase(name[i]))
		} else if (name[i] == ' ') {
			name[i + 1] = Character.toUpperCase(name[i + 1])
		} else {
			sb.append(name[i])
		}
	}
	return sb.toString()
   }

}
