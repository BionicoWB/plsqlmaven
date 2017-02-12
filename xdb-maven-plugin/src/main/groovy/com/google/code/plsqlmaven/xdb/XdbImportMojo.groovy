package com.google.code.plsqlmaven.xdb

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

/**
 * Import files to XDB
 *
 * @goal import
 *
 */
public class XdbImportMojo
    extends XdbMojo
{

  /**
   * Whether to import only files changed from last import
   * @since 1.0
   * @parameter property="changedOnly"
   */
    private boolean changedOnly;
    
  /**
   * Whether to loop waiting for changes, expressend in seconds between loops
   * @since 1.9
   * @parameter property="loop"
   */
    private int loop;

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
		
		def importThings= 
		{ 
	        getLastImportTime()
	        importFiles()
	        touchReferenceFile()
		}

		if (loop)
		  while (true) 
		  {
			  importThings()
			  Thread.currentThread().sleep(loop*1000)
	      }
		else
		  importThings()
		
        disconnectFromDatabase()
    }
    
    private importFiles()
    {
        if (!new File(xdbSourceDirectory).exists())
          return;

        def cnt= 0;
        
        def scanner=  ant.fileScanner
        {
            fileset(dir: xdbSourceDirectory)
        }

        for (file in scanner)
        {
           if (changedOnly&&file.lastModified()<lastImportTime) continue;
           def filePath= file.absolutePath.replace(xdbSourceDirectory,'')
           
           cnt++;
           
           xdbCreateResource(basePath+filePath,file)
        }

        if (changedOnly)
             log.info "found ${cnt} changed files..."
    }
    
    private void xdbCreateResource(path,file)
    {
        def cis;
        
        if (translateEntitiesEnabled(path))
        {
            log.info "removing html entities form ${path}..."
            def text= removeHtmlEntities(file.getText('UTF-8'))
            log.debug text
            cis= new ByteArrayInputStream(text.getBytes())
        }
        else
            cis= new FileInputStream(file)
        

        if (xdbUtils.createResource(path, cis))
          log.info "Imported xdb resource: "+path
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
