package com.google.code.plsqlmaven;

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

import java.io.File
import org.codehaus.plexus.util.DirectoryScanner
import oracle.sql.BLOB
import groovy.sql.Sql

/**
 * Import files to XDB
 *
 * @goal xdb-import
 *
 */
public class XdbImportMojo
    extends XdbMojo
{

  /**
   * Whether to import only files changed from last import
   * @since 1.0
   * @parameter expression="${changedOnly}"
   */
    private boolean changedOnly;
    
   /**
    * Location of the touch file.
    */
    private touchFile;

   /**
    * Last import time.
    */
    private lastImportTime= 0L;

    void execute()
    {
        if (!connectToDatabase())
        {
          fail('Need an Oracle connection')
          return
        }
        getLastImportTime()
        importFiles()
        touchReferenceFile()
        disconnectFromDatabase()
    }
    
    private importFiles()
    {
        if (!new File(xdbSourceDirectory).exists())
          return;

        DirectoryScanner plsqlFiles= new DirectoryScanner()
       
        plsqlFiles.setBasedir(xdbSourceDirectory)
       
        plsqlFiles.setIncludes(["**/*.*"] as String[])
       
        plsqlFiles.scan()
        
        def filePaths= plsqlFiles.getIncludedFiles()
        def cnt= 0;
        
        for (filePath in filePaths)
        {
           def file= new File(xdbSourceDirectory, filePath)
           
           if (changedOnly&&file.lastModified()<lastImportTime) continue;
           
           cnt++;
           def xdbFolder= basePath+filePath.substring(0,filePath.lastIndexOf(File.separator))
           xdbMkdir(xdbFolder)
           xdbCreateResource(basePath+filePath,file)
        }

        if (changedOnly)
             log.info "found ${cnt} changed files..."
    }
    
    private void xdbMkdir(dir)
    {
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
                 log.info "Created xdb dir: ${dir}"
           }
        }
         
    }
    
    private void xdbCreateResource(path,file)
    {
        sql.connection.autoCommit = false
        
        BLOB content = BLOB.createTemporary(sql.connection,true,BLOB.DURATION_SESSION)
        def cout= content.setBinaryStream(1)
        def fin= new FileInputStream(file)
        cout << fin
        
        sql.call("""declare
                       v_dummy number:= 0;
                    begin
                    
                        begin
                          dbms_xdb.deleteresource(${path});
                        exception
                         when others then
                           null; -- ignore
                        end;
                        
                        if dbms_xdb.createresource(${path},${content}) then
                           v_dummy:= 1;
                        end if;
                        
                        ${Sql.INTEGER}:= v_dummy;
                        
                    end;""")
        {
            ok ->
            
            sql.connection.commit()
            
            if (ok)
              log.info "Imported xdb resorce: "+path                     
        }

        sql.connection.autoCommit = true
    }
    
    private void getLastImportTime()
    {
        touchFile= new File(project.build.directory,".xdb")
        lastImportTime= touchFile.lastModified();
        log.debug("touch file: ${touchFile.absolutePath}")
    }

    private void touchReferenceFile()
    {
        ant.mkdir(dir: project.build.directory)
        ant.touch(file: touchFile.getAbsolutePath())
    }

}
