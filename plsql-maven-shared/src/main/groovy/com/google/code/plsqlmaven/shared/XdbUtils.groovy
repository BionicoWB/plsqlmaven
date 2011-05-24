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
import oracle.sql.BLOB


/**
 * Basic mojo to extend for PL/SQL Goals
 */
public class XdbUtils
{
    private ant;
    private log;
    private Sql sql;
    
    public XdbUtils(ant,log,sql)
    {
        this.ant= ant;
        this.sql= sql;
        this.log= log;
    }
    
    public void importDirectory(dir)
    {
        if (!new File(dir).exists())
          return;

        def cnt= 0;
		def adir= new File(dir).absolutePath.replace(File.separator+'.'+File.separator,File.separator)
      
        def scanner=  ant.fileScanner
        {
          fileset(dir: dir)
        }

        for (file in scanner)
        {
          def filePath= file.absolutePath.replace(File.separator+'.'+File.separator,File.separator).replace(adir,'')
		  log.info "importing resource: ${filePath} from: ${adir}"
          createResource(filePath,new FileInputStream(file))
        }

    }
    
    public boolean createResource(String path, InputStream contentStream)
    {
        def xdbPath= path.replaceAll('\\\\','/');
         
        // ensure xdb folder exists
        def fsli= xdbPath.lastIndexOf('/');
        if (fsli!=-1)
        {
           def xdbFolder= xdbPath.substring(0,fsli)
           mkDir(xdbFolder)
        }

        def retval= false;
        def ac= sql.connection.autoCommit
        sql.connection.autoCommit = false
        
        BLOB content = BLOB.createTemporary(sql.connection,true,BLOB.DURATION_SESSION)
        def cout= content.setBinaryStream(1)
        cout << contentStream
        
        sql.call("""declare
                       v_dummy number:= 0;
                    begin
                    
                        begin
                          dbms_xdb.deleteresource(${xdbPath});
                        exception
                         when others then
                           null; -- ignore
                        end;
                        
                        if dbms_xdb.createresource(${xdbPath},${content}) then
                           v_dummy:= 1;
                        end if;
                        
                        ${Sql.INTEGER}:= v_dummy;
                        
                    end;""")
        {
            ok ->
            
            sql.connection.commit()
            
            if (ok)
              retval= true;
        }

        sql.connection.autoCommit = ac
        
        return retval;
    }
    
    public boolean mkDir(String dir)
    {
        def retval= false;
        def pathParts= dir.split('/')
        def path= ''
        
        for (part in pathParts)
        {
           path+='/'+part;
           
           sql.call("""declare
                          v_dummy number:= 0;
                       begin
                      
                          begin
                             select 0
                               into v_dummy
                               from resource_view
                              where equals_path(res,${path},1)= 1;
                          exception
                           when no_data_found then
                             if dbms_xdb.createfolder(${path}) then
                                v_dummy:= 1;
                             end if;
                          end;
                          
                          ${Sql.INTEGER}:= v_dummy;
                          
                       end;""")
           {
               ok ->
               
               if (ok)
               {
                 log.info "Created xdb directory: ${path}"
                 retval= true;
               }
               else
                 return false;
           }
        }
        
        return retval;
    }
}
