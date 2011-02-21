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

import java.io.File;


/**
 * Extracts XDB files from the database to filesystem
 *
 * @goal xdb-export
 *
 */
public class XdbExportMojo
    extends PlSqlMojo
{

   /**
    * The base path from witch the export should start
    * @since 1.0
    * @parameter expression="${basePath}"
    * @required
    */
   private String basePath;

   /**
    * A comma separated list of absolute file paths to extract
    * @since 1.0
    * @parameter expression="${filePaths}"
    */
   private String filePaths;

  /**
   * Whether to force extraction even if the local file exists
   * @since 1.0
   * @parameter expression="${force}"
   */
   private boolean force;
   
   private String xdbDir;

    void execute()
    {
        if (checkSourceDirectory())
        {
            if (!connectToDatabase())
            {
              fail('Need an Oracle connection')
              return
            }
              
            exportFiles()
            disconnectFromDatabase()
        }
    }
    
    private checkSourceDirectory()
    {
        xdbDir= project.basedir.absolutePath+File.separator+"src"+File.separator+"main"+File.separator+"xdb"
        
        def continueExtraction= (force||!new File(xdbDir).exists())
        
        if (continueExtraction)
          ant.mkdir(dir: xdbDir)
        else
          fail('BE CAREFUL: The xdb directory exists... Use -Dforce to force the export, this may overwrite existing files')
          
        return (continueExtraction);
    }
    
    private exportFiles()
    {
        def pathFilter= '';
        
        if (filePaths)
            pathFilter= " and any_path in ('"+filePaths.split(',').collect({ it.toUpperCase() }).join("','")+"')"
        
        log.debug pathFilter
            
        sql.eachRow("select any_path path, XDBURIType(any_path).getBlob() content from resource_view where any_path like ${basePath}||'%'"+pathFilter)
        {
            def path= xdbDir+it.path.replaceAll('/',File.separator)
            def content= it.content?.binaryStream
            
            if (content) // is a file
            {
                ant.mkdir(dir: path.substring(0,path.lastIndexOf(File.separator)))
                ant.truncate(file: path)
                
                log.info "exporting xdb file: "+path
                
                def targetFile= new File(path);
                targetFile << content
            }
       
        }
        
    }
    
}
