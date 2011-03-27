package com.google.code.plsqlmaven.xdb;

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
 * @goal export
 *
 */
public class XdbExportMojo
    extends XdbMojo
{

   /**
    * A comma separated list of xdb paths relative to the basePath to export
    * @since 1.0
    * @parameter expression="${filePaths}"
    */
   private String filePaths;

   /**
    * A comma separated list of xdb directory paths relative to the basePath to export
    * use instead of filePaths to export entire subdirectories
    * @since 1.0
    * @parameter expression="${dirPaths}"
    */
   private String dirPaths;

  /**
   * Whether to force export even if the local file exists
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
              
            exportFiles()
            disconnectFromDatabase()
        }
    }
    
    private checkSourceDirectory()
    {
        
        def continueExtraction= (force||!new File(xdbSourceDirectory).exists())
        
        if (continueExtraction)
          ant.mkdir(dir: xdbSourceDirectory)
        else
          fail('BE CAREFUL: The xdb directory exists... Use -Dforce to force the export, this may overwrite existing files')
          
        return continueExtraction
    }
    
    private exportFiles()
    {
        
        def things_to_do_to_export_a_path= 
        {
            def path= xdbSourceDirectory+it.path.replaceAll('/',File.separator)
            def content= it.content?.binaryStream
            
            
            if (content) // is a file
            {
                ant.mkdir(dir: path.substring(0,path.lastIndexOf(File.separator)))
                ant.truncate(file: path)
                
                log.info "exporting xdb file: "+path
                
                def targetFile= new File(path)
                targetFile << content
            }
       
        }
        
        def filePathFilter= '1=0';
        
        if (filePaths)
           filePathFilter= "path in ('"+filePaths.split(',').join("','")+"')"

        def dirPathFilter= '1=1 and ';

        if (dirPaths)
        {
           def cnt=2;           
           dirPathFilter= '('+dirPaths.split(',').collect{ path -> "under_path(res, '"+basePath+path+"', "+(cnt++)+") = 1"}.join(' or ')+') or '
        }
        
        def query= "select path(1) path, XDBURIType(any_path).getBlob() content from resource_view where under_path(res, ${basePath}, 1) = 1 and ("+dirPathFilter+filePathFilter+")";
        log.debug query
        sql.eachRow(query,things_to_do_to_export_a_path)
    }
    
}
