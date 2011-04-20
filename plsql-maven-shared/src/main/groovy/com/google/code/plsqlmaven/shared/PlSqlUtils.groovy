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



/**
 * Basic mojo to extend for PL/SQL Goals
 */
public class PlSqlUtils
{
    public static final PLSQL_EXTENSION= '.plsql';

    private ant;
    
    private Sql sql;
    
    private log;

    public PlSqlUtils(ant,log)
    {
        this.ant= ant;
        this.log= log;
    }
    
    public PlSqlUtils(ant,log,sql)
    {
        this.ant= ant;
        this.log= log;
        this.sql= sql;
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
        switch (type)
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
            case 'trigger':
               return 'trg'
            default:
               return 'unk'
        }
    }
    
    public compile(File source)
    {
        def ddl= source.getText()
        ddl= ddl.substring(0,ddl.lastIndexOf("/"))
        
        sql.execute(ddl)
    }
    
    public compileDirectory(String dirPath)
    {
        def files= getPlsqlSourceFiles(dirPath);
        def sources= []
        
        for (file in files)
        {
               sources << getSourceDescriptor(file)
               compile(file)
        }
        
        sources.each()
        {
               def ddl= "alter ${it.baseType} ${it.name} compile";
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

}
